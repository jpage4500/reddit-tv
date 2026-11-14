package com.jpage4500.reddittv.util

import androidx.compose.ui.graphics.ImageBitmap
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.*
import kotlin.concurrent.thread

private val log = LoggerConfig.createLogger("PreviewPlayer")

// codecs of a single picture, not a video
private val PICTURE_CODECS = setOf(avcodec.AV_CODEC_ID_WEBP, avcodec.AV_CODEC_ID_PNG, avcodec.AV_CODEC_ID_MJPEG, avcodec.AV_CODEC_ID_GIF, avcodec.AV_CODEC_ID_BMP)

// µs. a dead server fails the preview instead of hanging it
private const val RW_TIMEOUT_US = 15_000_000L

// a tile's preview reads less before it starts: FFmpeg's defaults are 5MB and 5s, there to find
// every stream, where a muted tile only needs the picture
private val PREVIEW_PROBE_OPTIONS = mapOf(
    "probesize" to "500000",
    "analyzeduration" to "500000",
)

/**
 * what FFmpeg needs to fetch a stream the way the TV will: [headers] on every request, and
 * [timeoutUs] for a server that stops answering. shared with VideoFileProbe
 */
internal fun ffmpegOptions(headers: Map<String, String>, timeoutUs: Long): Map<String, String> {
    val options = mutableMapOf(
        "rw_timeout" to timeoutUs.toString(),
        // HLS: take a segment whatever its URL ends in — some hosts name theirs as images.
        // FFmpeg still checks what's inside
        "extension_picky" to "0",
    )
    // FFmpeg has its own options for these two, and HLS passes all three on to every segment
    val extra = StringBuilder()
    headers.forEach { (name, value) ->
        when {
            name.equals("User-Agent", ignoreCase = true) -> options["user_agent"] = value
            name.equals("Referer", ignoreCase = true) -> options["referer"] = value
            else -> extra.append(name).append(": ").append(value).append("\r\n")
        }
    }
    if (extra.isNotEmpty()) options["headers"] = extra.toString()
    return options
}

/**
 * the first real video stream, when the one JavaCV takes (the first) is a still picture — an
 * HLS variant whose segments are wrapped as images reads as one. null when its choice stands
 */
internal fun realVideoStream(grabber: FFmpegFrameGrabber): Int? {
    val context = grabber.formatContext ?: return null
    val videos = (0 until context.nb_streams()).filter { context.streams(it).codecpar().codec_type() == avutil.AVMEDIA_TYPE_VIDEO }
    fun isPicture(index: Int) = context.streams(index).codecpar().codec_id() in PICTURE_CODECS
    if (videos.isEmpty() || !isPicture(videos.first())) return null
    return videos.firstOrNull { !isPicture(it) }
}

/**
 * plays one video for the desktop VideoPreview: FFmpeg (JavaCV) fetches and decodes it on its own
 * thread, sending [headers] with every request, and hands each picture to [onFrame].
 *
 * a trimmed copy of HubitatDashboardKMP's FFmpegPlayer, which plays live cameras as fast as frames
 * arrive. a file or an HLS VOD decodes far faster than real time, so this one paces itself to the
 * stream's timestamps: the sound card's clock when there's audio (so picture and sound stay in
 * step), the wall clock otherwise.
 *
 * the controls ([pause], [play], [seekTo]) only set flags, which the play thread acts
 * on. at the end it stays open on the last frame, so a seek or play can start it over.
 *
 * [isPreview]: a grid tile's, which spends less time probing the stream before it starts.
 * [startPaused] opens it and shows its first frame, then waits for [play]
 */
class PreviewPlayer(
    private val url: String,
    private val headers: Map<String, String>,
    private val mimeType: String?,
    private val onStarted: (hasVideo: Boolean) -> Unit,
    private val onFrame: (ImageBitmap) -> Unit,
    private val onError: () -> Unit,
    private val isPreview: Boolean = false,
    startPaused: Boolean = false,
) {
    private val isRunning = AtomicBoolean(false)

    // what the controls show — written by the play thread, read by the UI
    @Volatile
    var positionMs = 0L
        private set
    // 0 for a live stream, or one whose length FFmpeg can't tell: it can't be seeked
    @Volatile
    var durationMs = 0L
        private set
    @Volatile
    var isPaused = startPaused
        private set
    @Volatile
    var isEnded = false
        private set

    // where to seek to (µs), picked up by the play thread; -1 for none
    private val pendingSeekUs = AtomicLong(-1L)

    // all touched only by the play thread
    private var soundLine: SourceDataLine? = null
    // the stream time of the first video frame, and when it was shown — the wall clock's zero
    private var basePts = -1L
    private var baseNs = 0L
    // the stream time of the first sample written to the sound line, and the line's position then —
    // its clock's zero
    private var audioBasePts = -1L
    private var lineBaseUs = 0L
    // a seek while paused shows the frame it lands on, then waits
    private var needsStill = startPaused

    fun start() {
        if (isRunning.getAndSet(true)) return
        thread(isDaemon = true, name = "PreviewPlayer") { playLoop() }
    }

    /** returns at once; the play thread notices and releases everything itself */
    fun release() {
        isRunning.set(false)
    }

    // silence instead of the sound: the line still runs, so it still keeps the time
    @Volatile
    var isMuted = false

    fun pause() {
        isPaused = true
    }

    /** at the end, starts it over */
    fun play() {
        if (isEnded) seekTo(0)
        isPaused = false
    }

    fun togglePlay() {
        if (isPaused || isEnded) play() else pause()
    }

    fun seekTo(ms: Long) {
        if (durationMs <= 0) return
        val target = ms.coerceIn(0, durationMs)
        positionMs = target
        pendingSeekUs.set(target * 1000)
    }

    fun seekBy(ms: Long) = seekTo(positionMs + ms)

    private fun playLoop() {
        var grabber: FFmpegFrameGrabber? = null
        val converter = SkiaFrameConverter()
        try {
            // FFmpeg logs every stream it opens to stderr otherwise
            avutil.av_log_set_level(avutil.AV_LOG_ERROR)
            grabber = FFmpegFrameGrabber(url).apply {
                options = ffmpegOptions(headers, RW_TIMEOUT_US).let { if (isPreview) it + PREVIEW_PROBE_OPTIONS else it }
                // FFmpeg only recognises a playlist by its .m3u8 name, and some have none
                if (mimeType == HLS_MIME_TYPE) format = "hls"
                // skia's own layout, so SkiaFrameConverter is a memcpy
                pixelFormat = SkiaFrameConverter.nativePixelFormat
                sampleFormat = avutil.AV_SAMPLE_FMT_S16
                setVideoOption("threads", "auto")
            }
            grabber.start()
            realVideoStream(grabber)?.let { index ->
                log.d("playLoop: first video stream is a picture, using stream $index")
                grabber.stop()
                grabber.videoStream = index
                grabber.start()
            }
            if (!isRunning.get()) return
            val hasVideo = grabber.imageWidth > 0
            log.d("playLoop: ${grabber.format} ${grabber.imageWidth}x${grabber.imageHeight} @${grabber.videoFrameRate}fps, audio:${grabber.audioChannels}ch, $url")
            durationMs = (grabber.lengthInTime / 1000).coerceAtLeast(0)
            soundLine = openSoundLine(grabber)
            onStarted(hasVideo)

            while (isRunning.get()) {
                val seekUs = pendingSeekUs.getAndSet(-1L)
                if (seekUs >= 0) seek(grabber, seekUs)
                if (isPaused && !needsStill) {
                    holdWhilePaused()
                    continue
                }
                val frame = grabber.grab()
                if (frame == null) {
                    onEnd()
                    continue
                }
                if (frame.image != null) {
                    if (needsStill) {
                        needsStill = false
                        positionMs = frame.timestamp / 1000
                        converter.convert(frame)?.let { onFrame(it) }
                    } else {
                        showFrame(frame, converter)
                    }
                } else if (frame.samples != null && !needsStill) {
                    if (!hasVideo) positionMs = frame.timestamp / 1000
                    writeSamples(frame)
                }
            }
        } catch (e: Throwable) {
            log.e("playLoop: ${e.message}, $url")
            if (isRunning.get()) onError()
        } finally {
            // release() must run even if stop() throws, or JavaCV's finalizer retries it on the GC
            // thread, where a dead connection can block it
            try { grabber?.stop() } catch (e: Throwable) { log.e("playLoop: stop: ${e.message}") }
            try { grabber?.release() } catch (e: Throwable) { log.e("playLoop: release: ${e.message}") }
            converter.close()
            closeSoundLine()
        }
    }

    private fun seek(grabber: FFmpegFrameGrabber, us: Long) {
        log.d("seek: ${us / 1000}ms")
        soundLine?.let {
            it.stop()
            it.flush()
        }
        try {
            // decodes on to the exact frame, not just the keyframe before it
            grabber.setTimestamp(us)
        } catch (e: Throwable) {
            log.e("seek: ${e.message}")
        }
        // both clocks start over from the first frame after it
        basePts = -1L
        audioBasePts = -1L
        isEnded = false
        needsStill = isPaused
        if (!isPaused) soundLine?.start()
    }

    /** waits for play, a seek or release. the sound line stops where it is, and so does its clock */
    private fun holdWhilePaused() {
        val line = soundLine
        line?.stop()
        val startNs = System.nanoTime()
        while (isPaused && isRunning.get() && pendingSeekUs.get() < 0) Thread.sleep(20)
        // the wall clock skips the pause
        baseNs += System.nanoTime() - startNs
        if (!isPaused) line?.start()
    }

    /** stays on the last frame until a seek (or play, which seeks to the start) or release */
    private fun onEnd() {
        if (!isEnded) {
            // let what's buffered finish playing, rather than cut off the last half second
            soundLine?.drain()
            log.d("onEnd: ${positionMs}ms")
            isEnded = true
            if (durationMs > 0) positionMs = durationMs
        }
        while (isRunning.get() && pendingSeekUs.get() < 0) Thread.sleep(20)
    }

    /** waits until [frame] is due, then shows it — or drops it, when it's already late */
    private fun showFrame(frame: Frame, converter: SkiaFrameConverter) {
        val pts = frame.timestamp
        if (basePts < 0) {
            basePts = pts
            baseNs = System.nanoTime()
        }
        while (isRunning.get()) {
            if (isPaused) holdWhilePaused()
            // a seek lands somewhere else — this frame is stale
            if (pendingSeekUs.get() >= 0) return
            val waitUs = pts - clockUs()
            if (waitUs <= 0) break
            // the sound line ran dry before this frame's audio arrived: its clock has stopped, and
            // waiting on it would never end
            val line = soundLine
            if (line != null && audioBasePts >= 0 && line.available() >= line.bufferSize) break
            Thread.sleep((waitUs / 1000).coerceIn(1, 10))
        }
        if (clockUs() - pts > MAX_LATE_US) return
        positionMs = pts / 1000
        converter.convert(frame)?.let { onFrame(it) }
    }

    /** where playback is, in stream time */
    private fun clockUs(): Long {
        val line = soundLine
        if (line != null && audioBasePts >= 0) return audioBasePts + line.microsecondPosition - lineBaseUs
        return basePts + (System.nanoTime() - baseNs) / 1000
    }

    private fun openSoundLine(grabber: FFmpegFrameGrabber): SourceDataLine? {
        if (grabber.audioChannels <= 0 || grabber.sampleRate <= 0) return null
        return try {
            val format = AudioFormat(grabber.sampleRate.toFloat(), 16, grabber.audioChannels, true, false)
            val line = AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, format)) as SourceDataLine
            // half a second: enough to ride out a slow segment, little enough to stop promptly
            line.open(format, format.frameSize * grabber.sampleRate / 2)
            line.start()
            line
        } catch (e: Throwable) {
            // no sound output — the picture still plays, on the wall clock
            log.e("openSoundLine: ${e.message}")
            null
        }
    }

    /** blocks while the line is full, which is what holds the loop to real time */
    private fun writeSamples(frame: Frame) {
        val line = soundLine ?: return
        val samples = frame.samples[0] as? ShortBuffer ?: return
        if (audioBasePts < 0) {
            audioBasePts = frame.timestamp
            lineBaseUs = line.microsecondPosition
        }
        samples.rewind()
        val buffer = ByteBuffer.allocate(samples.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asShortBuffer().put(samples)
        val bytes = if (isMuted) ByteArray(buffer.capacity()) else buffer.array()
        line.write(bytes, 0, buffer.capacity())
    }

    private fun closeSoundLine() {
        try {
            soundLine?.stop()
            soundLine?.flush()
            soundLine?.close()
        } catch (e: Throwable) {
        }
        soundLine = null
    }

    companion object {
        // a frame this far behind is dropped, so a slow decode catches up instead of lagging
        private const val MAX_LATE_US = 150_000L
    }
}

/** an HLS playlist's mime type: tells FFmpeg to open it as "hls" */
const val HLS_MIME_TYPE = "application/x-mpegURL"
