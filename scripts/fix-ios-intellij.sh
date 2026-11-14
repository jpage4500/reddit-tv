#!/usr/bin/env bash
set -euo pipefail
###################################################################
# Repair "can't run iosApp from IntelliJ IDEA" after an Xcode update.
#
# Xcode itself keeps working while IntelliJ breaks, because the JetBrains
# Kotlin Multiplatform plugin drives Xcode differently than Xcode does.
# Two independent things go wrong; this script checks and fixes both.
#
# 1) Metal toolchain vs. the plugin's toolchain override
#    IntelliJ builds with a global toolchain override to "Xcode Default"
#    (every build task logs: "Using global toolchain override 'Xcode
#    Default'"). Since Xcode 26 the Metal compiler is NOT inside
#    XcodeDefault.xctoolchain — it is a separately downloaded, cryptex-
#    mounted Metal.xctoolchain. Pinning TOOLCHAINS to XcodeDefault stops
#    the `metal` shim handing off to it, so compiling KSPlayer's
#    Shaders.metal dies with:
#        error: cannot execute tool 'metal' due to missing Metal Toolchain
#    IntelliJ reports this as "Build failed with 0 errors" — the real error
#    only appears in the .xcactivitylog. Plain `xcrun metal` still works,
#    which is why Xcode and command-line builds appear fine.
#
#    Despite the message, `-downloadComponent MetalToolchain` does NOT fix
#    this: Apple ships one Metal asset build per Xcode, so re-downloading
#    returns the same asset. The fix is to give XcodeDefault.xctoolchain the
#    `usr/metal` directory it lacks, symlinked to the mounted
#    Metal.xctoolchain — then a pinned build resolves the compiler normally.
#    Needs sudo, and does not survive an Xcode update, hence this script.
#
#    Note pre-compiling the shader from the CLI does NOT work as a
#    workaround: the build task signature includes the toolchain, so
#    IntelliJ's pinned build always considers the .air stale and recompiles.
#
# 2) Stale run destination in .idea/workspace.xml
#    IntelliJ persists the chosen destination. If it ends up as
#    "iphoneos_" — platform iphoneos with an EMPTY device UDID, i.e.
#    Xcode's generic "Any iOS Device" placeholder — nothing can launch on
#    it: the dropdown shows only "iOS Device" and the Run button vanishes.
#    The value survives restarts and cache invalidation, so it has to be
#    removed from disk. Usually caused by the config having been pinned to
#    a physical device that is no longer connected.
#
# Run this after every Xcode upgrade, or whenever iosApp stops being
# runnable from IntelliJ. Safe to re-run; it only changes what is broken.
#
# Usage:
#   scripts/fix-ios-intellij.sh            # check and fix
#   scripts/fix-ios-intellij.sh --check    # report only, change nothing
#
# Unrelated to scripts/seed-ios-packages.sh, which fixes SwiftPM package
# resolution behind an SSL-inspection proxy (Netskope). If Xcode cannot
# resolve packages at all, run that one first.
###################################################################

cd "$(dirname "$0")/.."

CHECK_ONLY=0
[ "${1:-}" = "--check" ] && CHECK_ONLY=1

WORKSPACE_XML=".idea/workspace.xml"
XCODE_DEFAULT_TOOLCHAIN="com.apple.dt.toolchain.XcodeDefault"

PROBLEMS=0
FIXED=0

ok()      { echo "  ✅ $*"; }
info()    { echo "     $*"; }
warn()    { echo "  ⚠️  $*"; }
bad()     { echo "  ✗ $*"; PROBLEMS=$((PROBLEMS + 1)); }
fixed()   { echo "  🔧 $*"; FIXED=$((FIXED + 1)); }
section() { echo; echo "== $* =="; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

###################################################################
section "Xcode"

if ! DEVELOPER_DIR_PATH="$(xcode-select -p 2>/dev/null)"; then
    bad "xcode-select failed — no Xcode selected"
    exit 1
fi
case "$DEVELOPER_DIR_PATH" in
    *CommandLineTools*)
        bad "xcode-select points at CommandLineTools, not Xcode.app"
        info "fix: sudo xcode-select -s /Applications/Xcode.app"
        ;;
    *) ok "developer dir: $DEVELOPER_DIR_PATH" ;;
esac

XCODE_VERSION="$(xcodebuild -version 2>/dev/null | head -1 || echo '?')"
XCODE_BUILD="$(xcodebuild -version 2>/dev/null | sed -n 's/^Build version //p' || echo '?')"
ok "$XCODE_VERSION (build ${XCODE_BUILD:-?})"

###################################################################
section "Metal toolchain (IntelliJ's blocker)"

cat > "$TMP/probe.metal" <<'METAL'
#include <metal_stdlib>
kernel void probe() {}
METAL

# two probes, because they fail for different reasons and need different fixes:
#   A - no toolchain pin  => is the Metal toolchain usable at all?
#   B - TOOLCHAINS pinned to XcodeDefault => is it usable the way IntelliJ builds?
# A can pass while B fails, which is why `xcrun metal --version` is worthless as
# a check here — only B predicts an IntelliJ build.
metal_probe() {
    local tc="$1" sdk
    sdk="$(xcrun --sdk iphoneos --show-sdk-path 2>/dev/null || true)"
    if [ -n "$tc" ]; then
        TOOLCHAINS="$tc" xcrun metal -c -target air64-apple-ios13.0 \
            ${sdk:+-isysroot "$sdk"} -o "$TMP/probe.air" "$TMP/probe.metal" 2>&1
    else
        env -u TOOLCHAINS xcrun metal -c -target air64-apple-ios13.0 \
            ${sdk:+-isysroot "$sdk"} -o "$TMP/probe.air" "$TMP/probe.metal" 2>&1
    fi
}

PROBE_A_OUT=""; PROBE_B_OUT=""
metal_ok_unpinned=0; metal_ok_pinned=0
# assign inside `if` so `set -e` does not abort on the expected failures
if PROBE_A_OUT="$(metal_probe "")"; then metal_ok_unpinned=1; fi
if PROBE_B_OUT="$(metal_probe "$XCODE_DEFAULT_TOOLCHAIN")"; then metal_ok_pinned=1; fi

if [ "$metal_ok_unpinned" = 0 ]; then
    # the toolchain really is missing — this is the case -downloadComponent fixes
    bad "Metal toolchain is not usable at all"
    echo "$PROBE_A_OUT" | sed -n '1,2p' | sed 's/^/       /'
    if [ "$CHECK_ONLY" = 1 ]; then
        info "fix: xcodebuild -downloadComponent MetalToolchain"
    else
        info "downloading the Metal toolchain (multi-GB, several minutes)..."
        info "on a VPN with SSL inspection (Netskope), disconnect first — large"
        info "transfers get corrupted and the download fails."
        if xcodebuild -downloadComponent MetalToolchain &&
           metal_probe "" >/dev/null 2>&1; then
            fixed "Metal toolchain installed"
            PROBLEMS=$((PROBLEMS - 1))
            metal_ok_unpinned=1
        else
            warn "still unusable — try: sudo xcodebuild -downloadComponent MetalToolchain"
        fi
    fi
else
    ok "Metal toolchain works (unpinned)"
fi

if [ "$metal_ok_pinned" = 1 ]; then
    ok "metal also works under the 'Xcode Default' toolchain override"
elif [ "$metal_ok_unpinned" = 1 ]; then
    # THE common case. do not suggest -downloadComponent: Apple ships exactly one
    # Metal asset build per Xcode, so re-downloading returns the same asset and
    # changes nothing. The Metal compiler lives in a separate Metal.xctoolchain
    # and pinning TOOLCHAINS to XcodeDefault blocks the hand-off, full stop.
    bad "metal FAILS under the 'Xcode Default' toolchain override — this is what breaks IntelliJ"
    echo "$PROBE_B_OUT" | sed -n '1,2p' | sed 's/^/       /'
    info "the Metal compiler is fine; IntelliJ just pins TOOLCHAINS to XcodeDefault,"
    info "which has no usr/metal, so the shim cannot reach the real compiler."
    info "re-downloading the component does NOT help (same asset build every time)."

    # Real fix: XcodeDefault.xctoolchain is missing the `usr/metal` directory that
    # the shim resolves the compiler through. Symlinking it to the live
    # Metal.xctoolchain makes a pinned build work — verified by building a probe
    # toolchain containing only the shim + this symlink and compiling a shader
    # under TOOLCHAINS pinned to it.
    #
    # Caveats, both handled by just re-running this script:
    #   - needs sudo (Xcode.app is root-owned) and technically modifies Xcode.app
    #   - the Metal toolchain lives on a cryptex mount whose path carries a version
    #     token, so the link can dangle after an Xcode/OS update or a remount
    METAL_TC="$(ls -d /private/var/run/com.apple.security.cryptexd/mnt/com.apple.MobileAsset.MetalToolchain-*/Metal.xctoolchain 2>/dev/null | head -1 || true)"
    METAL_LINK="$DEVELOPER_DIR_PATH/Toolchains/XcodeDefault.xctoolchain/usr/metal"

    if [ -z "$METAL_TC" ]; then
        warn "cannot locate a mounted Metal.xctoolchain to link against"
        info "run 'xcrun metal --version' once to trigger the mount, then re-run this"
    elif [ -L "$METAL_LINK" ] && [ ! -e "$METAL_LINK" ]; then
        info "existing usr/metal symlink is dangling (Metal toolchain was remounted)"
        info "re-run with sudo to repoint it: sudo $0"
    fi

    if [ -n "$METAL_TC" ]; then
        if [ "$CHECK_ONLY" = 1 ]; then
            info "fix available: re-run without --check (will need sudo) to add"
            info "  $METAL_LINK -> $METAL_TC/usr/metal"
        else
            echo
            info "linking usr/metal into XcodeDefault.xctoolchain (needs sudo)..."
            if sudo ln -sfn "$METAL_TC/usr/metal" "$METAL_LINK" 2>/dev/null &&
               metal_probe "$XCODE_DEFAULT_TOOLCHAIN" >/dev/null 2>&1; then
                fixed "metal now compiles under IntelliJ's toolchain pin"
                PROBLEMS=$((PROBLEMS - 1))
                info "re-run this script after an Xcode update — the link is inside"
                info "Xcode.app and points at a versioned mount, so it will not survive one."
            else
                warn "could not apply the symlink (sudo declined?) — run manually:"
                info "  sudo ln -sfn '$METAL_TC/usr/metal' '$METAL_LINK'"
                info "then re-run this script to verify."
            fi
        fi
    fi
fi

###################################################################
section "IntelliJ run destination ($WORKSPACE_XML)"

if [ ! -f "$WORKSPACE_XML" ]; then
    ok "no workspace.xml yet — IntelliJ will create a clean one"
elif ! grep -q 'iphoneos_"' "$WORKSPACE_XML" 2>/dev/null &&
     ! grep -q 'iphonesimulator_"' "$WORKSPACE_XML" 2>/dev/null &&
     ! grep -q '"loadedSdks" value="Device"' "$WORKSPACE_XML" 2>/dev/null; then
    ok "no stale placeholder destination"
else
    bad "stale destination / SDK state found (generic 'Any iOS Device' placeholder)"

    if [ "$CHECK_ONLY" = 1 ]; then
        info "fix: quit IntelliJ, then re-run this script without --check"
    elif pgrep -f 'IntelliJ IDEA.app/Contents/MacOS/idea' >/dev/null 2>&1; then
        warn "IntelliJ is running — it rewrites workspace.xml on exit, so edits would be lost"
        info "quit IntelliJ (⌘Q), then re-run this script"
    else
        cp "$WORKSPACE_XML" "$WORKSPACE_XML.bak"
        rc=0
        python3 - "$WORKSPACE_XML" <<'PY' || rc=$?
import re, sys

path = sys.argv[1]
text = original = open(path, encoding="utf-8").read()

# a destination id is "<platform>_<udid>"; an EMPTY udid is the unrunnable
# "Any iOS Device"/"Any iOS Simulator" placeholder. only strip those, so a
# real selected device or simulator is left alone.
placeholder = r'(?:iphoneos|iphonesimulator)_(?=")'

# self-closing one-liner: <component name="ExecutionTargetManager" SELECTED_TARGET="iphoneos_" />
text = re.sub(
    r'[ \t]*<component name="ExecutionTargetManager"[^>]*?SELECTED_TARGET="'
    + placeholder + r'"[^>]*/>\n',
    "", text)

# block form: <component name="XcodeBuildConfigurationMetadata"> ... </component>
def drop_block(name, src, require=None):
    pattern = (r'[ \t]*<component name="' + re.escape(name)
               + r'">.*?</component>\n')
    def repl(m):
        if require is None or re.search(require, m.group(0)):
            return ""
        return m.group(0)
    return re.sub(pattern, repl, src, flags=re.DOTALL)

text = drop_block("XcodeBuildConfigurationMetadata", text,
                  require=r'buildDestinationId="' + placeholder + r'"')

# only Device resolve context loaded => simulator source sets lose code
# insight and simulator destinations get filtered out; let it re-detect.
text = drop_block("XCUnloadedResolveContextsSettings", text,
                  require=r'"loadedSdks" value="Device"')

if text == original:
    print("       nothing matched — leaving file unchanged")
    sys.exit(2)

open(path, "w", encoding="utf-8").write(text)
PY
        case $rc in
            0)
                fixed "removed stale destination state (backup: $WORKSPACE_XML.bak)"
                PROBLEMS=$((PROBLEMS - 1))
                info "reopen the project and pick a simulator or device in the dropdown"
                ;;
            2) rm -f "$WORKSPACE_XML.bak" ;;
            *) warn "could not rewrite $WORKSPACE_XML" ;;
        esac
    fi
fi

###################################################################
section "Simulator runtimes"

RUNTIME_COUNT="$(xcrun simctl list runtimes 2>/dev/null | grep -c '^iOS ' || true)"
if [ "${RUNTIME_COUNT:-0}" -gt 0 ]; then
    ok "$RUNTIME_COUNT iOS runtime(s) installed"
    # the plugin logs "Simulator runtime not found: <id>" when it cannot
    # parse a runtime — that is a plugin-vs-Xcode version problem this
    # script cannot fix, so surface it rather than pretend it is fine.
    LOG="$(ls -t "$HOME/Library/Logs/JetBrains"/*/idea.log 2>/dev/null | head -1 || true)"
    if [ -n "$LOG" ] && grep -q 'Simulator runtime not found' "$LOG" 2>/dev/null; then
        warn "IntelliJ logged 'Simulator runtime not found' — the KMP plugin cannot read"
        info "these runtimes, so only physical devices appear in the dropdown."
        info "that is a plugin/Xcode version mismatch: update IntelliJ IDEA."
        info "log: $LOG"
    fi
else
    warn "no iOS simulator runtimes installed"
    info "fix: xcodebuild -downloadPlatform iOS"
fi

###################################################################
section "Summary"

if [ "$PROBLEMS" -eq 0 ]; then
    [ "$FIXED" -gt 0 ] && echo "✅ Fixed $FIXED problem(s). Reopen the project in IntelliJ and run iosApp." \
                       || echo "✅ Nothing to fix — iosApp should run from IntelliJ."
    exit 0
fi

echo "✗ $PROBLEMS problem(s) still outstanding."
[ "$CHECK_ONLY" = 1 ] && echo "  (ran with --check; re-run without it to apply fixes)"
echo
echo "If the build still fails with \"0 errors\", read the real error from Xcode's log:"
echo "  L=\$(ls -t ~/Library/Caches/JetBrains/*/DerivedData/iosApp-*/Logs/Build/*.xcactivitylog | head -1)"
echo "  gunzip -c \"\$L\" | strings | grep -iE 'error|failed'"
exit 1
