#!/usr/bin/env bash
# generate-icons.sh — regenerates every platform launcher icon and the in-app
# drawables from the source SVGs in resources/images/. Idempotent; safe to re-run.
#
# SOLID BACKGROUND COLOR (kept in sync across three places — currently #FF4500):
#   - androidApp/src/main/res/values/ic_launcher_background.xml
#       <color name="ic_launcher_background"> — solid color shown behind the
#       adaptive foreground.
#   - composeApp/src/desktopMain/resources/Icon.icon/icon.json
#       "automatic-gradient" : "display-p3:R,G,B,1.0" — macOS Tahoe Icon
#       Composer's procedural gradient behind Image.png.
#   - BG_COLOR variable below (used by flatten_rgb for App Store / Play Store
#       icons that must have no alpha channel — their square corners get filled
#       with this color).
# When you change the icon, update BG_COLOR + TAHOE_GRADIENT here and edit the two
# files above.
#
# ANDROID SIDELOAD FLAVORS ('full' + 'beta') each use a DISTINCT background color
# (BG_COLOR_FULL #BA7929, BG_COLOR_BETA #2E7D32) so every install is visually
# distinguishable from the Play Store build — and from each other — on the home
# screen. 'full' is the auto-updating sideload release; 'beta' is the local-dev
# build (see androidApp/build.gradle.kts). Each flavor overrides:
#   - androidApp/src/<flavor>/res/values/ic_launcher_background.xml — adaptive bg
#     color (the API 26+ path; foreground is inherited from src/main, unchanged).
#   - androidApp/src/<flavor>/res/mipmap-*/ic_launcher{,_round}.webp — legacy square
#     + round icons (API 24-25 / legacy-launcher fallback), generated below by
#     compositing the transparent foreground over a solid flavor-color square.
# Keep BG_COLOR_FULL / BG_COLOR_BETA here in sync with each flavor's
# ic_launcher_background.xml.
#
# SOURCES (do not delete):
#   - resources/images/logo.svg            — full icon (squared gradient bg +
#                                            content). Source for legacy Android,
#                                            iOS, Play Store, macOS .icns,
#                                            Windows .ico, Linux PNG. Squared on
#                                            purpose — iOS and adaptive-Android
#                                            apply their own corner mask. macOS
#                                            and Linux do NOT, so the .icns PNGs
#                                            and Linux icon.png get an inset
#                                            squircle alpha mask post-process (see
#                                            mask_squircle_inset below).

#   - resources/images/logo-foreground.svg — the same artwork minus the background
#                                            rect. Source for the Android adaptive
#                                            foreground, the per-flavor legacy
#                                            launcher icons, the Android TV banner,
#                                            the Tahoe Image.png and the in-app
#                                            logo_foreground.png. Its ink reaches
#                                            0.358 x the canvas width from centre
#                                            (the antenna tips are the
#                                            far points), which is what the adaptive
#                                            foreground — and only it — is inset by
#                                            ADAPTIVE_INSET below to land inside
#                                            Android's 66dp circular safe zone.
#
# NOTE: both SVGs are pure geometry — rects, paths and a gradient, no <text> element and
# no embedded bitmap — so rsvg-convert renders identical output on a dev Mac and on a CI
# runner whatever fonts it has. Edit the SVGs directly and re-run; keep the two in step
# (logo.svg squared + opaque, logo-foreground.svg the same artwork content-only +
# transparent). The background-only layer the TV banner needs is cut from logo.svg (its
# <defs> + background rect), so there's no third SVG to keep in step.
#
# Required tools (Homebrew names in parens):
#   rsvg-convert (librsvg), cwebp (webp), iconutil (built-in macOS), magick (imagemagick)

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

# ─── usage ───────────────────────────────────────────────────────────────────
usage() {
  cat >&2 <<EOF
Usage: $0

Regenerates every platform launcher icon and the in-app drawables from the
source SVGs in resources/images/:

  logo.svg             full icon (squared background + content)
  logo-foreground.svg  transparent foreground for the Android adaptive icon

Replace those two files with your own artwork and re-run. Idempotent.
EOF
  exit 1
}

if [[ $# -gt 0 ]]; then
  echo "ERROR: too many arguments (this script takes none)" >&2
  usage
fi

SRC_FULL="resources/images/logo.svg"
SRC_FG="resources/images/logo-foreground.svg"
ADAPTIVE_INSET=850          # per-mille: extra inset applied to the Android adaptive
                            # foreground only, so SRC_FG's artwork lands inside the
                            # 66dp circular safe zone (see ANDROID note below). SRC_FG
                            # reaches 0.358 x its canvas width from centre, and
                            # 33 / (108 x 0.358) = 0.854 — so 850 is the ceiling for
                            # this artwork. Re-derive it if the artwork changes: the
                            # screen's outer corners set this number. to measure:
                            #   rsvg-convert -w 1024 -h 1024 "$SRC_FG" -o fg.png, then the
                            #   farthest opaque pixel from (511.5, 511.5), over 1024
BG_COLOR="#FF4500"          # solid fill for alpha-stripped icons; see header note
LAUNCHER="ic_launcher"      # android launcher resource base name (see ANDROID note in header)
DRAWABLE_SUFFIX=""          # in-app compose drawable suffix (see IN-APP note in header)
DESKTOP_SUFFIX=""           # desktop icns/ico/png + jDeploy installer icon suffix
IOS_SUFFIX=""               # xcassets appiconset suffix (ASSETCATALOG_COMPILER_APPICON_NAME)
TAHOE_GRADIENT="display-p3:1.00000,0.27059,0.00000,1.00000"  # Icon.icon bg (see icon.json)
GEN_PLAYSTORE=0             # GitHub-only: no Play listing
GEN_README_LOGO=1           # repo-root logo.png is this repo's README image
BG_COLOR_FULL="#FF4500"  # 'full' (sideload release) flavor launcher background; see header note
BG_COLOR_BETA="#2E7D32"  # 'beta' (local dev) flavor launcher background; see header note

echo "▶ Generating icons (sources: $SRC_FULL)"

# ─── tool validation ────────────────────────────────────────────────────────
missing=()
for cmd in rsvg-convert cwebp iconutil magick; do
  command -v "$cmd" >/dev/null 2>&1 || missing+=("$cmd")
done
if (( ${#missing[@]} > 0 )); then
  echo "ERROR: missing required tools: ${missing[*]}" >&2
  echo "Install with: brew install librsvg webp imagemagick" >&2
  echo "(iconutil ships with macOS; if missing, install Xcode Command Line Tools.)" >&2
  exit 1
fi

for src in "$SRC_FULL" "$SRC_FG"; do
  [[ -f "$src" ]] || { echo "ERROR: source SVG not found: $src" >&2; exit 1; }
done

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# ─── helpers ────────────────────────────────────────────────────────────────
svg_to_png() {  # svg_to_png <src.svg> <size> <out.png>
  rsvg-convert -w "$2" -h "$2" "$1" -o "$3"
}

png_to_webp() {  # png_to_webp <in.png> <out.webp>
  cwebp -q 75 -mt -quiet "$1" -o "$2"
}

# magick stamps a creation date into every PNG it writes, which makes otherwise
# identical re-runs produce different bytes and show up as churn in git. Every magick
# PNG write below passes NO_DATE so this script's output is reproducible.
NO_DATE=(-define png:exclude-chunks=date,time)

# circular alpha mask: fully opaque inside the inscribed circle, transparent outside.
mask_circle() {  # mask_circle <in.png> <out.png>
  magick "$1" \
    \( +clone -alpha extract -threshold -1 \
       -fill black -draw "color 0,0 reset" \
       -fill white -draw "circle %[fx:w/2-0.5],%[fx:h/2-0.5] %[fx:w/2-0.5],0" \) \
    -alpha off -compose CopyOpacity -composite "${NO_DATE[@]}" "$2"
}

# squircle (rounded-rect) alpha mask: opaque inside an Apple-style rounded
# rectangle, transparent outside. Used for macOS .icns and Linux icon.png where
# the OS does NOT apply its own corner mask. Radius is 22.37% of the canvas
# (the conventional roundrectangle approximation of Apple's superellipse).
mask_squircle() {  # mask_squircle <in.png> <out.png>
  magick "$1" \
    \( +clone -alpha extract -threshold -1 \
       -fill black -draw "color 0,0 reset" \
       -fill white -draw "roundrectangle 0,0 %[fx:w-1],%[fx:h-1] %[fx:w*0.2237],%[fx:h*0.2237]" \) \
    -alpha off -compose CopyOpacity -composite "${NO_DATE[@]}" "$2"
}

# squircle mask WITH the macOS "icon grid" inset: the rounded body fills only
# ~80% of the canvas (824 of 1024) centred on transparent margin, so the tile
# matches the size of system icons in the Dock / ⌘-Tab switcher. macOS (and most
# Linux desktops) do NOT inset app icons themselves — unlike iOS/adaptive-Android,
# which is why only the desktop icons need the margin baked in. Corner radius stays
# 22.37% of the *body* (Apple-correct), not of the full canvas.
mask_squircle_inset() {  # mask_squircle_inset <in.png> <out.png>
  local size body
  size="$(magick identify -format '%w' "$1")"
  body=$(( size * 824 / 1024 ))
  magick "$1" -resize "${body}x${body}" "$TMP/inset-src.png"
  mask_squircle "$TMP/inset-src.png" "$TMP/inset-body.png"
  magick -size "${size}x${size}" xc:none "$TMP/inset-body.png" -gravity center -composite "${NO_DATE[@]}" "$2"
}

flatten_rgb() {  # flatten_rgb <in.png> <out.png>  — strips alpha onto BG_COLOR
  magick "$1" -background "$BG_COLOR" -alpha remove -alpha off "${NO_DATE[@]}" "$2"
}

# ─── Android: legacy square <LAUNCHER>.webp ─────────────────────────────────
# consumed by AndroidManifest.xml's android:icon="${appIcon}" placeholder, which
# referenced from AndroidManifest.xml as @mipmap/ic_launcher / @mipmap/ic_launcher_round
# per brand (pre-Oreo launchers + the fallback when the adaptive icon can't render).
echo "▶ Android ${LAUNCHER}.webp (legacy square)"
for entry in "mdpi:48" "hdpi:72" "xhdpi:96" "xxhdpi:144" "xxxhdpi:192"; do
  density="${entry%%:*}"; px="${entry##*:}"
  svg_to_png "$SRC_FULL" "$px" "$TMP/sq-$density.png"
  png_to_webp "$TMP/sq-$density.png" "androidApp/src/main/res/mipmap-${density}/${LAUNCHER}.webp"
done

# ─── Android: legacy round <LAUNCHER>_round.webp ────────────────────────────
# consumed by AndroidManifest.xml's android:roundIcon="${appRoundIcon}" placeholder.
echo "▶ Android ${LAUNCHER}_round.webp (legacy round)"
for entry in "mdpi:48" "hdpi:72" "xhdpi:96" "xxhdpi:144" "xxxhdpi:192"; do
  density="${entry%%:*}"; px="${entry##*:}"
  svg_to_png "$SRC_FULL" "$px" "$TMP/round-src-$density.png"
  mask_circle "$TMP/round-src-$density.png" "$TMP/round-$density.png"
  png_to_webp "$TMP/round-$density.png" "androidApp/src/main/res/mipmap-${density}/${LAUNCHER}_round.webp"
done

# ─── Android: adaptive foreground <LAUNCHER>_foreground.webp ────────────────
# consumed by mipmap-anydpi-v26/<LAUNCHER>.xml and <LAUNCHER>_round.xml as the
# <foreground> layer. Sits on top of @color/ic_launcher_background — the background is
# deliberately shared across brands, since that color encodes the *distribution channel*
# (play / full / beta) while the foreground glyph encodes the *brand*. Sized to 108dp at
# each density (Android's adaptive-icon canvas).
#
# Only the middle 72dp of that 108dp canvas survives the launcher mask, and only a
# centred 66dp CIRCLE is guaranteed (Pixel launchers mask icons to a circle). SRC_FG
# fills its own canvas — right for every consumer that is never mask-cropped — so for
# the adaptive icon it is rendered at ADAPTIVE_INSET of the canvas and centred on
# transparent padding instead. Raise ADAPTIVE_INSET only if the artwork's ink moves
# inward; the current value is the ceiling for the current artwork.
echo "▶ Android ${LAUNCHER}_foreground.webp (adaptive, inset to ${ADAPTIVE_INSET} per-mille)"
for entry in "mdpi:108" "hdpi:162" "xhdpi:216" "xxhdpi:324" "xxxhdpi:432"; do
  density="${entry%%:*}"; px="${entry##*:}"
  inner=$(( px * ADAPTIVE_INSET / 1000 ))
  svg_to_png "$SRC_FG" "$inner" "$TMP/fg-inner-$density.png"
  magick -size "${px}x${px}" xc:none \
    "$TMP/fg-inner-$density.png" -gravity center -composite "${NO_DATE[@]}" "$TMP/fg-$density.png"
  png_to_webp "$TMP/fg-$density.png" "androidApp/src/main/res/mipmap-${density}/${LAUNCHER}_foreground.webp"
done

# ─── Android sideload flavors ('full', 'beta'): legacy square + round icons ──
# each sideload flavor gets a distinct launcher background (BG_COLOR_FULL / BG_COLOR_BETA)
# so it's visually distinguishable from the Play Store build and from each other. The
# adaptive icon (API 26+) is handled by androidApp/src/<flavor>/res/values/ic_launcher_background.xml
# overriding @color/ic_launcher_background — the foreground webp is inherited from src/main
# and not regenerated. These legacy webps cover the API 24-25 / legacy-launcher fallback:
# the transparent foreground composited onto a solid flavor-color square (square for
# ic_launcher, circle-masked for ic_launcher_round), matching the adaptive look.
for flavor_entry in "full:$BG_COLOR_FULL" "beta:$BG_COLOR_BETA"; do
  flavor="${flavor_entry%%:*}"; flavor_bg="${flavor_entry##*:}"
  echo "▶ Android '$flavor' flavor ${LAUNCHER}{,_round}.webp (sideload bg $flavor_bg)"
  for entry in "mdpi:48" "hdpi:72" "xhdpi:96" "xxhdpi:144" "xxxhdpi:192"; do
    density="${entry%%:*}"; px="${entry##*:}"
    flavor_dir="androidApp/src/${flavor}/res/mipmap-${density}"
    mkdir -p "$flavor_dir"
    # transparent foreground (66% safe zone) over a solid flavor-color square
    svg_to_png "$SRC_FG" "$px" "$TMP/${flavor}-fg-$density.png"
    magick -size "${px}x${px}" "xc:$flavor_bg" \
      "$TMP/${flavor}-fg-$density.png" -gravity center -composite \
      "${NO_DATE[@]}" "$TMP/${flavor}-sq-$density.png"
    png_to_webp "$TMP/${flavor}-sq-$density.png" "$flavor_dir/${LAUNCHER}.webp"
    mask_circle "$TMP/${flavor}-sq-$density.png" "$TMP/${flavor}-round-$density.png"
    png_to_webp "$TMP/${flavor}-round-$density.png" "$flavor_dir/${LAUNCHER}_round.webp"
  done
done

# ─── Android TV: leanback banner ────────────────────────────────────────────
# consumed by AndroidManifest.xml's android:banner="@drawable/banner" — the tile on an Android
# TV / Google TV home row. 320×180 at xhdpi (Android's only required density for banners). the
# foreground over logo.svg's own background, stretched to 16:9 — which keeps the radial
# highlight where logo.svg puts it, relative to the tile. the background layer is cut from
# logo.svg (everything up to and including its full-canvas <rect>), not a separate SVG.
echo "▶ Android TV banner.png (320×180)"
awk '{ print } /<rect width="1024" height="1024"/ { print "</svg>"; exit }' "$SRC_FULL" > "$TMP/banner-bg.svg"
grep -q '</svg>' "$TMP/banner-bg.svg" || { echo "ERROR: no full-canvas background <rect> in $SRC_FULL" >&2; exit 1; }
svg_to_png "$TMP/banner-bg.svg" 320 "$TMP/banner-bg.png"
# the foreground at 200px: its TV is ~2/3 of that wide, filling the tile the way the launcher
# icon fills its circle
svg_to_png "$SRC_FG" 200 "$TMP/banner-fg.png"
# the composite gains cHRM + bKGD chunks with no gAMA/sRGB beside them, and Fire OS 6's launcher
# can't draw a banner like that — the tile comes up as a broken-image placeholder. excluded here
# (on top of NO_DATE's date/time), since one png:exclude-chunks define replaces the other
mkdir -p "androidApp/src/main/res/drawable-xhdpi"
magick "$TMP/banner-bg.png" -resize '320x180!' \
  "$TMP/banner-fg.png" -gravity center -composite \
  -alpha off -define png:exclude-chunks=date,time,cHRM,bKGD "androidApp/src/main/res/drawable-xhdpi/banner.png"

# ─── Android: Play Store listing icon ───────────────────────────────────────
# Play Console requires 512×512 PNG, 32-bit, no alpha. Three copies exist, all
# updated together: the androidApp/ master, the composeApp/ mirror, and the
# play-publisher listing slot (consumed by :androidApp:publishGoogleListing).
if (( GEN_PLAYSTORE )); then
  echo "▶ Android ic_launcher-playstore.png (Play Store, 512, no alpha)"
  svg_to_png "$SRC_FULL" 512 "$TMP/play.png"
  flatten_rgb "$TMP/play.png" "androidApp/src/main/ic_launcher-playstore.png"
  cp "androidApp/src/main/ic_launcher-playstore.png" "composeApp/src/androidMain/ic_launcher-playstore.png"
  mkdir -p "androidApp/src/main/play/listings/en-US/graphics/icon"
  cp "androidApp/src/main/ic_launcher-playstore.png" "androidApp/src/main/play/listings/en-US/graphics/icon/1.png"

  # the Play listing's TV banner: 1280×720, no alpha. the same composition as the leanback banner
  # above, at 4x, rather than that one scaled up
  echo "▶ Android TV Play banner (1280×720, no alpha)"
  svg_to_png "$TMP/banner-bg.svg" 1280 "$TMP/play-banner-bg.png"
  svg_to_png "$SRC_FG" 800 "$TMP/play-banner-fg.png"
  mkdir -p "androidApp/src/main/play/listings/en-US/graphics/tv-banner"
  magick "$TMP/play-banner-bg.png" -resize '1280x720!' \
    "$TMP/play-banner-fg.png" -gravity center -composite \
    -alpha off "${NO_DATE[@]}" "androidApp/src/main/play/listings/en-US/graphics/tv-banner/1.png"
else
  echo "▶ Android ic_launcher-playstore.png — SKIPPED"
fi

# ─── iOS: main AppIcon set ──────────────────────────────────────────────────
# filename → pixel-size table derived from iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json
# (which is authoritative — Xcode regenerates it. If sizes drift, update this table.)
# Marketing 1024 must be flattened (App Store rejects alpha).
#
# The set an iOS build uses is the ASSETCATALOG_COMPILER_APPICON_NAME build setting — see
# xcodebuild command line per brand. Image *filenames* inside the set are identical between brands
# (Contents.json is copied verbatim), so only the directory name differs.
echo "▶ iOS AppIcon${IOS_SUFFIX}.appiconset"
IOS_DIR="iosApp/Assets.xcassets/AppIcon${IOS_SUFFIX}.appiconset"
seed_appiconset() {  # seed_appiconset <target-dir> <source-dir>
  # a missing set starts as a copy of the committed Contents.json so the slot table,
  # idioms and appearance entries stay identical — only the PNGs differ.
  if [[ ! -f "$1/Contents.json" ]]; then
    mkdir -p "$1"
    cp "$2/Contents.json" "$1/Contents.json"
    echo "  seeded $1/Contents.json from $2"
  fi
}
seed_appiconset "$IOS_DIR" "iosApp/Assets.xcassets/AppIcon.appiconset"
declare -a IOS_ICONS=(
  "AppIcon-20@2x.png:40"
  "AppIcon-20@2x~ipad.png:40"
  "AppIcon-20@3x.png:60"
  "AppIcon-20~ipad.png:20"
  "AppIcon-29.png:29"
  "AppIcon-29@2x.png:58"
  "AppIcon-29@2x~ipad.png:58"
  "AppIcon-29@3x.png:87"
  "AppIcon-29~ipad.png:29"
  "AppIcon-40@2x.png:80"
  "AppIcon-40@2x~ipad.png:80"
  "AppIcon-40@3x.png:120"
  "AppIcon-40~ipad.png:40"
  "AppIcon-60@2x~car.png:120"
  "AppIcon-60@3x~car.png:180"
  "AppIcon-83.5@2x~ipad.png:167"
  "AppIcon@2x.png:120"
  "AppIcon@2x~ipad.png:152"
  "AppIcon@3x.png:180"
  "AppIcon~ipad.png:76"
)
for entry in "${IOS_ICONS[@]}"; do
  name="${entry%%:*}"; px="${entry##*:}"
  svg_to_png "$SRC_FULL" "$px" "$IOS_DIR/$name"
done
# marketing icon: 1024, no alpha
svg_to_png "$SRC_FULL" 1024 "$TMP/ios-marketing.png"
flatten_rgb "$TMP/ios-marketing.png" "$IOS_DIR/AppIcon~ios-marketing.png"

# ─── iOS: (widget extension removed — nothing to generate here) ──────────────
# ─── In-app drawables (commonMain composeResources) ────────────────────────
# consumed via Branding.logo / Branding.logoRound (commonMain/data/Branding.kt):
#   logo_round.png  logo_foreground.png  logo_mark.png
# Call sites: Branding.logo / Branding.logoRound, desktopMain/main.kt window + tray icon.
# logo_round*.png is
# circular-masked for the desktop window + tray icon. logo_foreground*.png is the only one
# rendered from SRC_FG: artwork with NO background at all, for drawing the mark straight onto
# an app surface (Branding.logo — the drawer header, and the TV list's icon for our app).
echo "▶ In-app drawables (commonMain, suffix '${DRAWABLE_SUFFIX:-none}')"
DRAWABLE_DIR="composeApp/src/commonMain/composeResources/drawable"
svg_to_png "$SRC_FULL" 512 "$TMP/logo-round-src.png"
mask_circle "$TMP/logo-round-src.png" "$DRAWABLE_DIR/logo_round${DRAWABLE_SUFFIX}.png"
svg_to_png "$SRC_FG" 512 "$DRAWABLE_DIR/logo_foreground${DRAWABLE_SUFFIX}.png"
# logo_mark*.png: the same artwork cropped to its ink and padded back to a square, so its width
# fills 92% of the canvas — what the in-app icons fill — and it sits at their
# size beside them (Branding.mark, the TV list). logo_foreground keeps the launcher margins
svg_to_png "$SRC_FG" 1024 "$TMP/mark-src.png"
magick "$TMP/mark-src.png" -trim +repage "$TMP/mark-trim.png"
mark_side=$(( $(magick identify -format '%w' "$TMP/mark-trim.png") * 100 / 92 ))
magick "$TMP/mark-trim.png" -background none -gravity center -extent "${mark_side}x${mark_side}" \
  -resize 512x512 "${NO_DATE[@]}" "$DRAWABLE_DIR/logo_mark${DRAWABLE_SUFFIX}.png"

# ─── Desktop macOS .icns ────────────────────────────────────────────────────
# consumed by composeApp/build.gradle.kts > nativeDistributions > macOS > iconFile, which picks
# icon.icns. iconutil expects a directory named *.iconset/
# with this exact filename scheme.
echo "▶ Desktop macOS icon${DESKTOP_SUFFIX}.icns"
ICONSET="$TMP/Icon.iconset"
mkdir -p "$ICONSET"
declare -a MAC_SIZES=(
  "icon_16x16.png:16"
  "icon_16x16@2x.png:32"
  "icon_32x32.png:32"
  "icon_32x32@2x.png:64"
  "icon_128x128.png:128"
  "icon_128x128@2x.png:256"
  "icon_256x256.png:256"
  "icon_256x256@2x.png:512"
  "icon_512x512.png:512"
  "icon_512x512@2x.png:1024"
)
for entry in "${MAC_SIZES[@]}"; do
  name="${entry%%:*}"; px="${entry##*:}"
  svg_to_png "$SRC_FULL" "$px" "$TMP/mac-src-$name"
  mask_squircle_inset "$TMP/mac-src-$name" "$ICONSET/$name"
done
iconutil --convert icns --output "composeApp/src/desktopMain/resources/icon${DESKTOP_SUFFIX}.icns" "$ICONSET"

# ─── Desktop Windows .ico ───────────────────────────────────────────────────
# consumed by composeApp/build.gradle.kts > nativeDistributions > windows > iconFile.
echo "▶ Desktop Windows icon${DESKTOP_SUFFIX}.ico"
for px in 16 32 48 64 128 256; do
  svg_to_png "$SRC_FULL" "$px" "$TMP/win-$px.png"
done
magick \
  "$TMP/win-16.png" "$TMP/win-32.png" "$TMP/win-48.png" \
  "$TMP/win-64.png" "$TMP/win-128.png" "$TMP/win-256.png" \
  "composeApp/src/desktopMain/resources/icon${DESKTOP_SUFFIX}.ico"

# ─── Desktop Linux icon.png ─────────────────────────────────────────────────
# consumed by composeApp/build.gradle.kts > nativeDistributions > linux > iconFile.
# NOTE: gradle reference is lowercase "icon.png"; the historical file was
# "Icon.png" (capital I), which works on macOS's case-insensitive FS but breaks
# Linux/CI. We write lowercase; remove the old uppercase file by hand once:
#   git rm composeApp/src/desktopMain/resources/Icon.png
echo "▶ Desktop Linux icon${DESKTOP_SUFFIX}.png"
svg_to_png "$SRC_FULL" 512 "$TMP/linux.png"
mask_squircle_inset "$TMP/linux.png" "composeApp/src/desktopMain/resources/icon${DESKTOP_SUFFIX}.png"

# ─── Repo-root logo.png / icon.png (jDeploy installer + README branding) ────
# logo.png (192, circle mask) is THIS repo's README / branding image.
# icon.png (1024, full-bleed squircle mask) is the jDeploy desktop installer icon — jDeploy has no config key
# for the icon path, it always reads ./icon.png next to package.json. The jdeploy-bundle/ copy
# ships inside the published npm bundle.
if (( GEN_README_LOGO )); then
  echo "▶ Repo-root logo.png (README branding)"
  svg_to_png "$SRC_FULL" 192 "$TMP/root-logo.png"
  mask_circle "$TMP/root-logo.png" "logo.png"
else
  echo "▶ Repo-root logo.png — SKIPPED"
fi
echo "▶ Repo-root icon${DESKTOP_SUFFIX}.png (jDeploy installer icon)"
svg_to_png "$SRC_FULL" 1024 "$TMP/root-icon.png"
mask_squircle "$TMP/root-icon.png" "icon${DESKTOP_SUFFIX}.png"
# jdeploy-bundle/ is gitignored (jDeploy regenerates it), so it's absent in a fresh checkout —
# create it or this cp aborts the whole script under `set -e`. mirror only the brand jDeploy will
# actually read, i.e. the unsuffixed icon.png, and only when generating that brand.
mkdir -p jdeploy-bundle
[[ -z "$DESKTOP_SUFFIX" ]] && cp "icon.png" "jdeploy-bundle/icon.png"

# ─── Desktop macOS Tahoe Icon Composer foreground ───────────────────────────
# consumed by Icon<suffix>.icon/icon.json, which applies its own automatic-gradient bg + shadow +
# translucency. Image.png must be a transparent foreground at high resolution (1024 works well).
# The bundle is Icon.icon; its icon.json carries the gradient ($TAHOE_GRADIENT).
# (Compose Desktop's nativeDistributions currently bundles icon.icns, not this bundle — but it's
# kept in sync for future use / direct macOS-app builds.)
TAHOE_DIR="composeApp/src/desktopMain/resources/Icon${DESKTOP_SUFFIX}.icon"
echo "▶ Desktop macOS Icon${DESKTOP_SUFFIX}.icon/Assets/Image.png (Tahoe foreground)"
mkdir -p "$TAHOE_DIR/Assets"
if [[ ! -f "$TAHOE_DIR/icon.json" ]]; then
  # seed a missing bundle from the committed one so layer/shadow settings stay identical
  cp "composeApp/src/desktopMain/resources/Icon.icon/icon.json" "$TAHOE_DIR/icon.json"
fi
python3 - "$TAHOE_DIR/icon.json" "$TAHOE_GRADIENT" <<'PYEOF'
import json, sys
path, gradient = sys.argv[1], sys.argv[2]
with open(path) as f:
    data = json.load(f)
if data.get("fill", {}).get("automatic-gradient") == gradient:
    # already correct — leave the file byte-for-byte alone so Icon Composer's own formatting
    # (which differs from json.dump's) doesn't show up as churn on every run
    sys.exit(0)
data.setdefault("fill", {})["automatic-gradient"] = gradient
with open(path, "w") as f:
    json.dump(data, f, indent=2)
    f.write("\n")
print(f"  updated automatic-gradient → {gradient}")
PYEOF
svg_to_png "$SRC_FG" 1024 "$TAHOE_DIR/Assets/Image.png"

# ─── done ───────────────────────────────────────────────────────────────────
echo ""
echo "Done. Review with:  git status && git diff --stat"
# the casing-conflict cleanup (Icon.png → icon.png) only needs to run once;
# on macOS's case-insensitive FS the inode is shared, so a manual:
#     git mv composeApp/src/desktopMain/resources/Icon.png composeApp/src/desktopMain/resources/icon.png
# is required to record the rename in git. After that, this script writes
# directly to icon.png and no further cleanup is needed.
