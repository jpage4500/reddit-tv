package com.jpage4500.reddittv.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jpage4500.reddittv.manager.UpdateManager
import com.jpage4500.reddittv.ui.dialog.common.ConfirmationDialog
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.PlatformType
import com.jpage4500.reddittv.util.PlatformUtils
import reddittv.composeapp.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.*

private val log = LoggerConfig.createLogger("CheckUpdatesDialog")

private sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object RequestingPermission : UpdateUiState()
    data class Downloading(val downloaded: Long, val total: Long) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

@Composable
fun CheckUpdatesDialog(
    onDismiss: () -> Unit,
) {
    var result by remember { mutableStateOf<UpdateManager.UpdateCheckResult?>(null) }

    LaunchedEffect(Unit) {
        val r = UpdateManager.checkForUpdates()
        log.d("result=$r")
        result = r
    }

    when (val r = result) {
        null -> {
            BaseDialog(
                titleRes = Res.string.checking_for_updates,
                icon = Res.drawable.icon_update,
                onDismiss = onDismiss,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        is UpdateManager.UpdateCheckResult.UpToDate -> {
            ConfirmationDialog(
                title = stringResource(Res.string.no_update_available),
                message = stringResource(Res.string.up_to_date, r.currentVersion),
                cancelText = null,
                onDismiss = onDismiss,
            )
        }

        is UpdateManager.UpdateCheckResult.Available -> {
            AvailableUpdateDialog(available = r, onDismiss = onDismiss)
        }

        is UpdateManager.UpdateCheckResult.StoreManaged -> {
            val msgRes = when (r.store) {
                UpdateManager.Store.PLAY_STORE -> Res.string.update_via_play_store
                UpdateManager.Store.APP_STORE -> Res.string.update_via_app_store
            }
            val hasUrl = r.storeUrl.isNotBlank()
            ConfirmationDialog(
                title = stringResource(Res.string.check_for_updates),
                message = stringResource(msgRes),
                okText = if (hasUrl) stringResource(Res.string.open) else stringResource(Res.string.ok),
                cancelText = if (hasUrl) stringResource(Res.string.close) else null,
                onDismiss = onDismiss,
                onConfirm = if (hasUrl) {
                    { PlatformUtils.openUrl(r.storeUrl) }
                } else {
                    null
                },
            )
        }

        is UpdateManager.UpdateCheckResult.Error -> {
            ConfirmationDialog(
                title = stringResource(Res.string.update_check_failed),
                message = r.message,
                cancelText = null,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun AvailableUpdateDialog(
    available: UpdateManager.UpdateCheckResult.Available,
    onDismiss: () -> Unit,
) {
    val canSideloadInstall = available.apkUrl != null
        && PlatformUtils.getPlatform() == PlatformType.ANDROID
        && !PlatformUtils.isPlayStoreInstall()

    // desktop has no in-app installer — link out to the GitHub release instead. jDeploy
    // installs auto-update on next launch; a manual/dev install can grab the build there
    val canOpenReleasePage = !canSideloadInstall
        && PlatformUtils.getPlatform() == PlatformType.DESKTOP
        && available.releaseUrl.isNotBlank()

    var uiState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    val scope = rememberCoroutineScope()
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun startDownload() {
        val url = available.apkUrl ?: return
        uiState = UpdateUiState.Downloading(0L, available.apkSizeBytes ?: -1L)
        downloadJob = scope.launch {
            try {
                val path = PlatformUtils.downloadFile(url, available.apkSizeBytes) { d, t ->
                    uiState = UpdateUiState.Downloading(d, t)
                }
                if (path != null) {
                    PlatformUtils.installApk(path)
                    onDismiss()
                } else {
                    uiState = UpdateUiState.Error(getString(Res.string.update_download_failed))
                }
            } catch (_: CancellationException) {
                // dialog dismissed mid-download; nothing to do
            } catch (e: Exception) {
                log.e("startDownload: ${e.message}")
                uiState = UpdateUiState.Error(e.message ?: getString(Res.string.update_download_failed))
            }
        }
    }

    fun onUpdateClicked() {
        if (!PlatformUtils.canInstallApk()) {
            uiState = UpdateUiState.RequestingPermission
            PlatformUtils.requestInstallPermission()
        } else {
            startDownload()
        }
    }

    // auto-resume after returning from the "Install unknown apps" settings page
    DisposableEffect(uiState) {
        if (uiState !is UpdateUiState.RequestingPermission) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && PlatformUtils.canInstallApk()) {
                    startDownload()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    when (val ui = uiState) {
        UpdateUiState.Idle -> {
            val okText = when {
                canSideloadInstall -> stringResource(Res.string.update_button)
                canOpenReleasePage -> stringResource(Res.string.update_download)
                else -> stringResource(Res.string.close)
            }
            val cancelText = if (canSideloadInstall || canOpenReleasePage) {
                stringResource(Res.string.close)
            } else {
                null
            }
            BaseDialog(
                titleRes = Res.string.type_update,
                icon = Res.drawable.icon_update,
                okText = okText,
                cancelText = cancelText,
                onDismiss = onDismiss,
                onConfirm = when {
                    canSideloadInstall -> ({ onUpdateClicked() })
                    canOpenReleasePage -> ({ PlatformUtils.openUrl(available.releaseUrl); Unit })
                    else -> null
                },
                // android sideload keeps the dialog open to show download progress; the
                // desktop "Download" hand-off to the browser dismisses like a plain close
                dismissOnConfirm = !canSideloadInstall,
            ) {
                AvailableBody(available = available)
            }
        }

        UpdateUiState.RequestingPermission -> {
            BaseDialog(
                titleRes = Res.string.type_update,
                icon = Res.drawable.icon_update,
                okText = stringResource(Res.string.open_settings),
                cancelText = stringResource(Res.string.cancel),
                onDismiss = onDismiss,
                onConfirm = { PlatformUtils.requestInstallPermission() },
                dismissOnConfirm = false,
            ) {
                Text(
                    text = stringResource(Res.string.update_install_permission_required),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        is UpdateUiState.Downloading -> {
            BaseDialog(
                titleRes = Res.string.type_update,
                icon = Res.drawable.icon_update,
                cancelText = stringResource(Res.string.cancel),
                onDismiss = {
                    downloadJob?.cancel()
                    onDismiss()
                },
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = stringResource(Res.string.update_downloading),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (ui.total > 0L) {
                        val progress = (ui.downloaded.toFloat() / ui.total.toFloat())
                            .coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                Res.string.update_download_progress,
                                formatBytes(ui.downloaded),
                                formatBytes(ui.total),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        is UpdateUiState.Error -> {
            BaseDialog(
                titleRes = Res.string.update_download_failed,
                icon = Res.drawable.icon_info,
                okText = stringResource(Res.string.update_retry),
                cancelText = stringResource(Res.string.close),
                onDismiss = onDismiss,
                onConfirm = {
                    uiState = UpdateUiState.Idle
                    onUpdateClicked()
                },
                dismissOnConfirm = false,
            ) {
                Text(
                    text = ui.message,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AvailableBody(available: UpdateManager.UpdateCheckResult.Available) {
    Text(
        text = stringResource(
            Res.string.update_available_msg,
            available.newVersion,
            available.currentVersion,
        ),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
    if (available.notes.isNotBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = available.notes,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        "${(mb * 10).roundToInt() / 10.0} MB"
    } else {
        val kb = bytes.toDouble() / 1024.0
        "${(kb * 10).roundToInt() / 10.0} KB"
    }
}
