#!/bin/bash
# build + run the iOS app on a connected device. `-destination` needs the device name
# as Xcode shows it — run `xcrun xctrace list devices` to find yours.

set -e

cd "$(dirname "$0")/../iosApp"

DEVICE="${1:-}"
if [ -z "$DEVICE" ]; then
    echo "usage: $0 '<device name>'   (see: xcrun xctrace list devices)"
    exit 1
fi

xcodebuild -project iosApp.xcodeproj -scheme iosApp -destination "name=$DEVICE"
