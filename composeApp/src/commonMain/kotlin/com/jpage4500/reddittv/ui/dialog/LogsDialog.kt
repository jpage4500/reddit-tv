package com.jpage4500.reddittv.ui.dialog

import com.jpage4500.reddittv.ui.theme.DialogTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter.Companion.tint
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jpage4500.reddittv.data.Branding
import com.jpage4500.reddittv.manager.LogManager
import com.jpage4500.reddittv.ui.components.DialogTitle
import com.jpage4500.reddittv.ui.components.DropdownButton
import com.jpage4500.reddittv.ui.components.SearchField
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.util.*
import reddittv.composeapp.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.*

private val log = LoggerConfig.createLogger("LogsDialog")

/**
 * log severity levels for filtering
 */
enum class LogLevel(val displayName: String, val prefix: String, val color: Color) {
    VERBOSE("Verbose", "V", Colors.LOG_VERBOSE),
    DEBUG("Debug", "D", Colors.LOG_DEBUG),
    INFO("Info", "I", Colors.LOG_INFO),
    WARN("Warn", "W", Colors.LOG_WARN),
    ERROR("Error", "E", Colors.LOG_ERROR),
    ASSERT("Assert", "A", Colors.LOG_ASSERT);

    companion object {
        fun fromPrefix(prefix: String): LogLevel? {
            return entries.find { it.prefix == prefix }
        }
    }
}

enum class LogSource(val displayName: String) {
    MAIN(Branding.appName),
}

/**
 * data class for a parsed log line
 */
data class LogLine(
    val rawLine: String,
    val level: LogLevel,
    val displayText: String
)

/**
 * full-screen dialog that displays log file contents with color coding and filtering
 */
@Composable
fun LogsDialog(
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var logLines by remember { mutableStateOf<List<LogLine>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadResult by remember { mutableStateOf<Result<String>?>(null) }
    var selectedLogLine by remember { mutableStateOf<String?>(null) }
    var selectedLevel by remember { mutableStateOf(LogLevel.DEBUG) }
    var showLevelDropdown by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var debouncedSearchText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var selectedSource by remember { mutableStateOf(LogSource.MAIN) }
    var showSourceDropdown by remember { mutableStateOf(false) }

    // desktop hands the log to the OS default app, iOS previews it via QuickLook, and android
    // opens it through a FileProvider VIEW intent (text/plain). only web can't open a local file.
    val canOpenExternally = remember {
        PlatformUtils.getPlatform() != PlatformType.WEB
    }

    // debounce search text changes (500ms delay)
    LaunchedEffect(searchText) {
        kotlinx.coroutines.delay(500)
        debouncedSearchText = searchText
    }

    // load logs function
    fun loadLogs() {
        scope.launch {
            isLoading = true
            try {
                val content = withContext(Dispatchers.Default) {
                    // flush first: AppLogWriter batches lines, so the newest aren't on disk yet
                    flushLogFile()
                    StorageManager.readFile(PATH_LOGS, FILE_LOG)
                }

                if (content != null) {
                    val lines = withContext(Dispatchers.Default) {
                        parseLogLines(content)
                    }
                    logLines = lines
                    isLoading = false
                    log.d("loadLogs: loaded ${lines.size} log lines")

                    // scroll to bottom after loading and UI update
                    if (lines.isNotEmpty()) {
                        listState.scrollToItem(lines.size - 1)
                    }
                } else {
                    log.d("loadLogs: no log file found")
                    logLines = emptyList()
                    isLoading = false
                }
            } catch (e: Exception) {
                log.e("loadLogs: Exception: ${e.message}")
                logLines = emptyList()
                isLoading = false
            }
        }
    }

    // load logs on first composition
    LaunchedEffect(Unit) {
        loadLogs()
    }

    LaunchedEffect(selectedSource) { loadLogs() }

    // filter logs by selected level and higher, plus search text
    val filteredLogs = remember(logLines, selectedLevel, debouncedSearchText) {
        logLines.filter { line ->
            val levelMatch = line.level.ordinal >= selectedLevel.ordinal
            val searchMatch = if (debouncedSearchText.isBlank()) {
                true
            } else {
                line.rawLine.contains(debouncedSearchText, ignoreCase = true)
            }
            levelMatch && searchMatch
        }
    }

    // scroll to bottom when filter changes
    LaunchedEffect(filteredLogs.size, selectedLevel) {
        if (filteredLogs.isNotEmpty()) {
            listState.scrollToItem(filteredLogs.size - 1)
        }
    }

    // this one builds its own Dialog rather than going through BaseDialog, so it opts into the
    // same rule by hand: hidden while the log-line detail is up, and back when it closes.
    // registered before the detail so it sits under it on the stack, and the detail is composed
    // before the early return — skipping it would unregister it and flip the two back and forth
    val isTop = isTopDialog()

    // popup dialog for full log line
    if (selectedLogLine != null) {
        LogLineDetailDialog(
            logLine = selectedLogLine!!,
            onDismiss = { selectedLogLine = null }
        )
    }

    if (!isTop) return

    val properties = DialogProperties(true, true, false)
    Dialog(onDismissRequest = onDismiss, properties = properties) {
      DialogTheme {
        Card(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // title bar with refresh button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // not a stop for a remote — see DialogTitle
                        .focusProperties { canFocus = false }
                        .clickable(onClick = onDismiss)
                        .background(Colors.DIALOG_TITLE)
                        .padding(16.dp, 0.dp, 16.dp, 0.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // title
                    Text(
                        text = stringResource(Res.string.logs),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Colors.DialogTitleText
                    )

                    // push buttons to the right
                    Spacer(Modifier.weight(1f))

                    // refresh button
                    IconButton(onClick = { loadLogs() }) {
                        Image(
                            painter = painterResource(Res.drawable.icon_refresh),
                            contentDescription = stringResource(Res.string.refresh_logs),
                            modifier = Modifier.size(24.dp),
                            colorFilter = tint(MaterialTheme.colorScheme.primary)
                        )
                    }

                    // open logs in the OS default viewer (widget log has no file path, so MAIN only)
                    if (canOpenExternally && selectedSource == LogSource.MAIN) {
                        IconButton(onClick = {
                            scope.launch {
                                val path = withContext(Dispatchers.Default) {
                                    flushLogFile()
                                    StorageManager.getAbsolutePath(PATH_LOGS, FILE_LOG)
                                }
                                if (path == null) {
                                    ToastManager.showToast(Res.string.no_log_file, success = false)
                                } else {
                                    val isOk = withContext(Dispatchers.Default) { PlatformUtils.openUrl(path) }
                                    if (!isOk) ToastManager.showToast(Res.string.log_file_open_failed, success = false)
                                }
                            }
                        }) {
                            Image(
                                painter = painterResource(Res.drawable.icon_edit),
                                contentDescription = stringResource(Res.string.open_logs_external),
                                modifier = Modifier.size(24.dp),
                                colorFilter = tint(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }

                    // upload button
                    IconButton(
                        onClick = {
                            isUploading = true
                            LogManager.uploadLogs(
                                listener = { result ->
                                    isUploading = false
                                    uploadResult = result
                                }
                            )
                        },
                        enabled = !isUploading
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Image(
                                painter = painterResource(Res.drawable.icon_backup),
                                contentDescription = stringResource(Res.string.upload_logs),
                                modifier = Modifier.size(24.dp),
                                colorFilter = tint(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }

                    // close button
                    IconButton(onClick = onDismiss) {
                        Image(
                            painter = painterResource(Res.drawable.icon_close),
                            contentDescription = stringResource(Res.string.close),
                            modifier = Modifier.size(24.dp),
                            colorFilter = tint(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                // filter dropdown and search field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Level Filter Dropdown
                    DropdownButton(
                        //modifier = Modifier.weight(0.30f),
                        items = LogLevel.entries,
                        selectedItem = selectedLevel,
                        onItemSelected = { selectedLevel = it },
                        buttonText = { "${it.displayName}+" },
                        itemText = { it.displayName },
                        itemLeadingIcon = { level ->
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(level.color, shape = RoundedCornerShape(2.dp))
                            )
                        }
                    )

                    //Spacer(Modifier.weight(.20f))

                    // search field
                    SearchField(
                        query = searchText,
                        onQueryChange = { searchText = it },
                        modifier = Modifier.weight(.30f),
                        placeholder = stringResource(Res.string.search_hint),
                        textColor = Colors.PrimaryBlue,
                        iconColor = Colors.PrimaryBlue,
                    )
                }

                // upload message display
                val upload = uploadResult
                if (upload != null) {
                    val uploadOk = upload.isSuccess
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .background(
                                color = if (uploadOk)
                                    MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.2f)
                                else
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = if (uploadOk) stringResource(Res.string.logs_uploaded)
                            else stringResource(Res.string.error_message, upload.exceptionOrNull()?.message.orEmpty()),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uploadOk)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.fillMaxWidth())

                // log content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(48.dp)
                        )
                    } else if (filteredLogs.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.no_logs),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        SelectionContainer {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                items(filteredLogs.size) { index ->
                                    LogLineItem(
                                        logLine = filteredLogs[index],
                                        index = index,
                                        onClick = { selectedLogLine = filteredLogs[index].rawLine }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
      }
    }
}

/**
 * individual log line item with color coding and alternating background
 */
@Composable
private fun LogLineItem(
    logLine: LogLine,
    index: Int,
    onClick: () -> Unit
) {
    val backgroundColor = if (index % 2 == 0) {
        Color.White
    } else {
        Color(0xFFF5F5F5) // light grey
    }

    Text(
        text = logLine.displayText,
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 1.dp, horizontal = 4.dp),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        color = logLine.level.color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false
    )
}

/**
 * popup dialog showing full log line content
 */
@Composable
private fun LogLineDetailDialog(
    logLine: String,
    onDismiss: () -> Unit
) {
    // also its own Dialog — registers so the log list behind it steps out of the way
    if (!isTopDialog()) return

    val properties = DialogProperties(true, true, false)
    Dialog(onDismissRequest = onDismiss, properties = properties) {
      DialogTheme {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            // title
            DialogTitle(
                title = stringResource(Res.string.log_detail),
                icon = Res.drawable.icon_info,
                onDismiss = onDismiss
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {

                // full log content with wrapping
                SelectionContainer {
                    Text(
                        text = logLine,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        softWrap = true
                    )
                }

            }
        }
      }
    }
}

/**
 * parse log file content into individual log lines
 * log format: "MM-dd-hh:mm:ss [V/D/I/W/E/A] TT DW+<tag>: <message...>"
 */
private fun parseLogLines(content: String): List<LogLine> {
    val lines = content.lines().filter { it.isNotBlank() }
    return lines.map { line ->
        // parse log level from format: mm:ss [L] ...
        val levelMatch = Regex("""\[([VDIWEA])]""").find(line)
        val levelPrefix = levelMatch?.groupValues?.get(1) ?: "D"
        val level = LogLevel.fromPrefix(levelPrefix) ?: LogLevel.DEBUG

        // remove the "DW+<tag>: " prefix (see LOG_TAG_PREFIX) from display text
        // remove "MM-dd-" date prefix from display text
        val displayText = line
            .replace(Regex("""${Regex.escape(LOG_TAG_PREFIX)}[^:]*:\s*"""), "")
            .replace(Regex("""^\d{2}-\d{2}-"""), "")

        LogLine(
            rawLine = line,
            level = level,
            displayText = displayText
        )
    }
}
