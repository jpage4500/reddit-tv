# Ported From Share to TV

RedditTV started as a copy of **CastToTVKMP** (Share to TV+), the same way that app was made from
Weather Dashboard+.

## What came across

- Build: Gradle setup, version catalog, BuildConfig, `androidApp` (flavors `full` / `beta`; the Play
  `google` flavor and the Play publisher plugin are gone), `iosApp`, desktop packaging (jDeploy),
  `.run/` configs, `.github/` workflows + the `write-secrets` action.
- Platform layer (`util/`): PlatformUtils, Settings, StorageManager/FileIO, NetworkMonitor,
  WebSession, LoggerConfig/AppLogWriter, CrashHandler, ImageLoaderConfig, Toast, Json/Date/Utils,
  LruCache, CpuSampler, LogRedactor, desktop CdpClient + PreviewPlayer.
- Managers: NetworkManager, HttpClientFactory, HttpLog, LogManager, AnalyticsManager, UpdateManager,
  AppLifecycle.
- UI: every generic component (AppImage, AppText, AppButton, VideoPreview, ZoomableImage,
  SignInBrowser, FocusIndication, FocusMemory, BackPress, TvTextInput, SearchField, …), BaseDialog
  and the `dialog/common` dialogs, About / Logs / Check for Updates / Sign-in dialogs, the
  `Setting*Item` composables.
- Scripts: build-android (GitHub-only now), run-desktop, run-ios, generate-icons, gen_release_notes,
  screenshot-ios, fix-ios-simulator, adb-get-device, set-github-secrets, remove-orphaned-branches;
  `fix-ios-intellij.sh` from HubitatDashboardKMP. Skill: `release-notes`.

## What was dropped

Everything about casting: device discovery, DIAL / Cast / AirPlay, the receiver, playlists, the
share intent + iOS share flow, link extraction, the browser extension, the Link Tester, store
listings (Play + App Store), whisperplay.

## Done since the port

- **Branding artwork**: RedditTV's own icon (`resources/images/logo*.svg` — a TV-headed mascot
  with rabbit-ear antennas on reddit orange, its own character rather than reddit's Snoo); every
  icon regenerated. The `beta`
  flavor's launcher background is green and its label "RedditTV Beta".
- **Desktop release**: `package.json`, `.jdpignore.*` and `scripts/build-desktop.sh` added;
  `build-desktop.yml` publishes to this repo (no README mirror, no URL-scheme DMG patch — the DMG
  action notarizes itself).
- **Analytics id**: `rtv` is in the supabase repo's `APP_NAMES` (needs a deploy there).
- **Strings**: the cast strings are gone, and every screen's English is in `strings.xml`.
- **Unused drawables** removed, and `SimpleHtmlRenderer` (unused).

## Still outstanding

- **iOS**: `iosApp/ShareExtension` target is still in the Xcode project (unused); remove it in
  Xcode. Bundle ids/team in the Xcode project still need checking.
- **Workflows**: `build-ios.yml` targets App Store Connect, which this app doesn't use (it's
  manual-only, so it never runs on a merge).
