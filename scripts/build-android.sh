#!/bin/bash
#######################################################################################################################################
# Build the Android app — handles both local install and CI publish flows.
#
# Usage: build-android.sh [mode]
#
# Modes:
#   local         (default) build the 'beta' flavor release APK, install via adb, launch the app.
#                           'beta' is a distinct package (com.jpage4500.reddittv.beta) from the
#                           auto-updating 'full' sideload install, so local installs never clobber it.
#                           scripts/adb-get-device.sh prompts for a device (defaulting to the last used),
#                           and asks again after each install until 'x', so one build reaches several.
#   ci                      build the full-flavor APK; on branch == main, create a GitHub Release
#                           with it (and refresh the fixed-link copy, below)
#   publish-github          build the full-flavor APK and create a GitHub Release with it (and refresh
#                           the fixed-link copy), on any branch
#
# RedditTV is GitHub-only (no Play Store build), so there is no AAB or Play publish here.
#
# Required env vars or ~/.gradle/gradle.properties entries (validated up front):
#   API keys:           LOGGING_URL, LOGGING_API
#   Signing:            KMP_SIGNING_STORE_PASSWORD, KMP_SIGNING_KEY_ALIAS, KMP_SIGNING_KEY_PASSWORD,
#                       KMP_KEYSTORE (base64-encoded keystore; Gradle decodes it on configure)
#   GitHub release:     GH_TOKEN (used by the gh CLI)
#######################################################################################################################################

set -e

SECONDS=0
MODE="${1:-local}"

cd "$(dirname "$0")/.."

# ==========================================
# release identity
# ==========================================
RELEASE_REPO="jpage4500/reddit-tv"
APK_BASENAME="reddit-tv"
# every android release also replaces the one asset on the fixed "$STABLE_TAG" release, so this
# never changes: https://github.com/<RELEASE_REPO>/releases/download/<STABLE_TAG>/<STABLE_APK_NAME>
# — what the README, the Downloader code and the TV's QR codes point at. GitHub's own
# releases/latest/download/… can't do it: jDeploy's desktop releases share the repo, so "latest"
# is as often a release with no APK in it
STABLE_TAG="android"
STABLE_APK_NAME="RedditTV.apk"
APP_ID_BASE="com.jpage4500.reddittv"
echo "▶ releases → $RELEASE_REPO"

# fetch a property from env or fall back to ~/.gradle/gradle.properties
function getProperty {
    PROP_KEY=$1
    if [ -n "${!PROP_KEY:-}" ]; then
        echo "${!PROP_KEY}"
    else
        PROP_VALUE=$(grep "^$PROP_KEY=" ~/.gradle/gradle.properties 2>/dev/null | cut -d'=' -f2-)
        echo "${PROP_VALUE}"
    fi
}

# ==========================================
# fetch + validate variables
# ==========================================
echo "Verifying environment variables..."
REQUIRED_VARS=(
    LOGGING_URL
    LOGGING_API
    KMP_SIGNING_KEY_ALIAS
    KMP_SIGNING_KEY_PASSWORD
    KMP_SIGNING_STORE_PASSWORD
)

for VAR_NAME in "${REQUIRED_VARS[@]}"; do
    export ${VAR_NAME}="$(getProperty "$VAR_NAME")"
    if [ -z "${!VAR_NAME}" ]; then
        echo "❌ Error: $VAR_NAME is missing or empty. Set it in env or ~/.gradle/gradle.properties."
        exit 1
    fi
done
echo "✅ API keys + signing creds present"

# keystore is supplied as KMP_KEYSTORE (base64). Gradle decodes it directly — we just verify
# it's present and export so child processes see it.
export KMP_KEYSTORE="$(getProperty "KMP_KEYSTORE")"
if [ -z "$KMP_KEYSTORE" ]; then
    echo "❌ Error: KMP_KEYSTORE (base64-encoded keystore) must be set."
    exit 1
fi
echo "✅ Keystore present (base64, $(echo -n "$KMP_KEYSTORE" | wc -c | tr -d ' ') chars)"

echo "Preparing Gradle..."
chmod +x ./gradlew
VERSION=$(./gradlew -q printVersion | tail -n 1)
echo "VERSION: $VERSION"

# ==========================================
# build helpers
# ==========================================
function buildSideloadApk {
    echo "📦 Building full-flavor release APK..."
    ./gradlew :androidApp:assembleFullRelease
    APK_SRC="androidApp/build/outputs/apk/full/release/androidApp-full-release.apk"
    if [ ! -f "$APK_SRC" ]; then
        echo "❌ APK not found at expected path: $APK_SRC"
        exit 1
    fi
    echo "✅ APK built: $APK_SRC"
}

function buildBetaApk {
    # 'beta' is the local-dev flavor: identical to 'full' but a distinct package
    # (com.jpage4500.reddittv.beta) so installing a local build never clobbers the
    # auto-updating 'full' sideload install.
    echo "📦 Building beta-flavor release APK..."
    ./gradlew :androidApp:assembleBetaRelease
    APK_SRC="androidApp/build/outputs/apk/beta/release/androidApp-beta-release.apk"
    if [ ! -f "$APK_SRC" ]; then
        echo "❌ APK not found at expected path: $APK_SRC"
        exit 1
    fi
    echo "✅ APK built: $APK_SRC"
}

# install the beta APK on one device and launch it
function installBetaApk {
    local device="$1"
    local apkPath="androidApp/build/outputs/apk/beta/release/androidApp-beta-release.apk"
    echo "Installing APK via adb..."
    # captured so a signature mismatch (an install signed with a different key, ie: an
    # Android Studio debug build) can offer to uninstall and retry
    local installOutput
    if ! installOutput=$(adb -s "$device" install -r "$apkPath" 2>&1); then
        echo "$installOutput"
        if echo "$installOutput" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
            echo "⚠️  ${APP_ID_BASE}.beta on this device is signed with a different key."
            read -r -p "Uninstall it (its data is lost) and install again? [y/N] " ANSWER || true
            if [[ "$ANSWER" =~ ^[Yy] ]]; then
                adb -s "$device" uninstall "${APP_ID_BASE}.beta"
                adb -s "$device" install "$apkPath" || return 1
            else
                return 1
            fi
        else
            return 1
        fi
    else
        echo "$installOutput"
    fi
    echo "Running app..."
    adb -s "$device" shell am start -n "${APP_ID_BASE}.beta/com.jpage4500.reddittv.MainActivity"
}

# ==========================================
# deploy helpers
# ==========================================
function generateReleaseNotes {
    chmod +x scripts/gen_release_notes.sh
    # this branch's changes (or main's last PR), minus iOS/desktop lines
    ./scripts/gen_release_notes.sh android > release-notes.txt || true
    echo "📝 Release notes:"
    cat release-notes.txt
}

function deployGithubRelease {
    # VERSION-android tag in $RELEASE_REPO. the "-android" suffix distinguishes android releases
    # from the desktop installers jDeploy publishes to the same repo
    if [ -z "${GH_TOKEN:-}" ]; then
        export GH_TOKEN="$(getProperty "GH_TOKEN")"
    fi
    if [ -z "${GH_TOKEN:-}" ]; then
        echo "⚠️  GH_TOKEN not set — skipping GitHub Release"
        return
    fi
    TAG="${VERSION}-android"
    APK_SRC="androidApp/build/outputs/apk/full/release/androidApp-full-release.apk"
    APK_NAME="${APK_BASENAME}-${VERSION}.apk"
    cp "$APK_SRC" "$APK_NAME"

    APK_URL="https://github.com/${RELEASE_REPO}/releases/download/${TAG}/${APK_NAME}"
    {
        echo "## Release Notes"
        echo ""
        cat release-notes.txt
        echo ""
    } > combined-notes.txt

    echo "🚀 Creating GitHub Release $TAG..."
    gh release create "$TAG" "$APK_NAME" \
        --repo "$RELEASE_REPO" \
        --title "Android ${VERSION}" \
        --notes-file combined-notes.txt \
        --latest=false
    echo "✅ GitHub Release created"

    # the fixed link: made once (--latest=false so it never becomes the repo's "Latest"), then only
    # its asset is swapped. UpdateManager skips its tag
    cp "$APK_SRC" "$STABLE_APK_NAME"
    if ! gh release view "$STABLE_TAG" --repo "$RELEASE_REPO" >/dev/null 2>&1; then
        gh release create "$STABLE_TAG" \
            --repo "$RELEASE_REPO" \
            --title "Android (always the newest APK)" \
            --notes "The newest Android APK, at a link that never changes: https://github.com/${RELEASE_REPO}/releases/download/${STABLE_TAG}/${STABLE_APK_NAME}" \
            --latest=false
    fi
    gh release upload "$STABLE_TAG" "$STABLE_APK_NAME" --repo "$RELEASE_REPO" --clobber
    echo "✅ https://github.com/${RELEASE_REPO}/releases/download/${STABLE_TAG}/${STABLE_APK_NAME} is now ${VERSION}"
}

# ==========================================
# mode dispatch
# ==========================================
case "$MODE" in
    local)
        echo "▶ local mode: build 'beta' flavor + adb install (leaves the auto-updating 'full' install untouched)"
        buildBetaApk
        if ! command -v adb &> /dev/null; then
            echo "❌ adb not found. Install Android SDK platform-tools."
            exit 1
        fi
        # pick devices to install to until 'x' - adb-get-device.sh exports ADB_DEVICE. sourced with
        # `||` on purpose: `set -e` is suspended there, so a "no devices" return reaches the case
        # below instead of killing the script on the first failing command inside the selector.
        while true; do
            PICK=0
            source scripts/adb-get-device.sh -x || PICK=$?
            case $PICK in
                0) installBetaApk "$ADB_DEVICE" || true ;;
                2) break ;;
                *) if [[ -z "$RAW_DEVICES" ]]; then echo "no devices!"; exit 1; fi ;;
            esac
        done
        ;;

    ci)
        BRANCH_NAME=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
        # in GitHub Actions, HEAD is detached — fall back to GITHUB_REF_NAME
        if [ -z "$BRANCH_NAME" ] || [ "$BRANCH_NAME" = "HEAD" ]; then
            BRANCH_NAME="${GITHUB_REF_NAME:-}"
        fi
        echo "▶ ci mode (branch=$BRANCH_NAME)"

        buildSideloadApk

        if [ "$BRANCH_NAME" = "main" ]; then
            generateReleaseNotes
            deployGithubRelease
        else
            echo "ℹ️  branch is $BRANCH_NAME (not main) — built artifacts only, no publish"
        fi
        ;;

    publish-github)
        echo "▶ publish-github mode: full-flavor APK → GitHub Release"
        # deployGithubRelease only warns without a token, which suits ci — here it's the whole point
        if [ -z "${GH_TOKEN:-}" ]; then
            export GH_TOKEN="$(getProperty "GH_TOKEN")"
        fi
        if [ -z "${GH_TOKEN:-}" ]; then
            echo "❌ GH_TOKEN is required for publish-github. Set it in env or ~/.gradle/gradle.properties."
            exit 1
        fi
        buildSideloadApk
        generateReleaseNotes
        deployGithubRelease
        ;;

    *)
        echo "❌ unknown mode: $MODE (expected: local | ci | publish-github)"
        exit 1
        ;;
esac

echo "🎉 Done."
printf '⏱  Total time: %dm %02ds\n' $((SECONDS/60)) $((SECONDS%60))
