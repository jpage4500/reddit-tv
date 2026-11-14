package com.jpage4500.reddittv.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.jpage4500.reddittv.data.Branding

class Colors {
    companion object {
        val DIALOG_TITLE = Color(0xFFEFEFEF)
        val DialogTitleText = Color(0xFF5b6abf)  // dialog title, its icon and the close (x): brand blue, readable on DIALOG_TITLE
        val DialogButton = Color.Gray            // a dialog button that isn't focused

        // -- RedditTV: dark theme used by every screen (see docs/Design.md) --
        val Background = Color(0xFF0B0C0E)
        val Card = Color(0xFF17191D)
        val CardFocused = Color(0xFFF4F5F7)
        val CardBorder = Color(0xFF26292F)
        val Sidebar = Color(0xFF111316)
        val Scrim = Color(0xB3000000)           // black, 70%: behind the open sidebar
        val Accent = Color(0xFFFFB84D)          // upvote / selected / focus accent
        val Downvote = Color(0xFF8AB4FF)
        val Link = Color(0xFF8AB4FF)            // a link in a post's text or a comment
        val VideoProgress = Color(0xFFFF5A36)
        val Text = Color(0xFFF4F5F7)
        val TextDim = Color(0xFFAEB4BF)
        val TextOnFocused = Color(0xFF0B0C0E)   // text on a focused (light) card
        val TextDimOnFocused = Color(0xFF4A505B)
        val FocusRing = Color(0xFFFFFFFF)
        val MediaBackground = Color(0xFF000000)
        val CommentRail = Color(0xFF2C3037)
        val SubredditIcon = Color(0xFF3D6AE0)   // behind "r/" for a community with no icon of its own
        val SubredditAvatar = Color(0xFF5A4632)  // the letter circle beside a post's title
        val Divider = Color(0xFF22252B)          // above a post's comments
        val MediaBadge = Color(0xE00B0C0E)       // Playing / Paused over a video
        val Nsfw = Color(0xFFFF4D6A)
        val MetaPill = Color(0x1FFFFFFF)         // a post's vote / comment pill on a dark card
        val MetaPillOnFocused = Color(0x14000000) // ... and on a focused (light) one

        // primary brand colors
        val PrimaryBlue = Color(0xFF5b6abf)  // primary brand blue (see Branding.brandColor)
        val LoginBlue = Color(0xFF5568C1)  // slightly deeper blue, for accents on the brand blue
        val PrimaryGray = Color(0xFF78909b)  // neutral gray

        val Success = Color(0xFF4CAF50)
        val Error = Color(0xFFFF5252)
        val Warning = Color(0xFFFF9800)

        // accent colors
        val AccentRed = Color(0xFFFF5252)

        // UI colors
        val DarkGray = Color(0xFF909191)
        // the unselected part of a choice setting's track (SettingChoiceDialog)
        val ChoiceTrack = Color(0xFFBCBCBC)
        // the ring round its selected stop while a remote has it focused
        val ChoiceFocus = Color(0x665b6abf)

        // media found on a shared page (MainScreen)
        val ThumbnailBackground = Color(0xFFE0E0E0)
        val PreviewBackground = Color(0xFF202020)
        // the desktop preview's controls, over the video
        val PreviewControls = Color(0xFFFFFFFF)
        val PreviewControlsBackground = Color(0x99000000)
        // the Recent pill over a full screen preview: light on the video, like the player's own buttons
        val RecentPill = Color(0x40FFFFFF)
        // the dark badge over a thumbnail (a video's length, the preview hint)
        val ThumbnailOverlay = Color(0x99000000)

        // the home screen's section bands (MainScreen): Ready to Cast on navy over a blue tint,
        // then green for the TVs that can play, gray for the rest and amber for Recent
        val TextPrimary = Color(0xFF161A2E)
        val TextSecondary = Color(0xFF4E5570)
        val ReadyBand = Color(0xFF1B2A7A)
        val ReadyBandText = Color(0xFFC9D0F2)
        val ReadyBackground = Color(0xFFEEF0FB)
        val DevicesBand = Color(0xFFE4F4EA)
        val DevicesBandBorder = Color(0xFFC8E7D4)
        val DevicesBandText = Color(0xFF17603A)
        val DevicesBandDot = Color(0xFF23A15E)
        val OtherBand = Color(0xFFF1F2F5)
        val OtherBandBorder = Color(0xFFE1E3EA)
        val OtherBandText = Color(0xFF4E5570)
        val OtherBandDot = Color(0xFF9DA3B4)
        val RecentBand = Color(0xFFFCEFDB)
        val RecentBandBorder = Color(0xFFF3D9B1)
        val RecentBandText = Color(0xFF7A4600)
        // Playlists gets violet, the one hue the other bands leave free
        val PlaylistsBand = Color(0xFFECE9FB)
        val PlaylistsBandBorder = Color(0xFFD6D0F3)
        val PlaylistsBandText = Color(0xFF3E2F8F)
        // the playlist screen (PlaylistScreen): a channel's logo tile, its filter chips, what's on
        val ChannelTile = Color(0xFFF1F2F5)
        val ChannelTileBorder = Color(0xFFE1E3EA)
        val ChannelRowShade = Color(0xFFF8F9FB)   // every other channel row: lighter than a logo's tile
        val ChipBorder = Color(0xFFC9CDD9)
        val ChipSelected = Color(0xFF1B2A7A)
        val OnNow = Color(0xFFDDF3E6)
        val OnNowBorder = Color(0xFF23A15E)
        val OnNowText = Color(0xFF17603A)
        val ProgressTrack = Color(0xFFD5D9E6)
        val FavoriteStar = Color(0xFFE0A100)
        val SearchHighlight = Color(0xFFFFE08A)
        val CheckOutline = Color(0xFFC9CDD9)    // a found video's empty check circle

        // Getting Started (GettingStartedDialog) and the phone's drawer
        val GuideArtBackground = Color(0xFFEEF0FB)
        val GuideArtScreen = Color(0xFFF3F4F9)
        val GuideArtVideo = Color(0xFF2B3A4E)
        val GuideArtDim = Color(0xFF5A6072)     // what's behind the share sheet
        val GuideArtBar = Color(0xFFD5D9E6)     // a line of text, drawn
        val GuideArtBarLight = Color(0xFFE6E8F0)
        val GuideDotIdle = Color(0xFFC9CDD9)
        val GuideTagInstall = Color(0xFFE3E7FA)
        val GuideTagInstallText = Color(0xFF3E4C9E)
        val GuideTagSideload = Color(0xFFFCEBD2)
        val GuideTagSideloadText = Color(0xFF7A4600)
        val GuideTagReady = Color(0xFFDDF3E6)
        val GuideTagReadyText = Color(0xFF1F7A4A)
        val DrawerSectionText = Color(0xFF5A6072)

        // the receiver's idle screen (ReceiverScreen): the logo's navy and yellow
        val TvBackground = Color(0xFF0A2340)
        val TvLogoDisc = Color(0xFF0E2E54)
        val TvAccent = Color(0xFFFFC531)
        val TvRing = Color(0xFFFFC531)          // drawn at 5-22% round the logo
        val TvTextSecondary = Color(0xFFB9C9DC)
        val TvTextMuted = Color(0xFF8FA6C0)
        val TvCardTitle = Color(0xFFD3DEEA)
        val TvCardBackground = Color(0xFF16365C)
        val TvCardPressed = Color(0x33FFFFFF)   // white, 20%: a Recent card while OK is held
        val TvStepBackground = Color(0x0FFFFFFF)  // white, 6%
        val TvListening = Color(0xFF5BD18B)
        val TvErrorBackground = Color(0xFF3A1E2A)
        val TvErrorBorder = Color(0xFF7A3446)
        val TvErrorIcon = Color(0xFFFF8A80)
        val TvErrorTitle = Color(0xFFFFE3E0)
        val TvErrorText = Color(0xFFF2B8B2)

        // the receiver's side menu (ReceiverScreen): a panel a shade off the screen's navy
        val TvMenuBackground = Color(0xFF0E2E54)
        val TvMenuScrim = Color(0x66000000)     // black, 40%

        // the receiver's player controls (PlayerOverlay), over the video
        val PlayerScrim = Color(0xD9000000)     // black, 85%: where the shade under the seek bar ends
        val PlayerFlashDisc = Color(0x99000000) // black, 60%: behind the play / pause icon
        val PlayerTrack = Color(0x4DFFFFFF)     // white, 30%
        val PlayerBuffered = Color(0x80FFFFFF)  // white, 50%
        // what the remote is on (FocusIndication, DialogButtons)
        val FocusHighlight = Color(0x405b6abf)  // brand blue, 25%
        val FocusBorder = Color(0xFF1B2A7A)     // deep navy
        val FocusOutline = PrimaryBlue          // round a focused setting row, and a dialog's X (focusBorder)

        // a TV's apps with no icon of their own (TvAppIcon): each service's brand color
        val BadgePrimeVideo = Color(0xFF00A8E1)
        val BadgeDisneyPlus = Color(0xFF113CCF)
        val BadgeMax = Color(0xFF002BE7)
        val BadgeHulu = Color(0xFF1CE783)
        val BadgePeacock = Color(0xFF000000)
        val BadgeParamountPlus = Color(0xFF0064FF)
        val BadgeAppleTv = Color(0xFF000000)
        val BadgePlex = Color(0xFFE5A00D)
        val BadgeTwitch = Color(0xFF9146FF)
        val BadgeSpotify = Color(0xFF1DB954)
        val BadgeTubi = Color(0xFF7408FF)
        val BadgePlutoTv = Color(0xFFFFF200)
        val BadgeCrunchyroll = Color(0xFFF47521)
        val BadgeTextLight = Color(0xFFFFFFFF)
        val BadgeTextDark = Color(0xFF000000)

        // log level colors
        val LOG_VERBOSE = Color(0xFF5C5C5C)     // gray
        val LOG_DEBUG = Color(0xFF000000)       // black
        val LOG_INFO = Color(0xFF2196F3)        // blue
        val LOG_WARN = Color(0xFFFF9800)        // orange
        val LOG_ERROR = Color(0xFFFF5252)       // red
        val LOG_ASSERT = Color(0xFFE91E63)      // pink
    }
}

/**
 * dark color scheme for the app
 * customize these colors to match your app's design
 */
val DarkColorScheme = darkColorScheme(
    primary = Branding.brandColor,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3E4C8A),
    onPrimaryContainer = Color(0xFFE0E3FF),

    secondary = Color(0xFF5D6B8A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF414E6E),
    onSecondaryContainer = Color(0xFFD9E3FF),

    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF5C3A47),
    onTertiaryContainer = Color(0xFFFFD9E3),

    error = Colors.AccentRed,
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),

    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC5C6D0),

    outline = Color(0xFF8F9099),
    outlineVariant = Color(0xFF44464F),

    inverseSurface = Color(0xFFE2E2E6),
    inverseOnSurface = Color(0xFF2F3033),
    inversePrimary = Color(0xFF3E5099)
)

/**
 * light color scheme for the app
 */
val LightColorScheme = lightColorScheme(
    primary = Branding.brandColor,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001452),

    secondary = Color(0xFF5D6B8A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1EBFF),
    onSecondaryContainer = Color(0xFF1A2844),

    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E3),
    onTertiaryContainer = Color(0xFF31111D),

    error = Colors.AccentRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFFEFBFF),
    onBackground = Color(0xFF1A1C1E),

    surface = Color(0xFFFEFBFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF44464F),

    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),

    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),
    inversePrimary = Color(0xFFB8C3FF)
)

/** RedditTV's dark scheme — the material components (dialogs, switches) pick it up */
val RedditColorScheme = darkColorScheme(
    primary = Colors.Accent,
    onPrimary = Colors.TextOnFocused,
    background = Colors.Background,
    onBackground = Colors.Text,
    surface = Colors.Card,
    onSurface = Colors.Text,
    surfaceVariant = Colors.CardBorder,
    onSurfaceVariant = Colors.TextDim,
    error = Colors.AccentRed,
)

/**
 * dialogs: Share to TV's light look — a white body under the light title bar — with the brand blue
 * as primary, so a dialog's icons, switches and focused button are blue (yellow reads badly on white)
 */
val DialogColorScheme = LightColorScheme.copy(primary = Colors.PrimaryBlue, onPrimary = Color.White)
