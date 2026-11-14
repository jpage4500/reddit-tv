# paths.sh — sourced, never run. Prints a file's path as it would be typed from the directory the
# script was run in (ie: ../resources/screenshots/phone/found.png from scripts/), so a path in the
# output can be opened straight from the terminal.
#
# The caller sets RUN_DIR="$PWD" before it cd's to the repo root.

show() {  # show <path relative to the repo root, or absolute>
    local p=$1 rel
    [[ "$p" == /* ]] || p="$PWD/$p"
    rel=$(perl -MFile::Spec -e 'print File::Spec->abs2rel($ARGV[0], $ARGV[1])' "$p" "$RUN_DIR")
    # somewhere else entirely (ie: an OUT= in a temp directory) reads better whole
    if [[ "$rel" == ../../../* ]]; then echo "$p"; else echo "$rel"; fi
}
