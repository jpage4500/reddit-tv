package com.jpage4500.reddittv.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.jpage4500.reddittv.data.AppPref
import com.jpage4500.reddittv.data.Comment
import com.jpage4500.reddittv.data.Post
import com.jpage4500.reddittv.data.PostMedia
import com.jpage4500.reddittv.manager.RedditClient
import com.jpage4500.reddittv.ui.components.*
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.ui.theme.LocalRedditType
import com.jpage4500.reddittv.ui.theme.changeTextSize
import com.jpage4500.reddittv.util.PlatformUtils
import com.jpage4500.reddittv.util.Utils
import com.jpage4500.reddittv.util.RedditFormat
import com.jpage4500.reddittv.util.SettingsManager
import com.jpage4500.reddittv.util.ToastManager
import kotlinx.coroutines.*
import kotlin.math.*
import org.jetbrains.compose.resources.*
import reddittv.composeapp.generated.resources.*

/**
 * a post (design boards 4 "wide" and 6): a compact header — the subreddit's letter, the
 * subreddit / author / age line and the title — then the media at about 60% of the screen so the
 * start of the text shows under it, then the text, the action row and the comments, all one
 * scroll. with a remote only the media and the action row take the focus: everywhere else UP and
 * DOWN smoothly scroll the page (reading mode), stopping on the action row and the media on the way.
 *
 * on the media, OK opens an image (or a gallery's current one) full screen and LEFT/RIGHT step a
 * gallery; on a video OK plays / pauses and a long press of OK goes full screen. a video floats
 * into a corner once it's mostly scrolled off, so it keeps playing over the comments
 */
@Composable
fun PostScreen(
    initial: Post,
    isTv: Boolean,
    // the TV's layout: a landscape phone or a desktop window too
    wide: Boolean,
    onPostChanged: (Post) -> Unit,
    onBack: () -> Unit,
    join: JoinState?,
) {
    val type = LocalRedditType.current
    val scope = rememberCoroutineScope()
    var post by remember(initial.id) { mutableStateOf(initial) }
    var comments by remember(initial.id) { mutableStateOf<List<Comment>?>(null) }
    var error by remember(initial.id) { mutableStateOf<String?>(null) }
    val guides = remember(comments) { comments?.let(::commentGuides).orEmpty() }
    var galleryIndex by remember(initial.id) { mutableStateOf(0) }
    var isImageFullScreen by remember(initial.id) { mutableStateOf(false) }
    val galleryCount = (post.media as? PostMedia.Gallery)?.images?.size ?: 0
    val videoControl = rememberVideoControl(initial.id)
    val isMuted by SettingsManager.getBoolAsState(AppPref.VIDEO_MUTED)
    val mediaFocus = remember { FocusRequester() }
    val actionsFocus = remember { FocusRequester() }
    val readingFocus = remember { FocusRequester() }

    BackPress(onBack)

    LaunchedEffect(initial.id) {
        RedditClient.getPost(initial.permalink)
            .onSuccess {
                post = it.post
                comments = it.comments
            }
            .onFailure { error = it.message ?: getString(Res.string.comments_load_failed) }
    }
    // text inside a focus block; the header and action row line up with it
    val blockPad = 16.dp
    val edgePad = if (wide) 40.dp else 16.dp
    // a link with no picture (its own or its page's) has nothing to show here: its "Open link" is
    // in the action row
    val hasMedia = post.hasMedia || rememberMediaImage(post.media) != null
    val hasText = post.selfText.isNotBlank()
    val isVideo = post.media is PostMedia.Video
    val listState = rememberLazyListState()
    val scroller = remember(listState) { SmoothScroller(listState, scope) }

    // a remote or keyboard starts on the media, else reading the text, else the action row
    val isFocusDriven = remember { Utils.isFocusDriven() }
    val blockInsetH = if (isFocusDriven) BLOCK_INSET_H else 0.dp
    val blockInsetV = if (isFocusDriven) BLOCK_INSET_V else 0.dp
    val firstFocus = when {
        hasMedia -> mediaFocus
        hasText -> readingFocus
        else -> actionsFocus
    }
    if (isFocusDriven) LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    // reading mode: whether it's in the comments (below the action row) or the post's text (above)
    var isBelowActions by remember(initial.id) { mutableStateOf(false) }

    fun isFullyVisible(key: String): Boolean {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return false
        return item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
    }
    fun isTopVisible(key: String): Boolean {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return false
        return item.offset >= info.viewportStartOffset
    }
    fun focusMedia() {
        scroller.stop()
        runCatching { mediaFocus.requestFocus() }
        // back to the very top, so the title shows too
        scope.launch { listState.animateScrollToItem(0) }
    }
    fun focusActions() {
        scroller.stop()
        runCatching { actionsFocus.requestFocus() }
    }
    fun scrollPage(down: Boolean) {
        runCatching { readingFocus.requestFocus() }
        val info = listState.layoutInfo
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        scroller.scrollBy(viewport * SCROLL_STEP_FRACTION * (if (down) 1 else -1), max = viewport)
    }
    // DOWN while reading or on the media: stop on the action row once it's all on screen
    fun moveDown() {
        if (!isBelowActions && isFullyVisible("actions")) focusActions() else scrollPage(down = true)
    }
    // UP while reading: stop on the action row, then on the media once its top shows
    fun moveUp() {
        when {
            isBelowActions && isFullyVisible("actions") -> focusActions()
            !isBelowActions && hasMedia && isTopVisible("media") -> focusMedia()
            !isBelowActions && hasMedia && !listState.canScrollBackward -> focusMedia()
            else -> scrollPage(down = false)
        }
    }

    fun vote(up: Boolean) {
        val old = post
        val newLikes = if (old.likes == up) null else up
        val delta = (if (newLikes == true) 1 else if (newLikes == false) -1 else 0) -
            (if (old.likes == true) 1 else if (old.likes == false) -1 else 0)
        post = old.copy(likes = newLikes, score = if (old.score == Post.UNKNOWN_COUNT) old.score else old.score + delta)
        onPostChanged(post)
        scope.launch {
            RedditClient.vote(old.fullName, if (newLikes == null) 0 else if (newLikes) 1 else -1).onFailure {
                post = old
                onPostChanged(old)
                ToastManager.showToast(Res.string.sign_in_to_vote, success = false)
            }
        }
    }

    // OK (or a tap) on the media, and a long press of it
    fun onMediaClick() {
        when (post.media) {
            is PostMedia.Video -> videoControl.togglePlay()
            is PostMedia.Image, is PostMedia.Gallery -> isImageFullScreen = true
            is PostMedia.Link -> PlatformUtils.openUrl(post.url)
            PostMedia.None -> {}
        }
    }
    fun onMediaLongClick() {
        when (post.media) {
            is PostMedia.Video -> videoControl.openFullScreen()
            else -> onMediaClick()
        }
    }

    // on a phone the status bar draws over the app: keep the post below it
    Box(Modifier.fillMaxSize().background(Colors.Background).windowInsetsPadding(WindowInsets.systemBars)) {
        // reading mode's focus: a sibling under the list (not its parent), so the keys of the media and
        // the action row never pass through it. OK here plays / pauses a video, even floating
        if (isFocusDriven) {
            Box(
                Modifier
                    .matchParentSize()
                    .focusRequester(readingFocus)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> { moveDown(); true }
                            Key.DirectionUp -> { moveUp(); true }
                            // nothing to the side: don't let the focus wander off
                            Key.DirectionLeft, Key.DirectionRight -> true
                            else -> false
                        }
                    }
                    .okKeyPress(
                        onPress = { if (isVideo) videoControl.togglePlay() },
                        onLongPress = { if (isVideo) videoControl.openFullScreen() },
                    )
                    .focusable(),
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = if (wide) 18.dp else 12.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(if (wide) 8.dp else 6.dp),
        ) {
            item(key = "header") {
                PostHeader(post, join, Modifier.padding(horizontal = edgePad))
            }

            if (hasMedia) {
                item(key = "media") {
                    val ratio = post.media.aspectRatio
                    val mediaModifier = if (wide) {
                        Modifier.fillParentMaxHeight(0.6f)
                    } else {
                        Modifier.heightIn(max = 400.dp)
                            .then(if (ratio != null) Modifier.aspectRatio(ratio.coerceAtLeast(0.75f)) else Modifier.height(240.dp))
                    }
                    FocusBlock(
                        isTv = isFocusDriven,
                        modifier = Modifier
                            .focusRequester(mediaFocus)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        isBelowActions = false
                                        moveDown()
                                        return@onPreviewKeyEvent true
                                    }
                                    // the title above it
                                    Key.DirectionUp -> {
                                        scope.launch { listState.animateScrollToItem(0) }
                                        return@onPreviewKeyEvent true
                                    }
                                    else -> {}
                                }
                                // RIGHT on a video mutes / unmutes it, and it's remembered
                                if (post.media is PostMedia.Video && event.key == Key.DirectionRight) {
                                    toggleVideoMuted()
                                    return@onPreviewKeyEvent true
                                }
                                if (galleryCount < 2) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionRight -> { galleryIndex = (galleryIndex + 1) % galleryCount; true }
                                    Key.DirectionLeft -> if (galleryIndex > 0) { galleryIndex--; true } else false
                                    else -> false
                                }
                            }
                            .okKeyPress(onPress = ::onMediaClick, onLongPress = ::onMediaLongClick),
                        // on a phone a video's own player takes its taps
                        onClick = ::onMediaClick,
                        onLongClick = ::onMediaLongClick,
                        clickableOnPhone = post.media !is PostMedia.Video,
                    ) {
                        if (isVideo) {
                            // just its spot: the player floats over it (FloatingVideo), so it can
                            // move into a corner without being re-created
                            Box(mediaModifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Colors.MediaBackground))
                        } else {
                            PostMediaFull(
                                post = post,
                                galleryIndex = galleryIndex,
                                modifier = mediaModifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                            )
                        }
                    }
                }
            }

            if (hasText) {
                item(key = "text") {
                    Box(Modifier.padding(horizontal = blockInsetH, vertical = blockInsetV)) {
                        RedditText(
                            text = post.selfText,
                            fontSize = type.postBody,
                            lineHeight = type.postBody * 1.5f,
                            color = Colors.Text,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = blockPad, vertical = 10.dp),
                        )
                    }
                }
            }

            item(key = "actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = edgePad, vertical = 6.dp)
                        // DOWN reads the comments, UP the text / goes back to the media
                        .onPreviewKeyEvent { event ->
                            if (!isFocusDriven || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> { isBelowActions = true; moveDown(); true }
                                Key.DirectionUp -> { isBelowActions = false; moveUp(); true }
                                else -> false
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionChip(if (post.score == Post.UNKNOWN_COUNT) "▲" else "▲ " + RedditFormat.count(post.score), selected = post.likes == true, modifier = Modifier.focusRequester(actionsFocus)) { vote(up = true) }
                    ActionChip("▼", selected = post.likes == false) { vote(up = false) }
                    ActionChip("A−") { changeTextSize(bigger = false) }
                    ActionChip("A+") { changeTextSize(bigger = true) }
                    if (post.media is PostMedia.Link) ActionChip(stringResource(Res.string.open_link)) { PlatformUtils.openUrl(post.url) }
                    Spacer(Modifier.weight(1f))
                    if (post.numComments != Post.UNKNOWN_COUNT) {
                        Text(
                            text = pluralStringResource(Res.plurals.comments_count, post.numComments, RedditFormat.count(post.numComments)),
                            fontSize = type.tileMeta,
                            color = Colors.TextDim,
                            maxLines = 1,
                        )
                    }
                }
            }

            item(key = "divider") {
                Box(Modifier.padding(horizontal = edgePad, vertical = 4.dp).fillMaxWidth().height(1.dp).background(Colors.Divider))
            }

            val list = comments
            when {
                list == null && error == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Colors.Accent)
                    }
                }
                list == null -> item(key = "error") {
                    Text(error ?: "", color = Colors.TextDim, fontSize = type.postBody, modifier = Modifier.padding(horizontal = edgePad))
                }
                else -> {
                    itemsIndexed(list, key = { _, it -> commentKey(it) }) { index, comment ->
                        // the vertical inset goes inside the row so its thread lines meet the next one's
                        Box(Modifier.padding(horizontal = blockInsetH)) {
                            CommentRow(comment, guides[index], padV = blockInsetV, modifier = Modifier.padding(horizontal = blockPad))
                        }
                    }
                }
            }
        }

        if (isVideo) {
            FloatingVideo(
                listState = listState,
                itemKey = "media",
                insetH = blockInsetH,
                insetV = blockInsetV,
                wide = wide,
                aspectRatio = post.media.aspectRatio,
            ) {
                PostMediaFull(post = post, videoControl = videoControl, modifier = Modifier.fillMaxSize())
                // playing is what it's meant to do: the pill only says when it's paused or muted
                val status = listOfNotNull(
                    if (!videoControl.isPlaying) stringResource(Res.string.video_paused) else null,
                    if (isMuted) stringResource(Res.string.video_muted) else null,
                )
                if (isFocusDriven && status.isNotEmpty()) {
                    Text(
                        text = status.joinToString(" · "),
                        fontSize = type.tileMeta,
                        fontWeight = FontWeight.SemiBold,
                        color = Colors.Text,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(CircleShape)
                            .background(Colors.MediaBadge)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }

    if (isImageFullScreen) {
        FullScreenImage(
            post = post,
            startIndex = galleryIndex,
            onIndexChanged = { galleryIndex = it },
            onDismiss = {
                isImageFullScreen = false
                if (isFocusDriven) runCatching { mediaFocus.requestFocus() }
            },
        )
    }
}

private fun commentKey(comment: Comment) = comment.id.ifEmpty { comment.hashCode().toString() }

// each reply level indents this much; deeper than MAX_COMMENT_DEPTH lines up with the last level
private val COMMENT_INDENT = 14.dp
private const val MAX_COMMENT_DEPTH = 8

// a focus block's ring sits this far in from the screen's edges (and the media inside it)
private val BLOCK_INSET_H = 24.dp
private val BLOCK_INSET_V = 2.dp

// how much of the screen one DOWN / UP scrolls while reading, and how long that takes
private const val SCROLL_STEP_FRACTION = 0.35f
private const val SCROLL_STEP_MS = 220

// the video floats into a corner once less than this much of it is still on screen
private const val FLOAT_SHOWING_FRACTION = 0.5f
private const val FLOAT_ANIM_MS = 280

/**
 * scrolls a list in smooth steps. a press that comes while the last one is still moving adds to
 * what's left of it rather than restarting, so holding a key glides at a steady pace
 */
private class SmoothScroller(private val listState: LazyListState, private val scope: CoroutineScope) {
    // what's still to scroll from earlier presses
    private var remaining = 0f
    private var job: Job? = null

    /** scrolls [distance] px more, with no more than [max] px ever queued up */
    fun scrollBy(distance: Float, max: Float) {
        // turning around drops what was left going the other way
        if (remaining != 0f && sign(remaining) != sign(distance)) remaining = 0f
        val target = (remaining + distance).coerceIn(-max, max)
        remaining = target
        job?.cancel()
        job = scope.launch {
            listState.scroll {
                var done = 0f
                animate(0f, target, animationSpec = tween(SCROLL_STEP_MS, easing = LinearOutSlowInEasing)) { value, _ ->
                    scrollBy(value - done)
                    done = value
                    remaining = target - value
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        remaining = 0f
    }
}

/**
 * the post's video, drawn over the list rather than in it: over its spot in the list (item
 * [itemKey], inset by [insetH] / [insetV]) while that's on screen, and once most of it has
 * scrolled off the top, shrunk into the bottom corner so it keeps playing over the comments. it's
 * one player that only moves — taking it out of the list item and back would start it over.
 * floating, it can be dragged anywhere on screen; where it's left is remembered for next time
 */
@Composable
private fun FloatingVideo(
    listState: LazyListState,
    itemKey: Any,
    insetH: Dp,
    insetV: Dp,
    wide: Boolean,
    aspectRatio: Float?,
    content: @Composable BoxScope.() -> Unit,
) {
    val isFloating by remember(listState) { derivedStateOf { isScrolledOff(listState, itemKey) } }
    val progress = animateFloatAsState(if (isFloating) 1f else 0f, tween(FLOAT_ANIM_MS), label = "floatingVideo")
    // its spot in the list as last seen — x, y, width, height — for once the item is gone
    val spot = remember { IntArray(4) }
    val shape = RoundedCornerShape(12.dp)
    val elevation = with(LocalDensity.current) { 12.dp.toPx() }
    // where it floats, as 0..1 of the room it has to move in: the bottom right until it's dragged
    var cornerPos by remember { mutableStateOf(loadFloatingPos()) }
    // that room, in px, as last laid out: a drag's px become a share of it
    val room = remember { FloatArray(2) }
    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val info = listState.layoutInfo
                info.visibleItemsInfo.firstOrNull { it.key == itemKey }?.let { item ->
                    val inH = insetH.roundToPx()
                    val inV = insetV.roundToPx()
                    // an item's offset counts from below the list's top padding
                    spot[0] = inH
                    spot[1] = item.offset - info.viewportStartOffset + inV
                    spot[2] = width - 2 * inH
                    spot[3] = item.size - 2 * inV
                }
                // the corner: the video's own shape, as big as fits a share of the screen
                val ratio = (aspectRatio ?: (16f / 9f)).coerceIn(0.5f, 2.4f)
                val maxW = width * (if (wide) 0.3f else 0.5f)
                val maxH = height * (if (wide) 0.4f else 0.3f)
                val cornerW = min(maxW, maxH * ratio)
                val cornerH = cornerW / ratio
                val margin = (if (wide) 24.dp else 12.dp).toPx()
                room[0] = (width - 2 * margin - cornerW).coerceAtLeast(0f)
                room[1] = (height - 2 * margin - cornerH).coerceAtLeast(0f)
                val cornerX = margin + room[0] * cornerPos.x
                val cornerY = margin + room[1] * cornerPos.y

                val p = progress.value
                val w = lerp(spot[2].toFloat(), cornerW, p).roundToInt().coerceAtLeast(0)
                val h = lerp(spot[3].toFloat(), cornerH, p).roundToInt().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(width, height) {
                    placeable.place(lerp(spot[0].toFloat(), cornerX, p).roundToInt(), lerp(spot[1].toFloat(), cornerY, p).roundToInt())
                }
            }
            .dragToMove(
                enabled = isFloating,
                onDrag = { delta ->
                    cornerPos = Offset(
                        if (room[0] > 0f) (cornerPos.x + delta.x / room[0]).coerceIn(0f, 1f) else cornerPos.x,
                        if (room[1] > 0f) (cornerPos.y + delta.y / room[1]).coerceIn(0f, 1f) else cornerPos.y,
                    )
                },
                onDragEnd = { saveFloatingPos(cornerPos) },
            )
            .graphicsLayer {
                shadowElevation = progress.value * elevation
                this.shape = shape
                clip = true
            }
            .background(Colors.MediaBackground),
        content = content,
    )
}

/**
 * drags what it's on by a touch or the mouse, while [enabled]. it watches before what's inside it
 * (the player) does: a press that doesn't move is still the player's tap, but once it moves past
 * the touch slop it's a drag, and the player sees it cancelled
 */
private fun Modifier.dragToMove(enabled: Boolean, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var moved = Offset.Zero
            var isDragging = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    // the release of a drag isn't a tap on the player
                    if (isDragging) change.consume()
                    break
                }
                val delta = change.positionChange()
                if (isDragging) {
                    onDrag(delta)
                } else {
                    moved += delta
                    if (moved.getDistance() <= viewConfiguration.touchSlop) continue
                    isDragging = true
                    onDrag(moved)
                }
                change.consume()
            }
            if (isDragging) onDragEnd()
        }
    }
}

/** the floating video's saved [AppPref.FLOATING_VIDEO_POS]: the bottom right when there's none */
private fun loadFloatingPos(): Offset {
    val parts = SettingsManager.getStringOrEmpty(AppPref.FLOATING_VIDEO_POS).split(",").mapNotNull { it.toFloatOrNull() }
    if (parts.size != 2) return Offset(1f, 1f)
    return Offset(parts[0].coerceIn(0f, 1f), parts[1].coerceIn(0f, 1f))
}

private fun saveFloatingPos(pos: Offset) {
    SettingsManager.setString(AppPref.FLOATING_VIDEO_POS, "${pos.x},${pos.y}")
}

/** whether less than [FLOAT_SHOWING_FRACTION] of the list's item [key] is still on screen */
private fun isScrolledOff(listState: LazyListState, key: Any): Boolean {
    val info = listState.layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return false
    val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return listState.firstVisibleItemIndex > 0
    val showing = item.offset + item.size - info.viewportStartOffset
    return showing < item.size * FLOAT_SHOWING_FRACTION
}

private fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction

/**
 * the top of a post: the subreddit's first letter in a circle, "r/sub · u/author · age · flair"
 * and the community's Join button on one line, then the whole title below at full width
 */
@Composable
private fun PostHeader(post: Post, join: JoinState?, modifier: Modifier) {
    val type = LocalRedditType.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(Colors.SubredditAvatar),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = post.subreddit.take(1).lowercase(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Colors.Text,
                )
            }
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Colors.Text, fontWeight = FontWeight.SemiBold)) { append("r/${post.subreddit}") }
                    append(" · u/${post.author} · ${RedditFormat.age(post.createdUtc)}")
                    post.flair?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                },
                fontSize = type.tileMeta,
                color = Colors.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (join != null) JoinButton(join)
        }
        // the whole title, full width so it lines up with the post's text below
        Text(
            text = post.title,
            fontSize = type.postTitle,
            lineHeight = type.postTitle * 1.2f,
            fontWeight = FontWeight.Bold,
            color = Colors.Text,
        )
    }
}

/**
 * where a comment's thread lines go, the way reddit draws them: [depth] (capped) sets the indent,
 * [continues] says which ancestors' lines run on past this comment to a later reply of theirs,
 * and [hasReplies] starts this comment's own line under its author
 */
private class CommentGuide(val depth: Int, val continues: BooleanArray, val hasReplies: Boolean)

/** the [CommentGuide] for each comment in the flattened list, worked out from the end backwards */
private fun commentGuides(list: List<Comment>): List<CommentGuide> {
    val depths = list.map { it.depth.coerceIn(0, MAX_COMMENT_DEPTH) }
    // open[k]: a later reply of the depth-k comment above is still to come
    val open = BooleanArray(MAX_COMMENT_DEPTH + 1)
    val guides = arrayOfNulls<CommentGuide>(list.size)
    for (i in list.indices.reversed()) {
        val d = depths[i]
        val hasReplies = depths.getOrNull(i + 1) == d + 1
        guides[i] = CommentGuide(d, open.copyOf(d), hasReplies)
        // this comment cuts off anything deeper, and is itself a later reply of its parent
        for (k in d until open.size) open[k] = false
        if (d > 0) open[d - 1] = true
    }
    return guides.map { it!! }
}

/**
 * one comment. each level indents by [COMMENT_INDENT]; the parent's line comes down and curves
 * into the author line, ancestors' lines carry on past it only while they have more replies
 */
@Composable
private fun CommentRow(comment: Comment, guide: CommentGuide, padV: Dp, modifier: Modifier) {
    val type = LocalRedditType.current
    var headerHeight by remember { mutableStateOf(0) }
    val topPad = padV + 8.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawCommentGuides(guide, topPad.toPx(), headerHeight.toFloat()) },
    ) {
        Spacer(Modifier.width(COMMENT_INDENT * (guide.depth + 1)))
        Column(Modifier.padding(top = topPad, bottom = padV + 8.dp).weight(1f)) {
            if (comment.isMore) {
                Text(
                    pluralStringResource(Res.plurals.more_replies, comment.moreCount, comment.moreCount),
                    fontSize = type.commentAuthor,
                    color = Colors.Accent,
                    modifier = Modifier.onSizeChanged { headerHeight = it.height },
                )
                return@Column
            }
            val opTag = stringResource(Res.string.comment_op)
            Text(
                text = buildString {
                    append(comment.author)
                    if (comment.isSubmitter) append(" ").append(opTag)
                    if (comment.score != Post.UNKNOWN_COUNT) append("  ·  ▲ ").append(RedditFormat.count(comment.score))
                    append("  ·  ").append(RedditFormat.age(comment.createdUtc))
                },
                fontSize = type.commentAuthor,
                fontWeight = FontWeight.SemiBold,
                color = if (comment.isSubmitter) Colors.Accent else Colors.TextDim,
                modifier = Modifier.onSizeChanged { headerHeight = it.height },
            )
            Spacer(Modifier.height(3.dp))
            RedditText(comment.body, fontSize = type.commentBody, lineHeight = type.commentBody * 1.45f, color = Colors.Text)
        }
    }
}

/** the thread lines for one comment row; column k's line sits in the middle of indent k */
private fun DrawScope.drawCommentGuides(guide: CommentGuide, topPad: Float, headerHeight: Float) {
    val indent = COMMENT_INDENT.toPx()
    val stroke = 1.5.dp.toPx()
    val radius = 8.dp.toPx()
    fun lineX(k: Int) = k * indent + indent / 2
    val headerMid = topPad + headerHeight / 2
    // ancestors above the parent: straight through, while they have more replies below
    for (k in 0 until guide.depth - 1) {
        if (guide.continues[k]) drawLine(Colors.CommentRail, Offset(lineX(k), 0f), Offset(lineX(k), size.height), stroke)
    }
    if (guide.depth > 0) {
        val x = lineX(guide.depth - 1)
        // the parent's line, on down past this reply when there's another after it
        val bottom = if (guide.continues[guide.depth - 1]) size.height else headerMid - radius
        drawLine(Colors.CommentRail, Offset(x, 0f), Offset(x, bottom), stroke)
        // and the elbow into this comment's author line
        val elbow = Path().apply {
            moveTo(x, headerMid - radius)
            quadraticTo(x, headerMid, x + radius, headerMid)
            lineTo(guide.depth * indent + indent - 4.dp.toPx(), headerMid)
        }
        drawPath(elbow, Colors.CommentRail, style = Stroke(stroke))
    }
    // this comment's own line, from under its author down to its first reply
    if (guide.hasReplies && headerHeight > 0f) {
        val x = lineX(guide.depth)
        drawLine(Colors.CommentRail, Offset(x, headerMid + headerHeight / 2 + 4.dp.toPx()), Offset(x, size.height), stroke)
    }
}

/**
 * on TV, a block the remote can land on, with a ring while focused; on a phone just the content,
 * tappable when [clickableOnPhone]
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusBlock(
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    clickableOnPhone: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!isTv) {
        val tap = if (clickableOnPhone) Modifier.combinedClickable(onLongClick = onLongClick, onClick = onClick) else Modifier
        Box(modifier.then(tap)) { content() }
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .padding(horizontal = BLOCK_INSET_H, vertical = BLOCK_INSET_V)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFocused) Colors.Card else Color.Transparent)
            .border(3.dp, if (isFocused) Colors.FocusRing else Color.Transparent, RoundedCornerShape(14.dp))
            .combinedClickable(interactionSource = interaction, indication = null, onLongClick = onLongClick, onClick = onClick),
    ) {
        content()
    }
}

@Composable
private fun ActionChip(label: String, selected: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val type = LocalRedditType.current
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    Text(
        text = label,
        fontSize = type.tileMeta,
        fontWeight = FontWeight.SemiBold,
        color = when {
            isFocused -> Colors.TextOnFocused
            selected -> Colors.Accent
            else -> Colors.Text
        },
        modifier = modifier
            .clip(CircleShape)
            .background(if (isFocused) Colors.CardFocused else Colors.Card)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
