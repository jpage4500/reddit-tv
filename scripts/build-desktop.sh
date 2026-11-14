#!/bin/bash
#######################################################################################################################################
# Build the desktop app and publish the installers via jDeploy.
#
# Usage: build-desktop.sh
#
# jDeploy reads ./package.json and ./icon.png from the repo root (it offers no config key for
# either). the release repo is RELEASE_REPO below; keep it the same as package.json >
# jdeploy.publishTargets[].url, which is what jDeploy writes into the download links.
#
# ICONS: the desktop icons are committed (composeApp/src/desktopMain/resources/icon.icns / .ico /
# .png plus the repo-root icon.png jDeploy installs with). Re-run 'scripts/generate-icons.sh' only
# when resources/images/logo.svg changes.
#
# Required env vars or ~/.gradle/gradle.properties entries (validated up front):
#   API keys:  LOGGING_URL, LOGGING_API
#   Publish:   GH_TOKEN — needs write access to the release repo (also used by the gh CLI to add
#              the release notes, and by gen_release_notes.sh to read PR titles)
#
# Also run by .github/workflows/build-desktop.yml, so a local publish and a CI one are the same.
# The signed + notarized Mac DMG is CI-only (the build-dmg job, when the MAC_* secrets are set).
#######################################################################################################################################

# Exit immediately if a command exits with a non-zero status
set -e

SECONDS=0

cd "$(dirname "$0")/.."

# Function to fetch properties from the environment or gradle.properties
function getProperty {
    PROP_KEY=$1
    if [ -n "${!PROP_KEY:-}" ]; then
        echo "${!PROP_KEY}"
    else
        # Suppress grep error if file doesn't exist, returning empty rather than failing
        PROP_VALUE=$(grep "^$PROP_KEY=" ~/.gradle/gradle.properties 2>/dev/null | cut -d'=' -f2-)
        echo "${PROP_VALUE}"
    fi
}

echo "Loading environment variables..."
export LOGGING_URL=$(getProperty "LOGGING_URL")
export LOGGING_API=$(getProperty "LOGGING_API")

# jDeploy relies on GITHUB_TOKEN to authenticate the release upload
export GITHUB_TOKEN=$(getProperty "GH_TOKEN")

# ==========================================
# Validate Environment Variables
# ==========================================
echo "Verifying environment variables..."
MISSING_VARS=0

# Loop through a list of all required variable names
for VAR_NAME in LOGGING_URL LOGGING_API GITHUB_TOKEN; do
    # ${!VAR_NAME} uses indirect expansion to check the value of the variable named in $VAR_NAME
    if [ -z "${!VAR_NAME}" ]; then
        echo "❌ Error: $VAR_NAME is missing or empty. Please check your environment or ~/.gradle/gradle.properties."
        MISSING_VARS=1
    fi
done

# If any variables were missing, exit the script immediately
if [ "$MISSING_VARS" -eq 1 ]; then
    echo "🚨 Exiting: Cannot proceed with missing environment variables."
    exit 1
fi

echo "✅ All required environment variables are set."
# ==========================================

echo "Preparing Gradle..."
chmod +x ./gradlew

echo "Building executable jar..."
./gradlew :composeApp:buildExecutableJar 

echo "Fetching version from Gradle..."
VERSION=$(./gradlew -q printVersion  | tail -n 1)
echo "Version resolved: $VERSION"

echo "Syncing package.json version..."
# --no-git-tag-version ensures no local tag is created during the version bump; --allow-same-version
# lets a failed publish be re-run, since the first attempt already wrote the version
npm version "$VERSION" --no-git-tag-version --allow-same-version

# 'jdeploy publish' can't publish to GitHub from the command line: it never hands the driver a token
# ("GitHub token is required for publishing to GitHub"); only its GUI and GitHub Action can. so this
# does what the Action (shannah/jdeploy action.yml) does for a target repo other than the one running
# it: jDeploy builds the release files, and gh uploads them.
RELEASE_REPO="jpage4500/reddit-tv"
RELEASE_FILES="jdeploy/github-release-files"

echo "Preparing release files via jDeploy..."
rm -rf "$RELEASE_FILES"
# pin the major version: a bare 'npx jdeploy' runs a globally installed jdeploy when there is one, and
# an old one (4.x) ignores publishTargets and falls back to 'npm publish' (ENEEDAUTH).
# github-prepare-release reads the published package-info.json from the jdeploy tag and adds this version
GITHUB_REPOSITORY="$RELEASE_REPO" npx --yes jdeploy@6 github-prepare-release

echo "Publishing $RELEASE_REPO release $VERSION..."
# a re-run replaces the release, as the Action does
GH_TOKEN="$GITHUB_TOKEN" gh release delete "$VERSION" --repo "$RELEASE_REPO" --yes 2>/dev/null || true
GH_TOKEN="$GITHUB_TOKEN" gh release create "$VERSION" --repo "$RELEASE_REPO" --title "$VERSION" \
    -F "$RELEASE_FILES/jdeploy-release-notes.md" "$RELEASE_FILES"/*

# installed copies check the 'jdeploy' tag's package-info.json for updates. uploaded after the version
# release, so it never lists a version that isn't there yet. package-info-2.json is jDeploy's backup copy
echo "Updating the jdeploy tag's package-info.json..."
if ! GH_TOKEN="$GITHUB_TOKEN" gh release view jdeploy --repo "$RELEASE_REPO" > /dev/null 2>&1; then
    GH_TOKEN="$GITHUB_TOKEN" gh release create jdeploy --repo "$RELEASE_REPO" --title jdeploy --prerelease \
        --notes "Release metadata for jDeploy releases"
fi
PACKAGE_INFO_DIR=$(mktemp -d)
cp "$RELEASE_FILES/package-info.json" "$PACKAGE_INFO_DIR/package-info.json"
cp "$RELEASE_FILES/package-info.json" "$PACKAGE_INFO_DIR/package-info-2.json"
GH_TOKEN="$GITHUB_TOKEN" gh release upload jdeploy --repo "$RELEASE_REPO" --clobber \
    "$PACKAGE_INFO_DIR/package-info.json" "$PACKAGE_INFO_DIR/package-info-2.json"
rm -rf "$PACKAGE_INFO_DIR"

# the release body is jDeploy's download links. put our notes above them
echo "Adding release notes to $RELEASE_REPO release $VERSION..."
chmod +x scripts/gen_release_notes.sh
# this branch's changes (or main's last PR), minus android/iOS lines
GH_TOKEN="$GITHUB_TOKEN" ./scripts/gen_release_notes.sh desktop > release-notes.txt || true
echo "📝 Release notes:"
cat release-notes.txt
EXISTING=$(GH_TOKEN="$GITHUB_TOKEN" gh release view "$VERSION" --repo "$RELEASE_REPO" --json body -q '.body' || echo "")
{ echo "## Release Notes"; echo ""; cat release-notes.txt; echo ""; echo "$EXISTING"; } > combined-notes.txt
GH_TOKEN="$GITHUB_TOKEN" gh release edit "$VERSION" --repo "$RELEASE_REPO" --notes-file combined-notes.txt
echo "✅ release notes added: https://github.com/$RELEASE_REPO/releases/tag/$VERSION"

echo "🎉 Build and deployment complete!"
printf '⏱  Total time: %dm %02ds\n' $((SECONDS/60)) $((SECONDS%60))
