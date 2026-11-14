package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import com.jpage4500.reddittv.util.LoggerConfig
import kotlin.math.max
import kotlin.math.min

private val log = LoggerConfig.createLogger("ZoomableImage")

/**
 * fullscreen image viewer with pinch/pan, double-tap, and desktop trackpad/wheel zoom.
 *
 * the inner [AsyncImage] always renders at ContentScale.Fit; the [contentScale] parameter only
 * controls the initial graphicsLayer scale — Fit starts at 1.0, Crop starts at the cover-scale so
 * the image fills the box. either way the user can pinch out to 1.0 (Fit) to see the full image.
 * zoom range is 1.0–5.0.
 *
 * @param inPager true when the image sits inside a HorizontalPager, so a single-finger drag at the
 * baseline scale swipes pages instead of being swallowed by the zoom gestures
 */
@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String? = "Image",
    modifier: Modifier = Modifier.fillMaxSize(),
    contentScale: ContentScale = ContentScale.Fit,
    inPager: Boolean = false,
    onTap: ((Offset) -> Unit)? = null,
    onError: ((Throwable) -> Unit)? = null,
) {
    // not keyed on model: animation frame swaps (same dimensions) should preserve user zoom/pan
    var imgSize by remember { mutableStateOf(IntSize.Zero) }
    var hasLoadedAny by remember { mutableStateOf(false) }

    // letterbox-aware content rect at Fit baseline so pan clamping respects the actual image extent
    val state = rememberZoomState { box ->
        val img = imgSize
        if (img == IntSize.Zero || box == IntSize.Zero) {
            Rect(0f, 0f, box.width.toFloat(), box.height.toFloat())
        } else {
            val fitFactor = min(box.width.toFloat() / img.width, box.height.toFloat() / img.height)
            val fw = img.width * fitFactor
            val fh = img.height * fitFactor
            val left = (box.width - fw) / 2f
            val top = (box.height - fh) / 2f
            Rect(left, top, left + fw, top + fh)
        }
    }

    // coverScale takes Fit → Crop (image fills container). 1f when image already fills, >1 otherwise.
    val coverScale: Float = remember(imgSize, state.boxSize) {
        if (imgSize == IntSize.Zero || state.boxSize == IntSize.Zero) 1f
        else {
            val rw = state.boxSize.width.toFloat() / imgSize.width
            val rh = state.boxSize.height.toFloat() / imgSize.height
            (max(rw, rh) / min(rw, rh)).coerceAtLeast(1f)
        }
    }
    val initialScale = if (contentScale == ContentScale.Crop) coverScale else 1f

    // snap back to initial scale when the underlying image actually changes (size or fit mode).
    // intentionally NOT keyed on `model` so animation sequences (radar) keep the user's zoom.
    LaunchedEffect(initialScale) {
        state.initialScale = initialScale
        state.reset(initialScale)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zoomable(state, onTap = onTap, inPager = inPager),
        ) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onSuccess = { successState ->
                    val w = successState.result.image.width
                    val h = successState.result.image.height
                    if (w != imgSize.width || h != imgSize.height) {
                        imgSize = IntSize(w, h)
                    }
                    hasLoadedAny = true
                },
                onError = { errState ->
                    val errorMsg = errState.result.throwable.message ?: "Unknown error"
                    log.e("error: model=$model, $errorMsg")
                    onError?.invoke(errState.result.throwable)
                },
            )
        }

        if (!hasLoadedAny) {
            CircularProgressIndicator()
        }
    }
}
