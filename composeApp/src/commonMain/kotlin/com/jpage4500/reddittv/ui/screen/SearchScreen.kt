package com.jpage4500.reddittv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.data.AppPref
import com.jpage4500.reddittv.data.Feed
import com.jpage4500.reddittv.data.Subreddit
import com.jpage4500.reddittv.manager.RedditClient
import com.jpage4500.reddittv.ui.components.AppIcon
import com.jpage4500.reddittv.ui.components.AppImage
import com.jpage4500.reddittv.ui.components.BackPress
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.ui.theme.LocalRedditType
import com.jpage4500.reddittv.util.JsonUtils
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.RedditFormat
import com.jpage4500.reddittv.util.SettingsManager
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.*
import reddittv.composeapp.generated.resources.*

private val log = LoggerConfig.createLogger("SearchScreen")

// recent searches kept
private const val MAX_RECENT = 12

// wait this long after the last key before asking reddit for matches
private const val SEARCH_DELAY_MS = 300L

// what reddit allows in a community's name
private val SUBREDDIT_NAME = Regex("^[A-Za-z0-9_]{2,21}$")

// the TV keyboard: letters and digits in rows of KEY_COLUMNS, then a row of TvKey.Special
private const val KEY_COLUMNS = 6
private const val KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"

/** one row of the list beside (TV) or under (phone) the search field */
private sealed class SearchItem {
    data class Header(val text: StringResource) : SearchItem()
    data class Message(val text: String) : SearchItem()
    data class Community(val sub: Subreddit) : SearchItem()
    data class GoTo(val name: String) : SearchItem()
    data class RecentQuery(val text: String) : SearchItem()
    data object ClearRecent : SearchItem()

    val isRow: Boolean get() = this is Community || this is GoTo || this is RecentQuery || this is ClearRecent
}

/** the TV keyboard's bottom row; [span] is its width in letter keys */
private enum class SpecialKey(val span: Int) {
    UNDERSCORE(1),
    SPACE(2),
    DELETE(2),
    CLEAR(1),
}

/**
 * find a community: a search field over the recent searches, which turn into reddit's matching
 * communities as the name is typed. opening one opens its feed and puts what was typed at the top
 * of the recent searches; picking a recent search types it in again.
 *
 * on a TV there's no system keyboard (Fire TV's covers the whole screen and the results with it):
 * a letter grid sits left of the results instead. RIGHT off the grid goes to the top result, LEFT
 * off a result back to the key it came from. BACK returns to the feed
 */
@Composable
fun SearchScreen(
    isTv: Boolean,
    onOpen: (Feed) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // what was typed, newest first: a search is kept once a community is opened from it
    var recents by remember { mutableStateOf(loadRecents()) }
    var results by remember { mutableStateOf<List<Subreddit>>(emptyList()) }
    // the query [results] are for, so a stale list isn't shown as "no matches"
    var resultsFor by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    // reddit refused the search (signed out it refuses most networks)
    var isSearchFailed by remember { mutableStateOf(false) }
    val trimmed = query.trim().removePrefix("/").removePrefix("r/")

    LaunchedEffect(trimmed) {
        if (trimmed.isEmpty()) {
            results = emptyList()
            resultsFor = ""
            isSearching = false
            return@LaunchedEffect
        }
        delay(SEARCH_DELAY_MS)
        isSearching = true
        RedditClient.searchSubreddits(trimmed, SettingsManager.getBoolean(AppPref.SHOW_NSFW))
            .onSuccess {
                results = it
                isSearchFailed = false
            }
            .onFailure {
                results = emptyList()
                isSearchFailed = !RedditClient.isSignedIn
            }
        resultsFor = trimmed
        isSearching = false
    }

    val noMatches = stringResource(if (isSearchFailed) Res.string.search_needs_sign_in else Res.string.search_no_matches, trimmed)
    val emptyHint = stringResource(Res.string.search_empty)
    val items = buildList {
        if (trimmed.isEmpty()) {
            if (recents.isEmpty()) {
                add(SearchItem.Message(emptyHint))
            } else {
                add(SearchItem.Header(Res.string.search_recent))
                recents.forEach { add(SearchItem.RecentQuery(it)) }
                add(SearchItem.ClearRecent)
            }
        } else {
            // a name typed in full can be opened before (or without) reddit suggesting it
            val exact = results.any { it.name.equals(trimmed, ignoreCase = true) }
            if (!exact && SUBREDDIT_NAME.matches(trimmed)) add(SearchItem.GoTo(trimmed))
            if (results.isNotEmpty()) {
                add(SearchItem.Header(Res.string.search_matching))
                results.forEach { add(SearchItem.Community(it)) }
            } else if (!isSearching && resultsFor == trimmed) {
                add(SearchItem.Message(noMatches))
            }
        }
    }

    /** open [item]; false when it doesn't open a feed (a recent search, Clear recent searches) */
    fun openItem(item: SearchItem): Boolean {
        val name = when (item) {
            is SearchItem.Community -> item.sub.name
            is SearchItem.GoTo -> item.name
            is SearchItem.RecentQuery -> {
                query = item.text
                return false
            }
            SearchItem.ClearRecent -> {
                recents = emptyList()
                saveRecents(recents)
                return false
            }
            else -> return false
        }
        log.d("openItem: $name, from: $trimmed")
        if (trimmed.isNotEmpty()) {
            recents = (listOf(trimmed) + recents.filterNot { it.equals(trimmed, ignoreCase = true) }).take(MAX_RECENT)
            saveRecents(recents)
        }
        onOpen(Feed.subreddit(name))
        return true
    }

    // the list is reddit's answer for what's typed (not a moment-old one, or still on its way)
    val resultsReady = trimmed.isNotEmpty() && !isSearching && resultsFor == trimmed

    BackPress(onBack)

    val listState = rememberLazyListState()
    // a new list starts at its top
    LaunchedEffect(trimmed, resultsFor) { listState.scrollToItem(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.Background)
            .then(if (isTv) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars))
            .onPreviewKeyEvent { event ->
                // desktop: ESC goes back
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onBack()
                    true
                } else false
            },
    ) {
        if (isTv) {
            TvSearch(
                query = query,
                onQueryChange = { query = it },
                isSearching = isSearching,
                resultsReady = resultsReady,
                items = items,
                listState = listState,
                onItem = ::openItem,
            )
        } else {
            PhoneSearch(
                query = query,
                onQueryChange = { query = it },
                isSearching = isSearching,
                items = items,
                listState = listState,
                onItem = ::openItem,
                onBack = onBack,
            )
        }
    }
}

/** TV: the keyboard and what's typed on the left, the list on the right */
@Composable
private fun TvSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    resultsReady: Boolean,
    items: List<SearchItem>,
    listState: LazyListState,
    onItem: (SearchItem) -> Boolean,
) {
    val type = LocalRedditType.current
    val keyCount = KEY_CHARS.length + SpecialKey.entries.size
    val keyRequesters = remember { List(keyCount) { FocusRequester() } }
    // the key the remote was last on: where LEFT off the list goes back to
    var lastKey by remember { mutableStateOf(0) }
    val firstRowRequester = remember { FocusRequester() }
    val firstRow = items.indexOfFirst { it.isRow }

    fun typeChar(c: Char) = onQueryChange(query + c)
    fun delete() = onQueryChange(query.dropLast(1))
    fun focusKey() = runCatching { keyRequesters[lastKey].requestFocus() }
    fun focusList(): Boolean = firstRow >= 0 && runCatching { firstRowRequester.requestFocus() }.isSuccess

    LaunchedEffect(Unit) { focusKey() }

    // a recent search was picked: its row is gone, so wait on the keyboard for its matches, then
    // move onto the top one
    var focusResults by remember { mutableStateOf(false) }
    LaunchedEffect(focusResults, resultsReady) {
        if (focusResults && resultsReady) {
            focusResults = false
            focusList()
        }
    }

    // a keyboard plugged into the TV (or a phone's remote app) types too
    val hardwareKeys = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.Backspace -> {
                delete()
                true
            }
            else -> {
                val c = event.utf16CodePoint.toChar().lowercaseChar()
                if (c in KEY_CHARS || c == '_') {
                    typeChar(c)
                    true
                } else false
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize().then(hardwareKeys).padding(start = 48.dp, end = 48.dp, top = 22.dp, bottom = 16.dp)) {
        Column(Modifier.width(300.dp)) {
            Text(stringResource(Res.string.search), fontSize = type.header, fontWeight = FontWeight.Bold, color = Colors.Text)
            Spacer(Modifier.height(14.dp))
            QueryBox(query = query, isSearching = isSearching, isFocused = false) {
                if (query.isEmpty()) {
                    Text(stringResource(Res.string.search_communities_hint), color = Colors.TextDim, fontSize = type.sidebarItem, maxLines = 1)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(query, color = Colors.Text, fontSize = type.sidebarItem, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Box(Modifier.padding(start = 1.dp).width(2.dp).height(20.dp).background(Colors.Accent))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // letters and digits
            val rows = KEY_CHARS.chunked(KEY_COLUMNS)
            rows.forEachIndexed { rowIndex, chars ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    chars.forEachIndexed { col, c ->
                        val index = rowIndex * KEY_COLUMNS + col
                        KeyboardKey(
                            label = c.toString(),
                            requester = keyRequesters[index],
                            isRightEdge = col == KEY_COLUMNS - 1,
                            onFocused = { lastKey = index },
                            onRight = ::focusList,
                            onClick = { typeChar(c) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            // _ space delete clear
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SpecialKey.entries.forEachIndexed { i, key ->
                    val index = KEY_CHARS.length + i
                    KeyboardKey(
                        label = when (key) {
                            SpecialKey.UNDERSCORE -> "_"
                            SpecialKey.SPACE -> stringResource(Res.string.key_space)
                            SpecialKey.DELETE -> stringResource(Res.string.key_delete)
                            SpecialKey.CLEAR -> stringResource(Res.string.key_clear)
                        },
                        requester = keyRequesters[index],
                        isRightEdge = i == SpecialKey.entries.lastIndex,
                        onFocused = { lastKey = index },
                        onRight = ::focusList,
                        onClick = {
                            when (key) {
                                SpecialKey.UNDERSCORE -> typeChar('_')
                                SpecialKey.SPACE -> typeChar(' ')
                                SpecialKey.DELETE -> delete()
                                SpecialKey.CLEAR -> onQueryChange("")
                            }
                        },
                        modifier = Modifier.weight(key.span.toFloat()),
                    )
                }
            }
        }

        Spacer(Modifier.width(32.dp))

        SearchList(
            items = items,
            listState = listState,
            firstRowRequester = firstRowRequester,
            modifier = Modifier.weight(1f).padding(top = 40.dp),
            rowModifier = Modifier.onPreviewKeyEvent { event ->
                // LEFT off the list: back to the keyboard
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    focusKey()
                    true
                } else false
            },
            onItem = { item ->
                // a recent search typed in again, or the recents cleared: the row's gone, so back to
                // the keyboard
                if (!onItem(item)) {
                    focusKey()
                    focusResults = item is SearchItem.RecentQuery
                }
            },
        )
    }
}

/** phone + desktop: a text field over the list, with the system keyboard */
@Composable
private fun PhoneSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    items: List<SearchItem>,
    listState: LazyListState,
    onItem: (SearchItem) -> Boolean,
    onBack: () -> Unit,
) {
    val type = LocalRedditType.current
    val fieldRequester = remember { FocusRequester() }
    val firstRowRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    // the field keeps its own cursor; a recent search picked from the list puts it at the end
    var field by remember { mutableStateOf(TextFieldValue(query)) }
    if (field.text != query) field = TextFieldValue(query, TextRange(query.length))

    // Search / Enter opens the top match
    fun openFirst() {
        items.firstOrNull { it is SearchItem.GoTo || it is SearchItem.Community }?.let { onItem(it) }
    }

    LaunchedEffect(Unit) {
        runCatching { fieldRequester.requestFocus() }
        keyboard?.show()
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text("←", fontSize = type.header, color = Colors.Text) }
        Text(stringResource(Res.string.search), fontSize = type.header, fontWeight = FontWeight.Bold, color = Colors.Text)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        BasicTextField(
            value = field,
            onValueChange = {
                field = it
                onQueryChange(it.text)
            },
            singleLine = true,
            interactionSource = interaction,
            textStyle = TextStyle(color = Colors.Text, fontSize = type.sidebarItem),
            cursorBrush = SolidColor(Colors.Accent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { openFirst() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fieldRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        // desktop's Enter
                        Key.Enter, Key.NumPadEnter -> {
                            openFirst()
                            true
                        }
                        // the field keeps DOWN for its cursor, so the root's arrow keys never see it:
                        // DOWN goes to the top of the list (the list starts at its top for a new query)
                        Key.DirectionDown -> items.any { it.isRow } && runCatching { firstRowRequester.requestFocus() }.isSuccess
                        else -> false
                    }
                },
            decorationBox = { inner ->
                QueryBox(query = query, isSearching = isSearching, isFocused = isFocused) {
                    if (query.isEmpty()) {
                        Text(stringResource(Res.string.search_communities_hint), color = Colors.TextDim, fontSize = type.sidebarItem, maxLines = 1)
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        SearchList(
            items = items,
            listState = listState,
            firstRowRequester = firstRowRequester,
            modifier = Modifier.fillMaxWidth().weight(1f),
            onItem = { onItem(it) },
        )
    }
}

/** the pill the query shows in: a search icon, the text, a spinner while reddit is asked */
@Composable
private fun QueryBox(
    query: String,
    isSearching: Boolean,
    isFocused: Boolean,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(Colors.Card)
            .border(2.dp, if (isFocused) Colors.Accent else Colors.CardBorder, CircleShape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(res = Res.drawable.search, color = Colors.TextDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) { content() }
        if (isSearching && query.isNotBlank()) {
            CircularProgressIndicator(color = Colors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        }
    }
}

/** one key of the TV keyboard: light when the remote is on it */
@Composable
private fun KeyboardKey(
    label: String,
    requester: FocusRequester,
    isRightEdge: Boolean,
    onFocused: () -> Unit,
    onRight: () -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalRedditType.current
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .height(38.dp)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { event ->
                // RIGHT off the keyboard goes to the top of the list, not whichever row is level
                isRightEdge && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight && onRight()
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Colors.CardFocused else Colors.Card)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = if (label.length > 1) type.tileMeta else type.sidebarItem,
            fontWeight = FontWeight.Medium,
            color = if (isFocused) Colors.TextOnFocused else Colors.Text,
            maxLines = 1,
        )
    }
}

@Composable
private fun SearchList(
    items: List<SearchItem>,
    listState: LazyListState,
    firstRowRequester: FocusRequester?,
    onItem: (SearchItem) -> Unit,
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
) {
    val type = LocalRedditType.current
    val firstRow = items.indexOfFirst { it.isRow }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(items, key = { index, item -> itemKey(index, item) }) { index, item ->
            val rowMod = rowModifier.then(
                if (index == firstRow && firstRowRequester != null) Modifier.focusRequester(firstRowRequester) else Modifier
            )
            when (item) {
                is SearchItem.Header -> Text(
                    text = stringResource(item.text).uppercase(),
                    fontSize = type.sidebarSection,
                    fontWeight = FontWeight.SemiBold,
                    color = Colors.TextDim,
                    modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp),
                )

                is SearchItem.Message -> Text(
                    text = item.text,
                    fontSize = type.postBody,
                    color = Colors.TextDim,
                    modifier = Modifier.padding(start = 12.dp, top = 16.dp),
                )

                is SearchItem.Community -> SearchRow(
                    label = "r/${item.sub.name}",
                    detail = communityDetail(item.sub),
                    iconUrl = item.sub.iconUrl,
                    onClick = { onItem(item) },
                    modifier = rowMod,
                )

                is SearchItem.GoTo -> SearchRow(
                    label = stringResource(Res.string.search_go_to, item.name),
                    icon = Res.drawable.icon_forward,
                    onClick = { onItem(item) },
                    modifier = rowMod,
                )

                is SearchItem.RecentQuery -> SearchRow(
                    label = item.text,
                    icon = Res.drawable.icon_clock,
                    onClick = { onItem(item) },
                    modifier = rowMod,
                )

                SearchItem.ClearRecent -> SearchRow(
                    label = stringResource(Res.string.search_clear_recent),
                    icon = Res.drawable.icon_close,
                    dim = true,
                    onClick = { onItem(item) },
                    modifier = rowMod,
                )
            }
        }
    }
}

/** stable keys, so focus stays on a row while the list around it changes */
private fun itemKey(index: Int, item: SearchItem): Any = when (item) {
    is SearchItem.Community -> "sub:${item.sub.name.lowercase()}"
    is SearchItem.GoTo -> "goto"
    is SearchItem.RecentQuery -> "recent:${item.text.lowercase()}"
    SearchItem.ClearRecent -> "clear"
    is SearchItem.Header -> "header:${item.text.key}"
    is SearchItem.Message -> "message:$index"
}

/** "1.2m members · The final frontier" */
@Composable
private fun communityDetail(sub: Subreddit): String? {
    val members = if (sub.subscribers > 0) stringResource(Res.string.search_members, RedditFormat.count(sub.subscribers)) else null
    return listOfNotNull(members, sub.title.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { null }
}

/** a community, "Go to r/…" or "Clear recent searches": light when the remote is on it */
@Composable
private fun SearchRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    iconUrl: String? = null,
    icon: DrawableResource? = null,
    dim: Boolean = false,
) {
    val type = LocalRedditType.current
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFocused) Colors.CardFocused else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(Colors.Card),
            contentAlignment = Alignment.Center,
        ) {
            when {
                iconUrl != null -> AppImage(url = iconUrl, modifier = Modifier.fillMaxSize())
                icon != null -> AppIcon(res = icon, color = Colors.TextDim, modifier = Modifier.size(16.dp))
                else -> Text("r/", fontSize = type.tileMeta, fontWeight = FontWeight.Bold, color = Colors.Accent)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = type.sidebarItem,
                fontWeight = FontWeight.Medium,
                color = when {
                    isFocused -> Colors.TextOnFocused
                    dim -> Colors.TextDim
                    else -> Colors.Text
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    fontSize = type.tileMeta,
                    color = if (isFocused) Colors.TextDimOnFocused else Colors.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun loadRecents(): List<String> = JsonUtils.stringToList(SettingsManager.getString(AppPref.RECENT_SEARCHES))

private fun saveRecents(recents: List<String>) {
    SettingsManager.setString(AppPref.RECENT_SEARCHES, if (recents.isEmpty()) null else JsonUtils.toJson(recents))
}
