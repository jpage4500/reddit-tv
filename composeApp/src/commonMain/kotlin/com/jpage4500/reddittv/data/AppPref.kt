package com.jpage4500.reddittv.data

import org.jetbrains.compose.resources.StringResource
import reddittv.composeapp.generated.resources.*

/**
 * app level settings. persisted through [com.jpage4500.reddittv.util.SettingsManager], which reads the
 * declared type from [defaultValue] — so a pref with a Boolean default is a boolean pref, etc.
 */
enum class AppPref(val key: String, val defaultValue: Any? = null) {
    // -------------------------------------------------------------
    // -- string settings --
    // -------------------------------------------------------------
    WINDOW_RECT("window_rect"),                                         // desktop app size and position
    INSTALL_ID("install_id"),                                           // random UUID identifying this install to analytics
    ANALYTICS_SESSION("analytics_session"),                             // JSON of the session whose app_exit hasn't been reported yet
    ANALYTICS_PENDING("analytics_pending"),                             // JSON list of analytics rows a failed POST left behind
    UPDATE_VERSION("update_version"),                                   // newest version the last update check found, when newer (see UpdateManager)
    LAST_FEED("last_feed"),                                             // path of the feed last viewed, ie: "/r/space" ("" = home)
    RECENT_SUBS("recent_subs"),                                         // JSON list of recently opened subreddits, newest first
    RECENT_SEARCHES("recent_searches"),                                 // JSON list of what was typed on the search screen, newest first
    FLOATING_VIDEO_POS("floating_video_pos"),                           // "x,y": where the post's floating video was dragged to, 0..1 of the free space

    // -------------------------------------------------------------
    // -- enum settings (SettingsManager.getEnum) --
    // -------------------------------------------------------------
    FEED_SORT("feed_sort", FeedSort.HOT),                               // how feeds are sorted

    // -------------------------------------------------------------
    // -- boolean settings --
    // -------------------------------------------------------------
    DEBUG_MODE("debug_mode", true),                                     // verbose logging, all of it saved to the log file
    MINIMIZE_TO_TRAY("minimize_to_tray"),                               // desktop: close button hides to tray
    ANALYTICS_ENABLED("analytics_enabled", true),                       // opt out of the launch/exit count (see AnalyticsManager)
    AUTO_UPDATE_CHECK("auto_update_check", true),                       // check GitHub for a new version daily, in the background (see UpdateManager)
    AUTOPLAY_VIDEO("autoplay_video", true),                             // play a focused video tile (muted) and a post's video
    SHOW_NSFW("show_nsfw", false),                                      // show NSFW media instead of a blurred placeholder
    HIDE_PINNED("hide_pinned", true),                                   // leave pinned (stickied) posts out of feeds
    HIDE_NO_MEDIA("hide_no_media", false),                              // leave text posts and thumbnail-less links out of feeds
    SIGN_IN_SKIPPED("sign_in_skipped", false),                          // chose "Browse without signing in": start on the feed
    VIDEO_MUTED("video_muted", false),                                  // a post's video plays muted (RIGHT on it, or the full screen mute button)

    // -------------------------------------------------------------
    // -- int settings --
    // -------------------------------------------------------------
    TEXT_SCALE("text_scale", TEXT_SCALE_DEFAULT),                       // % size of titles, post text and comments (see TEXT_SCALES)
    GRID_COLUMNS("grid_columns", GRID_COLUMNS_DEFAULT),                 // columns in the TV / wide grid (see GRID_COLUMN_CHOICES)

    // -------------------------------------------------------------
    // -- long settings --
    // -------------------------------------------------------------
    LAST_UPDATE_CHECK("last_update_check"),                             // ms of last background app-update check
}

/** text size steps, in percent — the Aa control on a post and in settings */
const val TEXT_SCALE_DEFAULT = 100
val TEXT_SCALES: List<Int> = (70..200 step 10).toList()

/** columns in the grid (TV, or a wide window) — the phone's single-column list doesn't use it */
const val GRID_COLUMNS_DEFAULT = 4
val GRID_COLUMN_CHOICES: List<Int> = (2..4).toList()

/** feed sort order: the path segment reddit uses */
enum class FeedSort(val path: String, val labelRes: StringResource, val descRes: StringResource) {
    HOT("hot", Res.string.sort_hot, Res.string.sort_hot_desc),
    NEW("new", Res.string.sort_new, Res.string.sort_new_desc),
    TOP("top", Res.string.sort_top, Res.string.sort_top_desc),
    RISING("rising", Res.string.sort_rising, Res.string.sort_rising_desc),
}

const val MIN_TEXT_SIZE = 8 // hide text when set to this value
