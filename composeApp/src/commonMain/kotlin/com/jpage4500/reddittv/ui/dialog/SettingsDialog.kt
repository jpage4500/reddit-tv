package com.jpage4500.reddittv.ui.dialog

import androidx.compose.runtime.*
import com.jpage4500.reddittv.data.*
import com.jpage4500.reddittv.ui.setting.SettingChoiceDialog
import com.jpage4500.reddittv.ui.setting.SettingItem
import com.jpage4500.reddittv.ui.setting.SettingSwitchItem
import com.jpage4500.reddittv.util.SettingsManager
import kotlin.math.abs
import reddittv.composeapp.generated.resources.*
import org.jetbrains.compose.resources.*

/**
 * text size, sort, autoplay, NSFW, hiding pinned / media-less posts and About. from the menu (and desktop's
 * File > Settings)
 */
@Composable
fun SettingsDialog(
    showGridSize: Boolean,
    onDismiss: () -> Unit,
) {
    val textScale by SettingsManager.getIntAsState(AppPref.TEXT_SCALE)
    val gridColumns by SettingsManager.getIntAsState(AppPref.GRID_COLUMNS)
    val sort by SettingsManager.getEnumAsState<FeedSort>(AppPref.FEED_SORT)
    val autoplay by SettingsManager.getBoolAsState(AppPref.AUTOPLAY_VIDEO)
    val showNsfw by SettingsManager.getBoolAsState(AppPref.SHOW_NSFW)
    val hidePinned by SettingsManager.getBoolAsState(AppPref.HIDE_PINNED)
    val hideNoMedia by SettingsManager.getBoolAsState(AppPref.HIDE_NO_MEDIA)
    var showAbout by remember { mutableStateOf(false) }
    // out here with the dialogs it opens, not in the content below: that leaves the composition
    // while another dialog is on top of this one
    var choosing by remember { mutableStateOf<SettingsChoice?>(null) }

    BaseDialog(
        titleRes = Res.string.settings,
        icon = Res.drawable.icon_settings,
        onDismiss = onDismiss,
    ) {
        // each choice row shows what it's set to, and opens a slider to change it
        SettingItem(
            icon = Res.drawable.icon_edit,
            labelRes = Res.string.text_size,
            valueText = stringResource(Res.string.text_size_percent, textScale),
            onClick = { choosing = SettingsChoice.TEXT_SIZE },
        )
        SettingItem(
            icon = Res.drawable.icon_refresh,
            labelRes = Res.string.sort_feeds_by,
            valueText = stringResource(sort.labelRes),
            onClick = { choosing = SettingsChoice.SORT },
        )
        // the grid's columns: not offered with the phone's single-column list
        if (showGridSize) {
            SettingItem(
                icon = Res.drawable.icon_tv,
                labelRes = Res.string.grid_size,
                valueText = gridColumns.toString(),
                onClick = { choosing = SettingsChoice.GRID_SIZE },
            )
        }
        SettingSwitchItem(
            icon = Res.drawable.icon_play,
            labelText = stringResource(Res.string.autoplay_videos),
            checked = autoplay,
            onCheckedChange = { SettingsManager.setBoolean(AppPref.AUTOPLAY_VIDEO, it) },
        )
        SettingSwitchItem(
            icon = Res.drawable.icon_info,
            labelText = stringResource(Res.string.show_nsfw),
            descText = stringResource(Res.string.show_nsfw_desc),
            checked = showNsfw,
            onCheckedChange = { SettingsManager.setBoolean(AppPref.SHOW_NSFW, it) },
        )
        SettingSwitchItem(
            icon = Res.drawable.icon_info,
            labelText = stringResource(Res.string.hide_pinned),
            descText = stringResource(Res.string.hide_pinned_desc),
            checked = hidePinned,
            onCheckedChange = { SettingsManager.setBoolean(AppPref.HIDE_PINNED, it) },
        )
        SettingSwitchItem(
            icon = Res.drawable.icon_info,
            labelText = stringResource(Res.string.hide_no_media),
            descText = stringResource(Res.string.hide_no_media_desc),
            checked = hideNoMedia,
            onCheckedChange = { SettingsManager.setBoolean(AppPref.HIDE_NO_MEDIA, it) },
        )
        SettingItem(
            icon = Res.drawable.icon_info,
            labelRes = Res.string.about,
            showDivider = false,
            onClick = { showAbout = true },
        )
    }

    when (choosing) {
        SettingsChoice.TEXT_SIZE -> SettingChoiceDialog(
            icon = Res.drawable.icon_edit,
            labelRes = Res.string.text_size,
            // too many stops to name each one: the selected one's percent shows under the slider
            choices = TEXT_SCALES.map { "" },
            descriptions = TEXT_SCALES.map {
                stringResource(if (it == TEXT_SCALE_DEFAULT) Res.string.text_size_percent_default else Res.string.text_size_percent, it)
            },
            infoText = stringResource(Res.string.text_size_info),
            // a saved value that's no longer a stop lands on the nearest one
            selectedIndex = TEXT_SCALES.indices.minBy { abs(TEXT_SCALES[it] - textScale) },
            onSelect = { SettingsManager.setInt(AppPref.TEXT_SCALE, TEXT_SCALES[it]) },
            onDismiss = { choosing = null },
        )
        SettingsChoice.SORT -> SettingChoiceDialog(
            icon = Res.drawable.icon_refresh,
            labelRes = Res.string.sort_feeds_by,
            choices = FeedSort.entries.map { stringResource(it.labelRes) },
            descriptions = FeedSort.entries.map { stringResource(it.descRes) },
            infoText = stringResource(Res.string.sort_feeds_info),
            selectedIndex = sort.ordinal,
            onSelect = { SettingsManager.setEnum(AppPref.FEED_SORT, FeedSort.entries[it]) },
            onDismiss = { choosing = null },
        )
        SettingsChoice.GRID_SIZE -> SettingChoiceDialog(
            icon = Res.drawable.icon_tv,
            labelRes = Res.string.grid_size,
            choices = GRID_COLUMN_CHOICES.map { it.toString() },
            descriptions = GRID_COLUMN_CHOICES.map {
                stringResource(if (it == GRID_COLUMNS_DEFAULT) Res.string.grid_size_columns_default else Res.string.grid_size_columns, it)
            },
            infoText = stringResource(Res.string.grid_size_info),
            selectedIndex = GRID_COLUMN_CHOICES.indexOf(gridColumns).coerceAtLeast(0),
            onSelect = { SettingsManager.setInt(AppPref.GRID_COLUMNS, GRID_COLUMN_CHOICES[it]) },
            onDismiss = { choosing = null },
        )
        null -> {}
    }
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}

// the settings that open a choice slider
private enum class SettingsChoice { TEXT_SIZE, SORT, GRID_SIZE }
