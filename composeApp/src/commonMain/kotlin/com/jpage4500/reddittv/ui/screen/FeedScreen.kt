package com.jpage4500.reddittv.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.data.*
import com.jpage4500.reddittv.ui.components.BackPress
import com.jpage4500.reddittv.ui.components.JoinButton
import com.jpage4500.reddittv.ui.components.JoinState
import com.jpage4500.reddittv.ui.components.PostCard
import com.jpage4500.reddittv.ui.components.PostTile
import com.jpage4500.reddittv.ui.components.focusRing
import com.jpage4500.reddittv.ui.components.focusRingBorder
import com.jpage4500.reddittv.ui.setting.SettingChoiceDialog
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.ui.theme.LocalRedditType
import com.jpage4500.reddittv.ui.theme.changeTextSize
import com.jpage4500.reddittv.util.SettingsManager
import com.jpage4500.reddittv.util.Utils
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.*
import reddittv.composeapp.generated.resources.*

// a wider window (desktop) fits more columns rather than stretching the tiles. this is the
// narrowest tile at the default column count; fewer columns scale it up so a TV-sized screen
// still shows exactly the chosen number
private val MIN_TILE_WIDTH = 200.dp
private val GRID_EDGE = 48.dp
private val GRID_GAP = 16.dp
// the grid's top padding — the header sits over it
private val GRID_TOP = 88.dp
private val GRID_BOTTOM = 40.dp
// keys a grid tile ignores while the menu is open over it
private val MENU_BLOCKED_KEYS = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * TV feed (design boards 2 and 3): a grid of [AppPref.GRID_COLUMNS] columns (4 by default) that
 * fits two full rows, under a header with the feed's name. the menu is hidden until LEFT from the first column, then slides out over the
 * grid. home and a community are the same screen with a different [FeedState]
 *
 * [focusIndex] is the tile to focus when the screen comes back (from a post), kept by the caller
 * with [gridState] so position survives the post screen.
 *
 * a landscape phone or a desktop window gets this screen too (not [isTv]): there the header has a
 * menu button, and nothing takes focus on its own
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvFeedScreen(
    isTv: Boolean,
    state: FeedState,
    gridState: LazyGridState,
    focusIndex: MutableState<Int>,
    sidebar: @Composable (isOpen: Boolean, close: () -> Unit) -> Unit,
    onOpen: (index: Int, Post) -> Unit,
    onLongPress: (Post) -> Unit,
    join: JoinState?,
) {
    val type = LocalRedditType.current
    var isMenuOpen by remember { mutableStateOf(false) }
    var isSortOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val gridColumns by SettingsManager.getIntAsState(AppPref.GRID_COLUMNS)
    val minColumns = gridColumns.coerceIn(GRID_COLUMN_CHOICES.first(), GRID_COLUMN_CHOICES.last())
    var columns by remember { mutableStateOf(minColumns) }
    val isFocusDriven = remember { Utils.isFocusDriven() }

    // page in more while there are still two rows to go
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.posts.size - columns * 3
        }
    }
    LaunchedEffect(nearEnd, state.posts.size) {
        if (nearEnd && state.posts.isNotEmpty()) state.loadMore()
    }

    // "Load more" at the end of the grid: once its posts arrive, focus moves to the first of them
    // (the button goes away with them, and would take focus with it)
    var resumeFrom by remember { mutableStateOf(-1) }
    LaunchedEffect(state.posts.size, resumeFrom) {
        if (resumeFrom in 0 until state.posts.size) {
            focusIndex.value = resumeFrom
            resumeFrom = -1
            if (isFocusDriven) {
                // let the tile pick up the focus requester first
                withFrameNanos { }
                runCatching { focusRequester.requestFocus() }
            }
        }
    }

    // first load, or back from a post: put focus on the tile. the tile to go back to is taken
    // before anything can steal it — coming back, the system focuses the first tile it finds on its
    // own, and that tile's onFocusChanged would overwrite it
    val restoreIndex = remember { focusIndex.value }
    var isRestored by remember { mutableStateOf(false) }
    val hasPosts = state.posts.isNotEmpty()
    LaunchedEffect(hasPosts, isMenuOpen) {
        if (hasPosts && !isMenuOpen) {
            if (!isRestored) {
                isRestored = true
                focusIndex.value = restoreIndex
            }
            if (focusIndex.value >= state.posts.size) focusIndex.value = 0
            gridState.scrollToItemIfHidden(focusIndex.value)
            // a remote or keyboard needs somewhere to start; on a touch screen a focused tile would
            // just look selected
            if (isFocusDriven) {
                // let the tile pick up the focus requester first
                withFrameNanos { }
                runCatching { focusRequester.requestFocus() }
            }
        }
    }

    // an empty feed ("Nothing here") has no tiles: the keyboard/remote starts on the sort button,
    // where LEFT (TV) or the ☰ next to it (desktop) gets to the menu
    val sortFocusRequester = remember { FocusRequester() }
    val isEmpty = !hasPosts && state.isEnd
    LaunchedEffect(isEmpty, isMenuOpen) {
        if (isEmpty && !isMenuOpen && isFocusDriven) {
            withFrameNanos { }
            runCatching { sortFocusRequester.requestFocus() }
        }
    }

    // TV: BACK opens the menu; with it open, BACK leaves the app as usual (RIGHT closes it).
    // elsewhere BACK (ESC) closes the menu, and leaves the app from the grid
    if (isTv) {
        if (!isMenuOpen) BackPress { isMenuOpen = true }
    } else if (isMenuOpen) {
        BackPress { isMenuOpen = false }
    }

    // the header's buttons (☰, A−, A+): DOWN goes to the grid's tile, and LEFT from ☰ opens the menu.
    // none take focus while the menu is over the grid
    fun Modifier.headerKeys(leftOpensMenu: Boolean = false): Modifier = this
        .focusProperties { canFocus = !isMenuOpen }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionDown -> hasPosts.also { if (it) runCatching { focusRequester.requestFocus() } }
                Key.DirectionLeft -> leftOpensMenu.also { if (it) isMenuOpen = true }
                else -> false
            }
        }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Colors.Background)
            .then(if (isTv) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
            .onPreviewKeyEvent { event ->
            // RIGHT closes the menu — watched here, not on the menu, so it works wherever focus is
            if (isMenuOpen && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                isMenuOpen = false
                true
            } else false
        },
    ) {
        if (state.posts.isEmpty()) LogoBackdrop()
        val minTileWidth = MIN_TILE_WIDTH * GRID_COLUMNS_DEFAULT / minColumns
        columns = ((maxWidth - GRID_EDGE * 2 + GRID_GAP) / (minTileWidth + GRID_GAP)).toInt().coerceAtLeast(minColumns)
        // a focused tile scrolls clear of the header, not just into the window under it
        val density = LocalDensity.current
        val bringIntoViewSpec = remember(density) {
            with(density) { ClearOfHeaderSpec(GRID_TOP.toPx(), GRID_BOTTOM.toPx()) }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize()
                    // focus left on a tile under the open menu: the remote doesn't move through
                    // (or open) posts that can't be seen. RIGHT, which closes it, is taken above
                    .onPreviewKeyEvent { event -> isMenuOpen && event.key in MENU_BLOCKED_KEYS },
                contentPadding = PaddingValues(start = GRID_EDGE, end = GRID_EDGE, top = GRID_TOP, bottom = GRID_BOTTOM),
                horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                itemsIndexed(state.posts, key = { _, post -> post.id }) { index, post ->
                    PostTile(
                        post = post,
                        modifier = Modifier
                            .then(if (index == focusIndex.value) Modifier.focusRequester(focusRequester) else Modifier)
                            .onFocusChanged { if (it.isFocused) focusIndex.value = index }
                            .onKeyEvent { event ->
                                // LEFT off the first column opens the menu
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && index % columns == 0) {
                                    isMenuOpen = true
                                    true
                                } else false
                            },
                        onLongClick = { onLongPress(post) },
                        onClick = { onOpen(index, post) },
                    )
                }
                if (state.showFooter(isResuming = resumeFrom >= 0)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LoadMoreFooter(state) { resumeFrom = state.posts.size }
                    }
                }
            }
        }

        // header over the grid, fading into it; it slides away scrolling down and back scrolling up
        val isHeaderShown by rememberIsHeaderShown { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
        AnimatedVisibility(
            visible = isHeaderShown || isEmpty,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(Brush.verticalGradient(0.78f to Colors.Background, 1f to Colors.Background.copy(alpha = 0f)))
                .padding(start = if (isTv) GRID_EDGE else 36.dp, end = GRID_EDGE, top = 22.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (!isTv) {
                // no remote to press LEFT with: the menu opens from here
                Text(
                    text = "☰",
                    fontSize = type.header,
                    color = Colors.Text,
                    modifier = Modifier.headerKeys(leftOpensMenu = true).headerButton { isMenuOpen = true },
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(feedLabel(state.feed), fontSize = type.header, fontWeight = FontWeight.Bold, color = Colors.Text)
            Spacer(Modifier.width(14.dp))
            // the sort: OK (or a click) picks another. on TV nothing's left of it, so LEFT opens the menu
            Text(
                text = stringResource(state.sort.labelRes) + " ▾",
                fontSize = type.tileMeta,
                color = Colors.TextDim,
                modifier = Modifier.padding(top = 6.dp).focusRequester(sortFocusRequester)
                    .headerKeys(leftOpensMenu = isTv).headerButton { isSortOpen = true },
            )
            Spacer(Modifier.weight(1f))
            if (isTv) {
                Text(stringResource(Res.string.menu_hint), fontSize = type.tileMeta, color = Colors.TextDim, modifier = Modifier.padding(top = 6.dp))
            } else {
                Text("A−", fontSize = type.header, color = Colors.TextDim, modifier = Modifier.headerKeys().headerButton { changeTextSize(bigger = false) })
                Text("A+", fontSize = type.header, color = Colors.Text, modifier = Modifier.headerKeys().headerButton { changeTextSize(bigger = true) })
            }
            if (join != null) {
                Spacer(Modifier.width(18.dp))
                JoinButton(
                    join = join,
                    modifier = Modifier
                        // not while the menu is over the grid: the remote stays in the menu
                        .focusProperties { canFocus = !isMenuOpen }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                // UP from the top row lands here; DOWN goes back to the tile it came from
                                Key.DirectionDown -> hasPosts.also { if (it) runCatching { focusRequester.requestFocus() } }
                                // nothing's left of it in the header: LEFT opens the menu, as it does from the grid
                                Key.DirectionLeft -> {
                                    isMenuOpen = true
                                    true
                                }
                                else -> false
                            }
                        },
                )
            }
        }
        }

        FeedStatus(state, Modifier.align(Alignment.Center))

        if (isSortOpen) FeedSortDialog(state.sort) { isSortOpen = false }

        // the menu, over the grid
        AnimatedVisibility(visible = isMenuOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Colors.Scrim).clickable { isMenuOpen = false })
        }
        AnimatedVisibility(
            visible = isMenuOpen,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
        ) {
            Box(modifier = Modifier.width(280.dp).fillMaxHeight()) {
                sidebar(true) { isMenuOpen = false }
            }
        }
    }
}

/**
 * phone feed (design board 5): a list with about two media posts per screen, a top bar with the
 * menu and the text size
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneFeedScreen(
    state: FeedState,
    listState: LazyListState,
    focusIndex: MutableState<Int>,
    sidebar: @Composable (isOpen: Boolean, close: () -> Unit) -> Unit,
    onOpen: (index: Int, Post) -> Unit,
    onLongPress: (Post) -> Unit,
    join: JoinState?,
) {
    val type = LocalRedditType.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var isSortOpen by remember { mutableStateOf(false) }

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.posts.size - 5
        }
    }
    LaunchedEffect(nearEnd, state.posts.size) {
        if (nearEnd && state.posts.isNotEmpty()) state.loadMore()
    }

    // the post a video autoplays in: with a keyboard the focused one, by touch the one taking up
    // the most of the screen (the first of a tie, so the top post wins at the top of the list)
    val activeIndex by remember {
        derivedStateOf {
            if (Utils.isFocusDriven()) return@derivedStateOf focusIndex.value
            val info = listState.layoutInfo
            info.visibleItemsInfo.maxByOrNull { item ->
                val top = maxOf(item.offset, info.viewportStartOffset)
                val bottom = minOf(item.offset + item.size, info.viewportEndOffset)
                bottom - top
            }?.index ?: -1
        }
    }

    if (drawerState.isOpen) BackPress { scope.launch { drawerState.close() } }

    // desktop (a narrow window): the keyboard starts on the first post — or, back from a post, on
    // that one — as a remote does on the grid
    val isFocusDriven = remember { Utils.isFocusDriven() }
    val focusRequester = remember { FocusRequester() }
    val restoreIndex = remember { focusIndex.value }
    val hasPosts = state.posts.isNotEmpty()
    if (isFocusDriven) LaunchedEffect(hasPosts) {
        if (hasPosts) {
            focusIndex.value = if (restoreIndex < state.posts.size) restoreIndex else 0
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == focusIndex.value }) listState.scrollToItem(focusIndex.value)
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }
    }
    // an empty feed ("Nothing here") has no posts to start on: start on ☰
    val menuFocusRequester = remember { FocusRequester() }
    val isEmpty = !hasPosts && state.isEnd
    if (isFocusDriven) LaunchedEffect(isEmpty, drawerState.isOpen) {
        if (isEmpty && !drawerState.isOpen) {
            withFrameNanos { }
            runCatching { menuFocusRequester.requestFocus() }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = Colors.Sidebar,
                windowInsets = WindowInsets(0),
            ) {
                Box(
                    Modifier.windowInsetsPadding(WindowInsets.systemBars).onPreviewKeyEvent { event ->
                        // RIGHT closes the menu, as on the grid's slide-over one
                        if (drawerState.isOpen && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                            scope.launch { drawerState.close() }
                            true
                        } else false
                    },
                ) {
                    sidebar(drawerState.targetValue == DrawerValue.Open) { scope.launch { drawerState.close() } }
                }
            }
        },
    ) {
        // the top bar folds away scrolling down the list and comes back scrolling up
        val isHeaderShown by rememberIsHeaderShown { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
        Column(modifier = Modifier.fillMaxSize().background(Colors.Background).windowInsetsPadding(WindowInsets.systemBars)) {
            AnimatedVisibility(
                visible = isHeaderShown || isEmpty,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderTextButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier.focusRequester(menuFocusRequester).onPreviewKeyEvent { event ->
                        // nothing's left of it: LEFT opens the menu, as it does from the grid
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                            scope.launch { drawerState.open() }
                            true
                        } else false
                    },
                ) {
                    Text("☰", fontSize = type.header, color = Colors.Text)
                }
                Text(
                    text = feedLabel(state.feed),
                    fontSize = type.header,
                    fontWeight = FontWeight.Bold,
                    color = Colors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                HeaderTextButton(onClick = { isSortOpen = true }) {
                    Text(stringResource(state.sort.labelRes) + " ▾", fontSize = type.tileMeta, color = Colors.TextDim)
                }
                Spacer(Modifier.weight(1f))
                HeaderTextButton(onClick = { changeTextSize(bigger = false) }) { Text("A−", color = Colors.TextDim) }
                HeaderTextButton(onClick = { changeTextSize(bigger = true) }) { Text("A+", color = Colors.Text) }
                if (join != null) JoinButton(join, Modifier.padding(end = 8.dp))
            }
            }
            Box(Modifier.weight(1f)) {
                if (state.posts.isEmpty()) LogoBackdrop()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(state.posts, key = { _, post -> post.id }) { index, post ->
                        PostCard(
                            post = post,
                            modifier = Modifier
                                .then(if (index == focusIndex.value) Modifier.focusRequester(focusRequester) else Modifier)
                                .onFocusChanged { if (it.isFocused) focusIndex.value = index },
                            isActive = index == activeIndex && !drawerState.isOpen,
                            onLongClick = { onLongPress(post) }, onClick = { onOpen(index, post) })
                    }
                    if (state.showFooter(isResuming = false)) {
                        item { LoadMoreFooter(state) {} }
                    }
                }
                FeedStatus(state, Modifier.align(Alignment.Center))
            }
        }
    }
    if (isSortOpen) FeedSortDialog(state.sort) { isSortOpen = false }
}

/** pick how feeds are sorted (the same choice as in settings); the feed reloads as it changes */
@Composable
private fun FeedSortDialog(sort: FeedSort, onDismiss: () -> Unit) {
    SettingChoiceDialog(
        icon = Res.drawable.icon_refresh,
        labelRes = Res.string.sort_feeds_by,
        choices = FeedSort.entries.map { stringResource(it.labelRes) },
        descriptions = FeedSort.entries.map { stringResource(it.descRes) },
        infoText = stringResource(Res.string.sort_feeds_info),
        selectedIndex = sort.ordinal,
        onSelect = { SettingsManager.setEnum(AppPref.FEED_SORT, FeedSort.entries[it]) },
        onDismiss = onDismiss,
    )
}

/** a button in the phone header (☰, A−, A+), with the focus ring on its own shape */
@Composable
private fun HeaderTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = modifier,
        border = focusRingBorder(interactionSource),
        interactionSource = interactionSource,
        content = content,
    )
}

/** a small tappable word in the wide header (A−, A+) */
@Composable
private fun Modifier.headerButton(onClick: () -> Unit): Modifier = this
    .offset(y = (-6).dp)
    .focusRing(CircleShape)
    .clip(CircleShape)
    .clickable(onClick = onClick)
    .padding(horizontal = 8.dp, vertical = 4.dp)

/** the header's name for [feed]: "Home", "r/space" */
@Composable
fun feedLabel(feed: Feed): String = if (feed.isHome) stringResource(Res.string.feed_home) else feed.label

/** spinner on the first load, or the error with a retry */
@Composable
private fun FeedStatus(state: FeedState, modifier: Modifier) {
    val type = LocalRedditType.current
    when {
        state.posts.isNotEmpty() -> {}
        state.isLoading -> CircularProgressIndicator(modifier = modifier, color = Colors.Accent)
        state.error != null -> StatusMessage(state.error ?: "", stringResource(Res.string.try_again), modifier) { state.refresh() }
        state.isEnd -> Text(stringResource(Res.string.nothing_here), modifier = modifier, color = Colors.TextDim, fontSize = type.postBody)
    }
}

/** a message over a button, taking focus for a remote since nothing else on screen can */
@Composable
private fun StatusMessage(message: String, button: String, modifier: Modifier, onClick: () -> Unit) {
    val type = LocalRedditType.current
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(Unit) { if (Utils.isFocusDriven()) runCatching { focusRequester.requestFocus() } }
    Column(modifier = modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = Colors.TextDim, fontSize = type.postBody)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onClick,
            modifier = Modifier.focusRequester(focusRequester),
            border = focusRingBorder(interactionSource),
            interactionSource = interactionSource,
        ) { Text(button) }
    }
}

/**
 * the footer under a feed's posts: paging stopped because the hide settings filtered out the last
 * few pages, or a page failed — or [isResuming], the button was just pressed and its page is loading
 */
private fun FeedState.showFooter(isResuming: Boolean): Boolean =
    posts.isNotEmpty() && !isEnd && (isStalled || error != null || (isResuming && isLoading))

/** "Load more" (or "Try again" after an error) under the posts; [onClick] runs before it loads */
@Composable
private fun LoadMoreFooter(state: FeedState, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Button(
            onClick = {
                onClick()
                state.loadMoreAnyway()
            },
            border = focusRingBorder(interactionSource),
            interactionSource = interactionSource,
        ) {
            Text(stringResource(if (state.error != null) Res.string.try_again else Res.string.load_more))
        }
    }
}

// how far the feed has to move before the header collapses or comes back, so a jitter doesn't flicker it
private val HEADER_SCROLL_SLOP = 8.dp

/**
 * whether the feed's header shows: it collapses as the feed scrolls down and comes back as it
 * scrolls up, or at the top. [position] is the first visible item and its scroll offset
 */
@Composable
private fun rememberIsHeaderShown(position: () -> Pair<Int, Int>): State<Boolean> {
    val slop = with(LocalDensity.current) { HEADER_SCROLL_SLOP.toPx() }
    val isShown = remember { mutableStateOf(true) }
    LaunchedEffect(slop) {
        var last = position()
        snapshotFlow(position).collect { pos ->
            val (index, offset) = pos
            val isDown = index > last.first || (index == last.first && offset > last.second + slop)
            val isUp = index < last.first || (index == last.first && offset < last.second - slop)
            if (isDown || isUp) last = pos
            when {
                index == 0 && offset <= slop -> isShown.value = true
                isDown -> isShown.value = false
                isUp -> isShown.value = true
            }
        }
    }
    return isShown
}

/** scroll so [index] is on screen, without moving when it already is */
private suspend fun LazyGridState.scrollToItemIfHidden(index: Int) {
    val visible = layoutInfo.visibleItemsInfo
    if (visible.none { it.index == index }) scrollToItem(index)
}

/**
 * bring-into-view for the feed grid: keeps a focused tile [top] px below the top edge (clear of the
 * header drawn over the grid) and [bottom] px above the bottom. the default only gets it inside the
 * window, so moving UP left the row under the header
 */
@OptIn(ExperimentalFoundationApi::class)
private class ClearOfHeaderSpec(private val top: Float, private val bottom: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = containerSize - bottom
        return when {
            offset < top -> offset - top
            // a tile taller than the space keeps its top in view
            offset + size > end -> minOf(offset + size - end, offset - top)
            else -> 0f
        }
    }
}
