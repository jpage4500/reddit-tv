# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**RedditTV** — a Kotlin Multiplatform Compose app for browsing Reddit from a couch: a 4-column,
remote-friendly grid on Android TV, and a simple list on phones. No API key and no OAuth app: the
user signs in on Reddit's own login page in the app's browser, and the app reads the `.json` view of
reddit pages with that browser's cookies. Targets Android (phones **and** Android TV / Fire OS, one
APK), iOS, and Desktop (macOS/Windows/Linux via jDeploy). Distributed on **GitHub only** — no Play
Store or App Store listing.

This repo was created from **CastToTVKMP** (Share to TV+): the shared platform layer, logging/crash
pipeline, HTTP + JSON stack, settings, dialogs, the sign-in browser and common UI components came
across; everything cast-specific was removed. See **`docs/Ported From Share to TV.md`** for what
came across and what's still outstanding.

**Read `docs/How It Works.md` before touching sign-in or the Reddit client**, and
**`docs/design/`** for the UI (the canvas boards the screens are built from).

## Build & Run

The app's build config pulls the shared `LOGGING_URL` / `LOGGING_API` from env vars or
`~/.gradle/gradle.properties`. Missing keys resolve to `""` rather than failing the build, so a fresh
clone compiles with none set.

| Task | Command |
|---|---|
| Compile common/Android | `./gradlew :androidApp:compileFullDebugKotlin` |
| Compile desktop | `./gradlew :composeApp:compileKotlinDesktop` |
| Compile iOS | `./gradlew :composeApp:compileKotlinIosSimulatorArm64` |
| Build & install Android (beta flavor) on connected devices (asks for another after each, until `x`) | `scripts/build-android.sh` |
| Build the full-flavor APK and publish a GitHub Release | `scripts/build-android.sh publish-github` |
| Build the desktop jar and publish it via jDeploy to this repo's Releases | `scripts/build-desktop.sh` |
| Run desktop app | `./gradlew :composeApp:run` (or `scripts/run-desktop.sh`) |
| Run on the booted iOS simulator | `scripts/run-ios.sh` |
| Screenshot the booted iOS simulator | `scripts/screenshot-ios.sh` |
| Fix iOS runs from IntelliJ after an Xcode update | `scripts/fix-ios-intellij.sh` |
| Make booted iOS simulators trust the VPN's TLS proxy | `scripts/fix-ios-simulator.sh` |
| Regenerate all launcher icons (+ TV banner) | `scripts/generate-icons.sh` |
| Push all CI secrets to GitHub | `scripts/set-github-secrets.sh` (`--dry-run` to preview) |
| Print version (git commit count) | `./gradlew -q printVersion` |

There is **no test suite** — do not add "run tests" to instructions or block work on it.

Version is `1.0.<git rev-list --count HEAD>`, computed in both build files.

## Architecture

### KMP module layout

`expect`/`actual` with `-Xexpect-actual-classes` enabled — most platform differences live as
`expect object` in commonMain with one file per target.

### Top-level packages in `commonMain`

- `data/` — `RedditModels.kt` (`Post`, `PostMedia`, `Comment`, `Feed`, `Subreddit`, `RedditUser`),
  `AppPref` (+ `TextSize`, `FeedSort`), `SignInSite`, `Branding`.
- `manager/`
  - `RedditClient` — every reddit request: feeds, a post + comments, subreddits, `/api/me`, vote,
    save. Sends `WebSession`'s cookies and user agent; never a Ktor cookie jar.
  - `RedditParser` — the `.json` responses → models, via `JsonElement` (not `@Serializable`), so
    one odd field drops one post, not a page.
  - `PhoneSignInServer` — the TV's LAN sign-in page (ktor-server-cio) behind the QR code, and
    `loginAutofillScript()` that types what the phone sent into reddit's login page.
  - Carried over: `NetworkManager`, `LogManager`, `AnalyticsManager`, `HttpLog`, `UpdateManager`,
    `HttpClientFactory`, `AppLifecycle`, plus `AppEvents` (desktop menu → app).
- `ui/RootScreen.kt` — sign in → feed → post. Holds the feed's state (pages, scroll, focused tile)
  so it's intact coming back from a post.
- `ui/screen/` — `SignInScreen`, `FeedScreen` (`TvFeedScreen` grid + `PhoneFeedScreen` list),
  `PostScreen`, `FeedState` (paging).
- `ui/components/` — `PostTile` (TV), `PostCard` (phone), `PostMediaView`, `Sidebar`, and the
  carried-over components (`AppImage`, `VideoPreview`, `SignInBrowser`, `ZoomableImage`, …).
- `ui/theme/` — `Colors` (the RedditTV dark palette is at the top), `RedditType` (text sizes scaled
  by the user's `TextSize`; read `LocalRedditType.current`).

### Platform abstractions (`util/`)

Check these before building new plumbing: `PlatformUtils`, `SettingsManager` (`Settings.kt`),
`StorageManager` / `FileIO`, `NetworkMonitor` (has `getLocalIpAddress()`), `WebSession` (the app
browser's cookies), `ImageLoaderConfig`, `LoggerConfig`, `CrashHandler`, `Toast`, `RedditFormat`.

### Logging and crash reports

`LoggerConfig.initialize(<platform writer>)` runs at process start — `MainApplication.onCreate`
(Android), `main()` (desktop), `MainViewController()` (iOS) — followed by `installCrashHandler()` and
`LogManager.onAppStart()`. The crash handler must be armed before `onAppStart()`.

> **Everything Supabase-side lives in its own repo: `/Users/jpage/working/projects/supabase`.**
> The `rtv` app id (`Branding.analyticsId`) still has to be registered there.

## Conventions

- **Logging** — use `LoggerConfig.createLogger("ClassName")` (Kermit). Every log line starts with the
  enclosing function name: `log.d("myFunction: ...")`. For errors, log `e.message`, not `e` itself.
  Inside a Ktor `routing {}` block `log` is Ktor's logger — call out to a function instead.
- **Comments** — start with lowercase.
- **Colors** — define in `ui/theme/Colors.kt` rather than inline.
- **Text sizes** — reddit content uses `LocalRedditType.current` so the Aa control scales it.
- **TV sizes** — the design boards are 1920×1080 px; a TV is 960×540 dp, so halve the numbers.
- **Utils** — prefer existing `*Utils` helpers (`JsonUtils`, `DateUtils`, `Utils`) over new ones.
- **Settings** — add a case to the `AppPref` enum and read it through `SettingsManager`; use the
  existing `Setting*Item` composables for settings UI.
- **Imports** — wildcard imports and no fully qualified class names in code.
- **Strings** — use Compose resources over hardcoded strings; outside compose use
  `getString(...)` or `ToastManager.showToast(Res.string.x, …)`.
- **Re-read before editing** — files may have changed since the last read.
- **Don't prompt to check-in code**
- **Don't run the app to verify a change** — compile instead, and say what to look for when the user
  runs it.
- **Docs** — design/investigation write-ups live in `docs/`, named for their topic.

## CHANGES.txt

Every fix or feature appends **one line** to `CHANGES.txt` in the repo root, at the end of the file,
under a bare `MM/DD` header for the current day — add the header only for that day's first entry.

- **One line per change, no wrapping**, high level and behavioural, under 80 characters.
- **Prefix the line with the part of the app that changed** (`Settings:`, `iOS:`, `Android TV:`).
- **A fix says what stopped happening**.

## Things that are not what they look like

- `composeApp/` applies the `com.android.library` plugin and builds the Android library that
  `androidApp` depends on.
- **The Android build is one APK for phones and TVs.** The manifest declares `LEANBACK_LAUNCHER`
  and `android:banner`, and marks both `touchscreen` and `leanback` as not required.
  `PlatformUtils.isTv()` picks the grid or the list at runtime.
- `androidApp` has two product flavors: `full` (sideload release kept current from GitHub
  Releases, `…reddittv.full`) and `beta` (local dev builds, `…reddittv.beta`).
- **Reddit's CDN wants a referer**: images/video go out with `REDDIT_MEDIA_HEADERS`.
- `.github/actions/write-secrets/` — the workflows list **no** secret names; one composite action
  appends every Actions secret to `~/.gradle/gradle.properties` on the runner.
- **Two `proguard-rules.pro` files**: `androidApp/` is R8 for Android, `composeApp/` is the
  *desktop* ProGuard config.
- `scripts/adb-get-device.sh` is a **verbatim copy of the HD+ script** — `source`d, never executed.
- `resources/images/logo.svg` + `logo-foreground.svg` — the source artwork `generate-icons.sh`
  renders every launcher icon and the TV banner from.
- **Compose resources can't decode SVG on Android** — rasterise icons before bundling. Subfolders
  under `composeResources/drawable` are silently ignored.
- `iosApp/Assets.xcassets/AppIcon.appiconset` is generated by `generate-icons.sh`.
