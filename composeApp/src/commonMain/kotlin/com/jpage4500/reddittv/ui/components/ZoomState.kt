package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 10f
private const val DOUBLE_TAP_SCALE = 2f
private const val SCROLL_ZOOM_STEP = 1.1f
// pixels of pan per unit of scrollDelta. one mouse-wheel "click" is delta=1.0; trackpad
// gestures come through as smaller continuous values and feel proportional.
private const val SCROLL_PAN_STEP = 30f
// slack when deciding "is the content zoomed in?" (see detectPagerFriendlyTransformGestures)
private const val ZOOM_EPSILON = 0.01f

/**
 * holds pan/zoom state shared by [Modifier.zoomable]. obtain via [rememberZoomState].
 *
 * scale = 1f means the content renders at its Fit baseline (entire image visible inside the box).
 * a caller can set [initialScale] above 1f to start zoomed in (e.g. ZoomableImage's Crop mode sets
 * it to the cover scale); the user can still pinch out down to 1f to see the full image. zoom range
 * is always clamped to [MIN_SCALE, MAX_SCALE].
 *
 * [contentRectAtBaseline] returns the projected layout rectangle of the content at scale=1f, in
 * the box's coordinate space. For content that fills the box (video) the default is fine; centered
 * content (image at Fit) should return its centered letterbox rect; content anchored top-left (HTML)
 * should return Rect(0, 0, contentWidth, contentHeight). The clamp keeps the content's screen
 * projection from leaving empty space at the box edges when content is larger than the box, and
 * from drifting out of the box when smaller.
 */
class ZoomState(
    private val contentRectAtBaseline: (IntSize) -> Rect = { box ->
        Rect(0f, 0f, box.width.toFloat(), box.height.toFloat())
    },
) {
    var scale by mutableStateOf(MIN_SCALE)
    var offsetX by mutableStateOf(0f)
    var offsetY by mutableStateOf(0f)
    var boxSize by mutableStateOf(IntSize.Zero)
    var initialScale by mutableStateOf(MIN_SCALE)

    fun reset(toScale: Float = initialScale) {
        scale = toScale
        offsetX = 0f
        offsetY = 0f
        clampOffsets(toScale)
    }

    fun clampOffsets(s: Float) {
        if (boxSize == IntSize.Zero) return
        val rect = contentRectAtBaseline(boxSize)
        if (rect.width <= 0f || rect.height <= 0f) return
        val cx = boxSize.width / 2f
        val cy = boxSize.height / 2f
        // graphicsLayer's transformOrigin defaults to (0.5, 0.5), so a layer-space point p maps to
        // screen position: screen = c + (p - c) * s + offset. Solve for the offsets that put each
        // content edge flush with the matching box edge:
        //   tLeft  : content's left edge sits at screen x = 0
        //   tRight : content's right edge sits at screen x = boxWidth
        // When content is larger than the box, tLeft > tRight and the valid range is [tRight, tLeft]
        // (content covers the box; pan stops when an edge would create empty space). When content
        // is smaller, tLeft < tRight and the range flips to [tLeft, tRight] (content stays fully
        // inside the box). min/max picks the correct ordering either way — and crucially, allows
        // an asymmetric range for content that isn't centered (e.g., a top-left-anchored table),
        // which the old size-based clamp collapsed to (0, 0).
        val tLeft = 0f - cx - (rect.left - cx) * s
        val tRight = boxSize.width - cx - (rect.right - cx) * s
        val tTop = 0f - cy - (rect.top - cy) * s
        val tBottom = boxSize.height - cy - (rect.bottom - cy) * s
        offsetX = offsetX.coerceIn(min(tLeft, tRight), max(tLeft, tRight))
        offsetY = offsetY.coerceIn(min(tTop, tBottom), max(tTop, tBottom))
    }

    // zoom about an anchor point (in container coords) so that point stays put across a scale change
    fun zoomAbout(anchorX: Float, anchorY: Float, newScale: Float) {
        val cx = boxSize.width / 2f
        val cy = boxSize.height / 2f
        val contentX = (anchorX - cx - offsetX) / scale + cx
        val contentY = (anchorY - cy - offsetY) / scale + cy
        offsetX = anchorX - cx - (contentX - cx) * newScale
        offsetY = anchorY - cy - (contentY - cy) * newScale
        scale = newScale
        clampOffsets(newScale)
    }

    fun applyTransformGesture(centroid: Offset, pan: Offset, zoom: Float) {
        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        // anchor zoom at the pinch centroid so the point between the fingers stays put
        zoomAbout(centroid.x, centroid.y, newScale)
        offsetX += pan.x
        offsetY += pan.y
        clampOffsets(newScale)
    }

    fun applyScrollZoom(position: Offset, scrollY: Float): Boolean {
        if (scrollY == 0f) return false
        // scale in proportion to scroll magnitude so fast scrolls zoom further per event
        val factor = SCROLL_ZOOM_STEP.pow(-scrollY)
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (newScale == scale) return false
        zoomAbout(position.x, position.y, newScale)
        return true
    }

    fun applyScrollPan(scrollX: Float, scrollY: Float): Boolean {
        if (scrollX == 0f && scrollY == 0f) return false
        val beforeX = offsetX
        val beforeY = offsetY
        // scrollDelta is positive when the OS reports "scroll forward" (revealing content below /
        // to the right). Subtract so the content shifts opposite to the scroll direction, which on
        // a Mac trackpad with natural scrolling means content follows the user's fingers.
        offsetX -= scrollX * SCROLL_PAN_STEP
        offsetY -= scrollY * SCROLL_PAN_STEP
        clampOffsets(scale)
        return offsetX != beforeX || offsetY != beforeY
    }

    fun applyDoubleTap(tap: Offset) {
        // reset always goes to MIN_SCALE (Fit / full image) — even if the caller's initial view was Crop
        if (scale > MIN_SCALE + 0.001f) {
            reset(MIN_SCALE)
        } else {
            val target = (MIN_SCALE * DOUBLE_TAP_SCALE).coerceAtMost(MAX_SCALE)
            zoomAbout(tap.x, tap.y, target)
        }
    }
}

@Composable
fun rememberZoomState(
    contentRectAtBaseline: (IntSize) -> Rect = { box ->
        Rect(0f, 0f, box.width.toFloat(), box.height.toFloat())
    },
): ZoomState = remember { ZoomState(contentRectAtBaseline) }

/**
 * applies pinch-to-zoom + pan, double-tap zoom, and desktop scroll-wheel / trackpad zoom on top
 * of [state]. owns onSizeChanged, the three pointerInput blocks, clipToBounds, and graphicsLayer.
 *
 * desktop scroll behavior:
 *   plain scroll          → zoom at the cursor
 *   Cmd/Ctrl + scroll     → pan (so trackpad users can navigate around zoomed-in content without
 *                           click-dragging; the modifier mirrors the browser zoom shortcut so
 *                           "hold the zoom key to do the other thing" is the discoverable inverse).
 *
 * @param inPager true when the content sits inside a HorizontalPager — see
 * [detectPagerFriendlyTransformGestures]
 */
fun Modifier.zoomable(
    state: ZoomState,
    onTap: ((Offset) -> Unit)? = null,
    inPager: Boolean = false,
): Modifier = this
    .onSizeChanged { state.boxSize = it }
    .pointerInput(inPager) {
        if (inPager) {
            detectPagerFriendlyTransformGestures(state)
        } else {
            detectTransformGestures { centroid, pan, zoom, _ ->
                state.applyTransformGesture(centroid, pan, zoom)
            }
        }
    }
    .pointerInput(Unit) {
        detectTapGestures(
            onTap = { offset -> onTap?.invoke(offset) },
            onDoubleTap = { tap -> state.applyDoubleTap(tap) },
        )
    }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                val mods = event.keyboardModifiers
                val delta = change.scrollDelta
                val handled = if (mods.isCtrlPressed || mods.isMetaPressed) {
                    state.applyScrollPan(delta.x, delta.y)
                } else {
                    state.applyScrollZoom(change.position, delta.y)
                }
                if (handled) change.consume()
            }
        }
    }
    .clipToBounds()
    .graphicsLayer {
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.offsetX
        translationY = state.offsetY
    }

/**
 * pinch/pan detector that co-operates with an enclosing HorizontalPager. modelled on Compose's
 * detectTransformGestures with one change: while the content is at its baseline scale, a
 * single-pointer drag is neither applied nor consumed, so the pager sees it and swipes to the
 * next/previous page. a two-finger pinch always zooms, and once zoomed in (scale > 1) single-pointer
 * drags pan the content — pinch/double-tap back out to resume paging.
 *
 * without this, the child's transform gestures consume every drag and the pager never scrolls.
 */
private suspend fun PointerInputScope.detectPagerFriendlyTransformGestures(state: ZoomState) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            // the pager (or anything else) took over the gesture
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    if (zoomMotion > touchSlop || pan.getDistance() > touchSlop) pastTouchSlop = true
                }

                if (pastTouchSlop) {
                    // one finger at baseline scale = a page swipe; leave it for the pager. the
                    // epsilon keeps a pinch that lands a hair above baseline from blocking paging
                    val isMultiTouch = event.changes.count { it.pressed } > 1
                    val isZoomedIn = state.scale > state.initialScale + ZOOM_EPSILON
                    if (isMultiTouch || isZoomedIn) {
                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            val centroid = event.calculateCentroid(useCurrent = false)
                            state.applyTransformGesture(centroid, panChange, zoomChange)
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}
