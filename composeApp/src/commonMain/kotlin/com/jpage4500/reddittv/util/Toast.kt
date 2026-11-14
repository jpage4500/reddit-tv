package com.jpage4500.reddittv.util

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.ui.components.AppIcon
import com.jpage4500.reddittv.ui.theme.Colors
import reddittv.composeapp.generated.resources.Res
import reddittv.composeapp.generated.resources.icon_checkmark
import reddittv.composeapp.generated.resources.icon_info
import reddittv.composeapp.generated.resources.icon_x
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import reddittv.composeapp.generated.resources.*

private val log = LoggerConfig.createLogger("ToastManager")

enum class ToastType { INFO, SUCCESS, ERROR }

data class ToastData(
    val message: String,
    val type: ToastType,
    val actionLabel: String? = null,
    val actionCallback: (() -> Unit)? = null,
)

object ToastManager {
    private const val DURATION_MS = 3000L

    // long enough to read the message and reach its button
    private const val ACTION_DURATION_MS = 8000L

    private val _current = MutableStateFlow<ToastData?>(null)
    val current: StateFlow<ToastData?> = _current.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dismissJob: Job? = null

    fun showToast(
        message: String,
        success: Boolean? = null,
        actionLabel: String? = null,
        actionCallback: (() -> Unit)? = null,
    ) {
        val type = when (success) {
            true -> ToastType.SUCCESS
            false -> ToastType.ERROR
            null -> ToastType.INFO
        }
        val data = ToastData(message, type, actionLabel, actionCallback)
        log.d("showToast: $type - $message")
        dismissJob?.cancel()
        _current.value = data
        dismissJob = scope.launch {
            delay(if (actionLabel != null) ACTION_DURATION_MS else DURATION_MS)
            _current.value = null
        }
    }

    // for code outside compose: resolves [res] (formatted with [args]) and shows it
    fun showToast(res: StringResource, vararg args: Any, success: Boolean? = null) {
        scope.launch { showToast(getString(res, *args), success = success) }
    }

    fun dismiss() {
        dismissJob?.cancel()
        _current.value = null
    }
}

/**
 * single toast host — place once at the app root. on Android the overlay is a
 * non-blocking Dialog so it floats above any user-opened Dialog without needing a
 * per-dialog wrapper; on iOS/Desktop it's a Popup.
 */
@Composable
fun ToastHost() {
    val toast by ToastManager.current.collectAsState()
    val data = toast ?: return
    ToastOverlayContainer(onDismiss = { ToastManager.dismiss() }) {
        // key on data so swipe state resets when a new toast replaces the old one
        key(data) {
            val dismissState = rememberSwipeToDismissBoxState()
            LaunchedEffect(dismissState) {
                snapshotFlow { dismissState.currentValue }.collect { value ->
                    if (value != SwipeToDismissBoxValue.Settled) ToastManager.dismiss()
                }
            }
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {},
                content = { ToastCard(data) },
            )
        }
    }
}

@Composable
expect fun ToastOverlayContainer(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
)

@Composable
private fun ToastCard(data: ToastData) {
    val bg = when (data.type) {
        ToastType.INFO -> Colors.PrimaryBlue
        ToastType.SUCCESS -> Colors.Success
        ToastType.ERROR -> Colors.Error
    }
    Card(
        modifier = Modifier
            .widthIn(max = 400.dp)
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToastIcon(data.type)
            Text(
                text = data.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (data.actionLabel != null && data.actionCallback != null) {
                TextButton(onClick = {
                    data.actionCallback.invoke()
                    ToastManager.dismiss()
                }) {
                    Text(text = data.actionLabel, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ToastIcon(type: ToastType) {
    val iconRes = when (type) {
        ToastType.INFO -> Res.drawable.icon_info
        ToastType.SUCCESS -> Res.drawable.icon_checkmark
        ToastType.ERROR -> Res.drawable.icon_x
    }
    AppIcon(
        res = iconRes,
        modifier = Modifier.size(24.dp),
        color = Color.White,
    )
}
