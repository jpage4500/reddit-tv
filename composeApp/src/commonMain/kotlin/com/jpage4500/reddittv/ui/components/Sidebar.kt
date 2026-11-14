package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpage4500.reddittv.data.Branding
import com.jpage4500.reddittv.data.Feed
import com.jpage4500.reddittv.data.RedditUser
import com.jpage4500.reddittv.data.Subreddit
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.ui.theme.LocalRedditType
import org.jetbrains.compose.resources.*
import reddittv.composeapp.generated.resources.*

/**
 * the menu (design board 3): the app's logo, name and user (select to sign in / out), Search, Home / Popular / All, the user's
 * communities, then Settings. the same list is the TV's slide-over sidebar and the phone's drawer.
 * [currentFeed]'s row is highlighted and, on TV or with a keyboard, takes focus when the menu opens
 */
@Composable
fun SidebarContent(
    currentFeed: Feed,
    subreddits: List<Subreddit>,
    user: RedditUser?,
    // the menu has just opened for a remote or a keyboard: its current row takes focus
    takeFocus: Boolean,
    onFeed: (Feed) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    // the header's user: signed in, sign out (after a confirm); signed out, sign in
    onAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalRedditType.current
    val currentRequester = remember { FocusRequester() }
    val main = listOf(
        Triple(Feed.HOME, stringResource(Res.string.feed_home), MenuIcons.Home),
        Triple(Feed.POPULAR, stringResource(Res.string.feed_popular), MenuIcons.Popular),
        Triple(Feed.ALL, stringResource(Res.string.feed_all), MenuIcons.All),
    )
    val communitiesLabel = stringResource(if (user != null) Res.string.your_communities else Res.string.popular_communities)
    val settingsLabel = stringResource(Res.string.settings)
    val searchLabel = stringResource(Res.string.search)
    val isCurrentListed = main.any { it.first == currentFeed } || subreddits.any { Feed.subreddit(it.name) == currentFeed }
    // the current row's place in the list below: header, Search, the main feeds, then the
    // communities after their label
    val mainIndex = main.indexOfFirst { it.first == currentFeed }
    val subIndex = subreddits.indexOfFirst { Feed.subreddit(it.name) == currentFeed }
    val currentIndex = when {
        mainIndex >= 0 -> 2 + mainIndex
        subIndex >= 0 -> 2 + main.size + 1 + subIndex
        else -> 1
    }
    val listState = rememberLazyListState()
    var hasFocus by remember { mutableStateOf(false) }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxHeight()
            .onFocusChanged { hasFocus = it.hasFocus }
            .background(Colors.Sidebar)
            // UP off the first row or DOWN off the last stays put, rather than focus slipping out to
            // the screen behind the menu (where it can't be seen). RIGHT closes the menu, so it's let go
            .focusProperties {
                onExit = {
                    if (requestedFocusDirection in TRAPPED_DIRECTIONS) cancelFocusChange()
                }
            }
            .focusGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            SidebarHeader(
                userLabel = user?.let { "u/${it.name}" } ?: stringResource(Res.string.sign_in),
                onClick = onAccount,
            )
        }
        item {
            SidebarRow(
                label = searchLabel,
                icon = MenuIcons.Search,
                onClick = onSearch,
                // a feed with no row of its own (a community found by search): focus lands here
                modifier = if (isCurrentListed) Modifier else Modifier.focusRequester(currentRequester),
            )
        }
        items(main) { (feed, label, icon) ->
            SidebarRow(
                label = label,
                icon = icon,
                selected = feed == currentFeed,
                onClick = { onFeed(feed) },
                modifier = if (feed == currentFeed) Modifier.focusRequester(currentRequester) else Modifier,
            )
        }
        if (subreddits.isNotEmpty()) {
            item {
                Text(
                    text = communitiesLabel.uppercase(),
                    fontSize = type.sidebarSection,
                    fontWeight = FontWeight.SemiBold,
                    color = Colors.TextDim,
                    modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp),
                )
            }
            items(subreddits, key = { it.name }) { sub ->
                val feed = Feed.subreddit(sub.name)
                SidebarRow(
                    label = "r/${sub.name}",
                    iconUrl = sub.iconUrl,
                    isCommunity = true,
                    selected = feed == currentFeed,
                    onClick = { onFeed(feed) },
                    modifier = if (feed == currentFeed) Modifier.focusRequester(currentRequester) else Modifier,
                )
            }
        }
        item { Spacer(Modifier.height(14.dp)) }
        item { SidebarRow(label = settingsLabel, icon = MenuIcons.Settings, onClick = onSettings) }
    }
    // the rows aren't laid out on the frame the menu opens (and a community far down the list
    // isn't composed at all), so a request then fails and focus stays on the grid behind the
    // menu: bring the row in, then try again each frame until the menu has focus
    LaunchedEffect(takeFocus) {
        if (!takeFocus) return@LaunchedEffect
        // once laid out, so what's visible is known
        withFrameNanos { }
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == currentIndex }) {
            listState.scrollToItem(currentIndex)
        }
        repeat(FOCUS_ATTEMPTS) {
            withFrameNanos { }
            if (hasFocus) return@LaunchedEffect
            runCatching { currentRequester.requestFocus() }
        }
    }
}

/** the app's logo and name over the user (or "Sign in"); selecting it signs in or out */
@Composable
private fun SidebarHeader(userLabel: String, onClick: () -> Unit) {
    val type = LocalRedditType.current
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Colors.CardFocused else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Image(
            painter = painterResource(Branding.logoRound),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                Branding.appName,
                color = if (isFocused) Colors.TextOnFocused else Colors.Text,
                fontWeight = FontWeight.Bold,
                fontSize = type.header,
            )
            Text(
                userLabel,
                color = if (isFocused) Colors.TextOnFocused else Colors.TextDim,
                fontSize = type.tileMeta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SidebarRow(
    label: String,
    modifier: Modifier = Modifier,
    iconUrl: String? = null,
    // a community: its icon, or reddit's "r/" circle when it has none (or it won't load)
    isCommunity: Boolean = false,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val type = LocalRedditType.current
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(
                when {
                    isFocused -> Colors.CardFocused
                    selected -> Colors.Card
                    else -> Color.Transparent
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCommunity) {
            CommunityIcon(iconUrl)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    isFocused -> Colors.TextOnFocused
                    selected -> Colors.Accent
                    else -> Colors.Text
                },
                // the same 22dp box as a community's icon, so every label lines up
                modifier = Modifier.size(22.dp).padding(1.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            fontSize = type.sidebarItem,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                isFocused -> Colors.TextOnFocused
                selected -> Colors.Accent
                else -> Colors.Text
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// frames to keep asking for focus when the menu opens
private const val FOCUS_ATTEMPTS = 10

private val TRAPPED_DIRECTIONS = listOf(FocusDirection.Up, FocusDirection.Down, FocusDirection.Left)

/** a community's round icon; "r/" on a colored circle when it has none, as reddit shows it */
@Composable
private fun CommunityIcon(iconUrl: String?) {
    var failed by remember(iconUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier.size(22.dp).clip(CircleShape).background(Colors.SubredditIcon),
        contentAlignment = Alignment.Center,
    ) {
        if (iconUrl == null || failed) {
            Text("r/", color = Colors.Text, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        } else {
            AppImage(url = iconUrl, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { failed = true })
        }
    }
}
