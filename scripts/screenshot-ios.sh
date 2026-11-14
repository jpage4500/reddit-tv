#!/bin/bash
#######################################################################################################################################
# Take a screenshot of a running iOS simulator and save it to ~/Downloads.
#
# Usage: screenshot-ios.sh [device] [-o|--open] [--out file] [--rotate auto|none|cw|ccw|180]
#
#   device        simulator name or UDID (see: xcrun simctl list devices booted).
#                 Defaults to the booted simulator; if more than one is booted, they're listed
#                 and you're asked to name one.
#   -o, --open    reveal the saved file in Finder
#   --out         save to this file instead (record-demo.sh's captures come through here)
#   --rotate      how to turn the capture. Default "auto" — see below.
#
# Saves as ~/Screenshots/cast-ios-<yyyyMMdd-HHmmss>.png
#
# ROTATION. simctl always writes the device's NATIVE framebuffer, so a screenshot taken
# while the simulator is rotated comes out portrait-shaped with the UI lying on its side —
# which App Store Connect then rejects for being the wrong size. Simulator.app records the
# window orientation per device in com.apple.iphonesimulator, and "auto" reads it back and
# turns the capture to match:
#
#   Portrait / not recorded  no rotation
#   PortraitUpsideDown       180
#   LandscapeLeft            90 counter-clockwise   (home button to the right)
#   LandscapeRight           90 clockwise           (home button to the left)
#
# The plist is only written when the window is rotated through Simulator's own UI
# (Cmd-left / Cmd-right), so a device rotated some other way — programmatically, or by an
# app that forces an orientation — leaves a stale value. The script prints what it did on
# every rotated capture; if it ever reads wrong, pass --rotate explicitly.
#
# Requires ImageMagick for anything but --rotate none (brew install imagemagick).
#######################################################################################################################################

set -e

OUT_DIR="$HOME/Screenshots"
PREFIX="cast-ios"

function usage {
    cat <<EOF
usage: screenshot-ios.sh [device] [-o|--open] [--out file] [--rotate auto|none|cw|ccw|180]

  device      simulator name or UDID (see: xcrun simctl list devices booted).
              defaults to the booted simulator; if more than one is booted,
              they're listed and you're asked to name one.
  -o, --open  reveal the saved file in Finder
  --out       save to this file instead
  --rotate    how to turn the capture. default "auto", which matches whatever
              orientation Simulator has the device's window in — simctl always
              writes the native portrait framebuffer, so a landscape screenshot
              is sideways without this.

saves to $OUT_DIR/$PREFIX-<yyyyMMdd-HHmmss>.png
EOF
}

DEVICE=""
OPEN_FINDER=false
ROTATE="auto"
OUT_FILE=""

while [ $# -gt 0 ]; do
    case "$1" in
        -o|--open) OPEN_FINDER=true ;;
        --rotate) ROTATE="${2:-}"; shift ;;
        --out) OUT_FILE="${2:-}"; shift ;;
        -h|--help) usage; exit 0 ;;
        -*) echo "unknown option: $1"; exit 1 ;;
        *) DEVICE="$1" ;;
    esac
    shift
done

case "$ROTATE" in
    auto|none|cw|ccw|180) ;;
    *) echo "✗ --rotate takes auto, none, cw, ccw or 180 (got '$ROTATE')"; exit 1 ;;
esac

# ==========================================
# pick the simulator
# ==========================================
# one line per booted sim: "    iPhone 17 Pro Max (<udid>) (Booted)". a name can match several
# devices across runtimes, so always match against the booted list — never hand a name to simctl,
# which would resolve it to a shut-down device and hang waiting for a screen.
BOOTED=$(xcrun simctl list devices booted | grep '(Booted)' || true)
if [ -n "$DEVICE" ]; then
    BOOTED=$(echo "$BOOTED" | grep -i -- "$DEVICE" || true)
fi

COUNT=$(echo "$BOOTED" | grep -c . || true)
if [ "$COUNT" -eq 0 ]; then
    if [ -n "$DEVICE" ]; then
        echo "✗ no booted simulator matching '$DEVICE':"
        xcrun simctl list devices booted | grep '(Booted)' || echo "    (none booted)"
    else
        echo "✗ no booted simulator — start one first (Xcode > Open Developer Tool > Simulator)"
    fi
    exit 1
elif [ "$COUNT" -gt 1 ]; then
    echo "✗ more than one simulator is booted — pass a name or UDID:"
    echo "$BOOTED"
    exit 1
fi

UDID=$(echo "$BOOTED" | grep -o '[0-9A-F-]\{36\}' | head -1)
NAME=$(echo "$BOOTED" | sed 's/^ *//;s/ (.*//')

# ==========================================
# capture
# ==========================================
if [ -z "$OUT_FILE" ]; then
    mkdir -p "$OUT_DIR"
    OUT_FILE="$OUT_DIR/$PREFIX-$(date +%Y%m%d-%H%M%S).png"

    # two captures inside the same second would otherwise overwrite each other
    N=2
    while [ -e "$OUT_FILE" ]; do
        OUT_FILE="${OUT_FILE%.png}-$N.png"
        N=$((N + 1))
    done
fi

# simctl is chatty about the display it picked — only show it if the capture fails
if ! ERR=$(xcrun simctl io "$UDID" screenshot "$OUT_FILE" 2>&1); then
    echo "$ERR"
    exit 1
fi

# ==========================================
# rotate to match the simulator window
# ==========================================
# simctl writes the native portrait framebuffer whatever way up the window is, so a
# landscape capture arrives lying on its side at portrait dimensions
ORIENTATION=""
if [ "$ROTATE" = "auto" ]; then
    # `defaults export` rather than reading the plist file: the file on disk lags
    # behind, cfprefsd holds the current value
    PREFS=$(mktemp -t simprefs)
    if defaults export com.apple.iphonesimulator - > "$PREFS" 2>/dev/null; then
        ORIENTATION=$(/usr/libexec/PlistBuddy \
            -c "Print :DevicePreferences:$UDID:SimulatorWindowOrientation" "$PREFS" 2>/dev/null || true)
    fi
    rm -f "$PREFS"
    # a device that has never been rotated has no key at all — that means portrait
    case "$ORIENTATION" in
        LandscapeLeft)      ROTATE="ccw" ;;
        LandscapeRight)     ROTATE="cw" ;;
        PortraitUpsideDown) ROTATE="180" ;;
        *)                  ROTATE="none" ;;
    esac
fi

if [ "$ROTATE" != "none" ]; then
    if ! command -v magick > /dev/null 2>&1; then
        echo "✗ rotating needs ImageMagick — brew install imagemagick, or pass --rotate none"
        exit 1
    fi
    case "$ROTATE" in
        cw)  DEGREES=90 ;;
        ccw) DEGREES=-90 ;;
        180) DEGREES=180 ;;
    esac
    magick "$OUT_FILE" -rotate "$DEGREES" -strip "$OUT_FILE.tmp" && mv "$OUT_FILE.tmp" "$OUT_FILE"
fi

SIZE=$(du -h "$OUT_FILE" | cut -f1 | tr -d ' ')
DIMS=$(sips -g pixelWidth -g pixelHeight "$OUT_FILE" 2>/dev/null | awk '/pixel/ {printf "%s%s", sep, $2; sep="x"}')
TURNED=""
[ "$ROTATE" != "none" ] && TURNED=", rotated $ROTATE${ORIENTATION:+ for $ORIENTATION}"
echo "✔ $NAME → $OUT_FILE (${DIMS:-?}, $SIZE$TURNED)"

if [ "$OPEN_FINDER" = true ]; then
    open -R "$OUT_FILE"
fi
