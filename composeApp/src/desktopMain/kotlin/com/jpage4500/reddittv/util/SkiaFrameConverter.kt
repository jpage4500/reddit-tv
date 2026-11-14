package com.jpage4500.reddittv.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.Frame
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer

/**
 * copied from HubitatDashboardKMP's desktop player — fix bugs in both.
 *
 * turns a decoded ffmpeg frame into a compose ImageBitmap with a single bulk copy.
 *
 * this replaces `Java2DFrameConverter` -> `BufferedImage` -> `toComposeImageBitmap()`. that chain ran
 * a **per-pixel `getRGB()` loop** and allocated a fresh byte array *and* a fresh skia bitmap for every
 * frame. measured at 27 ms/frame at 1080p and 69 ms at 2560x1920 — and on a real 4-camera desktop
 * dashboard it accounted for **85-96% of all video CPU**, roughly 2.1 of 2.4 cores.
 * (HubitatDashboardKMP: docs/Desktop Video CPU Usage.md)
 *
 * the trick is to ask ffmpeg for skia's OWN native 32-bit layout, so nothing has to swizzle channels:
 * swscale writes exactly the bytes skia wants and this becomes a memcpy. measured 35-113x faster
 * (~0.7 ms/frame at 1080p, ~1.3 ms at 2560x1920).
 *
 * every frame gets its OWN skia bitmap, marked immutable before it leaves this thread. that is a
 * correctness requirement, not a style choice: a bitmap the play thread re-installs into is one the
 * compose render thread can be *drawing* at the same moment, and `SkBitmap::installPixels` nulls the
 * pixel ref before installing the new one. a draw landing in that window gets a null back from
 * `Image.makeFromBitmap`, skiko throws, and the throw kills skiko's frame-dispatcher coroutine —
 * i.e. the whole app. immutability is also what lets skia SHARE the pixels with the `Image` it builds
 * on every draw instead of copying the entire frame again on the UI thread.
 * (HubitatDashboardKMP: docs/Desktop Video CPU Usage.md)
 */
class SkiaFrameConverter {

    // reused across frames. installPixels() copies into skia's own storage (verified), so handing it
    // the same array every frame is safe and keeps this off the GC's radar entirely.
    private var pixels: ByteArray? = null

    private var info: ImageInfo? = null
    private var infoWidth = 0
    private var infoHeight = 0

    fun convert(frame: Frame): ImageBitmap? {
        val width = frame.imageWidth
        val height = frame.imageHeight
        if (width <= 0 || height <= 0) return null
        val source = frame.image?.getOrNull(0) as? ByteBuffer ?: return null

        // ffmpeg pads each row to a 64-byte-aligned linesize; skia takes the row stride directly
        val stride = frame.imageStride
        val needed = height * stride

        var buffer = pixels
        if (buffer == null || buffer.size < needed) {
            buffer = ByteArray(needed)
            pixels = buffer
        }
        source.clear()
        if (source.remaining() < needed) return null
        source.get(buffer, 0, needed)

        var imageInfo = info
        if (imageInfo == null || infoWidth != width || infoHeight != height) {
            imageInfo = ImageInfo.makeS32(width, height, ColorAlphaType.UNPREMUL)
            info = imageInfo
            infoWidth = width
            infoHeight = height
        }

        // one bitmap per frame — never re-installed into, so no reader can catch it half-written.
        // the pixel buffer is malloc'd and copied inside installPixels either way, so pooling the
        // bitmap saved nothing but the (tiny) wrapper, at the cost of sharing mutable state with the
        // render thread. see the class doc.
        val bitmap = Bitmap()
        if (!bitmap.installPixels(imageInfo, buffer, stride)) return null
        // pixels can no longer change, so skia's makeFromBitmap shares them with the Image it builds
        // per draw instead of copying the whole frame on the UI thread
        bitmap.setImmutable()
        return bitmap.asComposeImageBitmap()
    }

    /**
     * drops the scratch buffer only — it deliberately does NOT call `Bitmap.close()`.
     *
     * frames already handed out are immutable and owned by whoever still holds them: the preview keeps
     * the last one on screen after the stream ends, and an in-flight draw may still be reading it. freeing the native pixels here would leave those
     * reading released memory. skiko's Bitmap is `Managed` and frees itself once unreachable.
     */
    fun close() {
        pixels = null
        info = null
    }

    companion object {
        /**
         * the ffmpeg pixel format that matches skia's native 32-bit layout on this platform — BGRA on
         * macOS/windows, RGBA where skia builds N32 the other way round. asking skia instead of
         * hardcoding is what keeps the copy a plain memcpy everywhere rather than a channel swap.
         */
        val nativePixelFormat: Int =
            if (ColorType.N32 == ColorType.BGRA_8888) avutil.AV_PIX_FMT_BGRA
            else avutil.AV_PIX_FMT_RGBA
    }
}
