package com.jpage4500.reddittv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.data.*
import com.jpage4500.reddittv.manager.AppEvents
import com.jpage4500.reddittv.manager.RedditClient
import com.jpage4500.reddittv.ui.components.JoinState
import com.jpage4500.reddittv.ui.components.SidebarContent
import com.jpage4500.reddittv.ui.components.arrowKeyFocus
import com.jpage4500.reddittv.ui.dialog.PostOptionsDialog
import com.jpage4500.reddittv.ui.dialog.common.ConfirmationDialog
import com.jpage4500.reddittv.ui.dialog.SettingsDialog
import com.jpage4500.reddittv.ui.screen.*
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.ui.theme.ProvideRedditType
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.Utils
import com.jpage4500.reddittv.util.ToastManager
import com.jpage4500.reddittv.util.SettingsManager
import com.jpage4500.reddittv.util.WebSession
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.*
import reddittv.composeapp.generated.resources.*

private val log = LoggerConfig.createLogger("RootScreen")

/**
 * the app's screens: sign in → feed (or search) → post. the feed's state (pages, scroll, focused tile) is held
 * here so it's all still there coming back from a post
 */
@Composable
fun RootScreen(isTv: Boolean) {
    ProvideRedditType(isTv = isTv) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Colors.Background)
                // TV: the remote's D-pad already moves focus
                .then(if (isTv) Modifier else Modifier.arrowKeyFocus()),
        ) {
            // a landscape phone, tablet or desktop window gets the TV's layout (grid, slide-over menu)
            val wide = isTv || (maxWidth > maxHeight && maxWidth >= WIDE_MIN_WIDTH)
            RootContent(isTv, wide)
        }
    }
}

// narrower than this, a landscape window still gets the phone's list
private val WIDE_MIN_WIDTH = 600.dp

/**
 * [isTv] is a real TV (a remote, no touch); [wide] is the TV's layout, which a landscape phone or
 * a desktop window gets too
 */
@Composable
private fun RootContent(isTv: Boolean, wide: Boolean) {
    // null until the session has been read (desktop reads its browser's cookies asynchronously)
    var isReady by remember { mutableStateOf(false) }
    var showSignIn by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var subreddits by remember { mutableStateOf<List<Subreddit>>(emptyList()) }
    // bumped after a sign-in or out: the feed and menu reload as the new user
    var sessionVersion by remember { mutableStateOf(0) }
    val user by RedditClient.user.collectAsState()

    LaunchedEffect(Unit) {
        WebSession.refresh()
        val signedIn = RedditClient.isSignedIn
        log.d("RootContent: signed in:$signedIn")
        showSignIn = !signedIn && !SettingsManager.getBoolean(AppPref.SIGN_IN_SKIPPED)
        isReady = true
    }
    // keyed on isReady too: on desktop the session is read after this first runs
    LaunchedEffect(isReady, sessionVersion, user?.name) {
        if (!isReady) return@LaunchedEffect
        RedditClient.refreshUser()
        RedditClient.getSubreddits().onSuccess { subreddits = it }
    }
    LaunchedEffect(Unit) {
        AppEvents.openSettings.collect { showSettings = true }
    }

    if (!isReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Colors.Accent) }
        return
    }

    if (showSignIn) {
        SignInScreen(isTv = isTv) { signedIn ->
            if (!signedIn) SettingsManager.setBoolean(AppPref.SIGN_IN_SKIPPED, true)
            showSignIn = false
            sessionVersion++
        }
        return
    }

    var feed by remember { mutableStateOf(Feed(SettingsManager.getStringOrEmpty(AppPref.LAST_FEED))) }
    val sort by SettingsManager.getEnumAsState<FeedSort>(AppPref.FEED_SORT)
    val feedState = key(sessionVersion) { rememberFeedState(feed, sort) }
    val gridState = key(feed, sort, sessionVersion) { rememberLazyGridState() }
    val listState = key(feed, sort, sessionVersion) { rememberLazyListState() }
    val focusIndex = key(feed, sort, sessionVersion) { remember { mutableStateOf(0) } }
    var openPost by remember { mutableStateOf<Post?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    // the post whose options are open (long press on a tile or card)
    var optionsPost by remember { mutableStateOf<Post?>(null) }
    // the community a join/leave is on its way to reddit for
    var joinPending by remember { mutableStateOf<String?>(null) }
    // the community "Leave r/…?" is asking about
    var confirmLeave by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun openFeed(newFeed: Feed) {
        feed = newFeed
        SettingsManager.setString(AppPref.LAST_FEED, newFeed.path)
    }

    // join or leave [name], then reload the menu's communities so it shows there (or doesn't).
    // leaving asks first ([confirmed] once it has)
    fun setJoined(name: String, join: Boolean, confirmed: Boolean = false) {
        if (user == null) {
            showSignIn = true
            return
        }
        if (!join && !confirmed) {
            confirmLeave = name
            return
        }
        joinPending = name
        scope.launch {
            RedditClient.subscribe(name, join)
                .onSuccess {
                    ToastManager.showToast(if (join) Res.string.post_joined else Res.string.post_left, name, success = true)
                    // show it straight away: the full reload below (with icons) takes a moment
                    subreddits = if (join) {
                        (subreddits + Subreddit(name, "", null, 0)).distinctBy { it.name.lowercase() }.sortedBy { it.name.lowercase() }
                    } else {
                        subreddits.filterNot { it.name.equals(name, ignoreCase = true) }
                    }
                    joinPending = null
                    RedditClient.getSubreddits().onSuccess { subreddits = it }
                }
                .onFailure {
                    joinPending = null
                    ToastManager.showToast(if (join) Res.string.post_join_failed else Res.string.post_leave_failed, name, success = false)
                }
        }
    }

    // whether the user is in [name]: signed out, the menu's communities are reddit's popular ones
    fun isJoined(name: String) = user != null && subreddits.any { it.name.equals(name, ignoreCase = true) }

    // the Join button for community [name], in a feed's or a post's header
    fun joinState(name: String): JoinState {
        val joined = isJoined(name)
        return JoinState(isJoined = joined, isPending = joinPending == name, onClick = { setJoined(name, !joined) })
    }
    // signed out there's nothing to join with
    val join = if (user == null) null else feed.subredditName?.let(::joinState)

    val sidebar: @Composable (isOpen: Boolean, close: () -> Unit) -> Unit = { isOpen, close ->
        SidebarContent(
            currentFeed = feed,
            subreddits = subreddits,
            user = user,
            // a touch screen has nothing to put focus on: a focused row would just look selected
            takeFocus = isOpen && Utils.isFocusDriven(),
            onFeed = {
                close()
                openFeed(it)
            },
            onSearch = {
                close()
                showSearch = true
            },
            onSettings = {
                close()
                showSettings = true
            },
            onAccount = {
                close()
                if (user == null) showSignIn = true else confirmSignOut = true
            },
        )
    }

    val post = openPost
    if (post != null) {
        PostScreen(
            initial = post,
            isTv = isTv,
            wide = wide,
            onPostChanged = { feedState.updatePost(it) },
            onBack = { openPost = null },
            join = if (user == null) null else joinState(post.subreddit),
        )
    } else if (showSearch) {
        SearchScreen(
            isTv = isTv,
            onOpen = {
                showSearch = false
                openFeed(it)
            },
            onBack = { showSearch = false },
        )
    } else if (wide) {
        TvFeedScreen(
            isTv = isTv,
            state = feedState,
            gridState = gridState,
            focusIndex = focusIndex,
            sidebar = sidebar,
            onOpen = { i, p ->
                // a mouse click doesn't move the focus: remember the post to come back to
                focusIndex.value = i
                openPost = p
            },
            onLongPress = { optionsPost = it },
            join = join,
        )
    } else {
        PhoneFeedScreen(
            state = feedState,
            listState = listState,
            focusIndex = focusIndex,
            sidebar = sidebar,
            onOpen = { i, p ->
                focusIndex.value = i
                openPost = p
            },
            onLongPress = { optionsPost = it },
            join = join,
        )
    }

    optionsPost?.let { p ->
        val community = Feed.subreddit(p.subreddit)
        PostOptionsDialog(
            post = p,
            // signed out, the menu's communities are reddit's popular ones, not joined ones
            isJoined = isJoined(p.subreddit),
            isSignedIn = user != null,
            showCommunity = !feed.path.equals(community.path, ignoreCase = true),
            waitForOkRelease = isTv,
            onOpen = { openPost = p },
            onCommunity = { openFeed(community) },
            onJoin = { join -> setJoined(p.subreddit, join) },
            onDismiss = { optionsPost = null },
        )
    }

    confirmLeave?.let { name ->
        ConfirmationDialog(
            title = stringResource(Res.string.leave_title, name),
            message = stringResource(Res.string.leave_message),
            okText = stringResource(Res.string.leave),
            onDismiss = { confirmLeave = null },
            onConfirm = { setJoined(name, join = false, confirmed = true) },
        )
    }

    if (showSettings) {
        SettingsDialog(showGridSize = wide, onDismiss = { showSettings = false })
    }

    if (confirmSignOut) {
        ConfirmationDialog(
            title = stringResource(Res.string.sign_out_confirm_title),
            message = stringResource(Res.string.sign_out_confirm_msg),
            onDismiss = { confirmSignOut = false },
            onConfirm = {
                RedditClient.signOut()
                confirmSignOut = false
                // back to the QR code sign-in; the feed and menu reload as whoever comes next
                showSignIn = true
                sessionVersion++
            },
        )
    }
}
