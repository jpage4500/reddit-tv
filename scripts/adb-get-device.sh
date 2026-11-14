#!/bin/bash

# short id used to tell two identically-named devices apart
function deviceSerial() {
    local id="$1"
    case "$id" in
        # mDNS: "adb-R5CT30XXXX-abcdef._adb-tls-connect._tcp" -> "R5CT30XXXX"
        adb-*._tcp*) id="${id#adb-}"; id="${id%%.*}"; echo "${id%-*}" ;;
        # IP:PORT -> IP
        *:*) echo "${id%:*}" ;;
        # usb serial
        *) echo "$id" ;;
    esac
}

# fuller id, used when two connections to one device share a serial
function deviceLongId() {
    local id="$1"
    case "$id" in
        # mDNS: drop the "adb-" prefix and the "._adb-tls-connect._tcp" suffix
        adb-*._tcp*) id="${id#adb-}"; echo "${id%%.*}" ;;
        *) echo "$id" ;;
    esac
}

# remember last selected device (~/.pm/ is outside the repo, so it can't be committed)
LAST_DEVICE_FILE="$HOME/.pm/last-device"

# device names set on the device itself (Settings > About > Device name), one "serial<TAB>name" per line
DEVICE_NAMES_FILE="$HOME/.pm/device-names"

# ask a device for its user-set name; prints nothing if it has none
# NOTE: stdin from /dev/null - adb shell would otherwise eat the device list selectDevice is reading
function fetchDeviceName() {
    local id="$1"
    local name
    name=$(adb -s "$id" shell 'n=$(settings get global device_name); case "$n" in ""|null) n=$(settings get secure bluetooth_name) ;; esac; echo "$n"' </dev/null 2>/dev/null | tr -d '\r' | head -1)
    case "$name" in
        ""|null) ;;
        *) echo "$name" ;;
    esac
}

function cachedDeviceName() {
    local serial="$1"
    [ -f "$DEVICE_NAMES_FILE" ] || return 0
    awk -F'\t' -v s="$serial" '$1 == s { print $2; exit }' "$DEVICE_NAMES_FILE" 2>/dev/null
}

function saveDeviceName() {
    local serial="$1" name="$2"
    [ -n "$serial" ] && [ -n "$name" ] || return 0
    mkdir -p "$(dirname "$DEVICE_NAMES_FILE")" 2>/dev/null || return 0
    local tmp="$DEVICE_NAMES_FILE.$$"
    { [ -f "$DEVICE_NAMES_FILE" ] && awk -F'\t' -v s="$serial" '$1 != s' "$DEVICE_NAMES_FILE"; printf '%s\t%s\n' "$serial" "$name"; } > "$tmp" 2>/dev/null \
        && mv "$tmp" "$DEVICE_NAMES_FILE" 2>/dev/null
    return 0
}

# the device's own name, from the cache or (on a miss) from the device; empty if unknown
function deviceName() {
    local id="$1" serial="$2" offline="$3"
    local name
    name=$(cachedDeviceName "$serial")
    if [ -z "$name" ] && [ -z "$offline" ]; then
        name=$(fetchDeviceName "$id")
        saveDeviceName "$serial" "$name"
    fi
    echo "$name"
}

# re-read the chosen device's name in the background, so a rename shows up next time
function refreshDeviceName() {
    local id="$1" serial="$2"
    ( name=$(fetchDeviceName "$id"); saveDeviceName "$serial" "$name" ) >/dev/null 2>&1 &
}

# "Name - RAWMODEL" when the device has a name, else just the raw model
function displayName() {
    local name="$1" model="$2"
    if [ -n "$name" ]; then
        echo "$name - $model"
    else
        echo "$model"
    fi
}

function saveLastDevice() {
    mkdir -p "$(dirname "$LAST_DEVICE_FILE")" 2>/dev/null || return 0
    echo "$1" > "$LAST_DEVICE_FILE" 2>/dev/null
    return 0
}

# -x: add an 'x' choice that exits without selecting (returns 2)
function selectDevice() {
    local allowExit=""
    if [[ "$1" == "-x" ]]; then allowExit="1"; fi

    # Use 'adb devices -l' to get extra details (model, transport_id, etc.)
    # NOTE: offline devices are listed too (marked, but not selectable) - a wireless device that
    # dropped off is easier to spot than one that's just missing
    RAW_DEVICES=$(adb devices -l | grep -v "^List" | grep -E "[[:space:]](device|offline)([[:space:]]|$)")

    if [[ -z "$RAW_DEVICES" ]]; then
        echo "no devices!"
        return 1
    fi

    # Initialize arrays to store parsed device properties
    ids=()
    models=()
    friendly=()
    serials=()
    transports=()
    wireless_flags=()
    offline_flags=()

    # Parse each connected device
    while read -r line; do
        if [[ -z "$line" ]]; then continue; fi

        # Extract ID (everything before the state column, minus adb's column padding)
        # NOTE: not field 1 - an mDNS serial can contain a space: "adb-XXX-YYY (2)._adb-tls-connect._tcp"
        id=$(echo "$line" | sed -E 's/[[:space:]]+(device|offline)([[:space:]]+.*)?$//')

        offline_flag=""
        if echo "$line" | grep -qE "[[:space:]]offline([[:space:]]|$)"; then
            offline_flag=" (OFFLINE)"
        fi

        # Extract model (looks for "model:XYZ" and grabs the XYZ)
        model=$(echo "$line" | grep -o 'model:[^ ]*' | cut -d: -f2)
        if [[ -z "$model" ]]; then model="Unknown"; fi

        # Extract transport_id (looks for "transport_id:XYZ" and grabs the XYZ)
        transport=$(echo "$line" | grep -o 'transport_id:[^ ]*' | cut -d: -f2)

        # Determine if wireless (ID contains ":" for IP:PORT or "._tcp" for mDNS)
        wireless_flag=""
        if [[ "$id" == *":"* || "$id" == *"._tcp"* ]]; then
            wireless_flag=" (wireless)"
        fi

        serial=$(deviceSerial "$id")

        ids+=("$id")
        models+=("$model")
        friendly+=("$(displayName "$(deviceName "$id" "$serial" "$offline_flag")" "$model")")
        serials+=("$serial")
        transports+=("$transport")
        wireless_flags+=("$wireless_flag")
        offline_flags+=("$offline_flag")
    done <<< "$RAW_DEVICES"

    NUM_DEVICES=${#ids[@]}

    # Handle single device case
    if [ $NUM_DEVICES -eq 1 ] && [ -z "$allowExit" ] && [ -n "${offline_flags[0]}" ]; then
        echo "only device is offline: ${friendly[0]}${wireless_flags[0]} (${ids[0]})"
        return 1
    elif [ $NUM_DEVICES -eq 1 ] && [ -z "$allowExit" ]; then
        export ADB_DEVICE="${ids[0]}"
        saveLastDevice "$ADB_DEVICE"
        refreshDeviceName "$ADB_DEVICE" "${serials[0]}"
        echo "using device: ${friendly[0]} (${transports[0]}) ($ADB_DEVICE)"
        return 0
    fi

    # the last device used, if it's still attached
    local lastDevice=""
    if [ -f "$LAST_DEVICE_FILE" ]; then
        lastDevice=$(head -1 "$LAST_DEVICE_FILE" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')
    fi
    local defaultIndex=-1
    if [ -n "$lastDevice" ]; then
        for i in "${!ids[@]}"; do
            if [ "${ids[$i]}" = "$lastDevice" ] && [ -z "${offline_flags[$i]}" ]; then defaultIndex=$i; fi
        done
    fi

    # Build the display list (model name + wireless flag; transport id omitted)
    displays=()
    for i in "${!models[@]}"; do
        displays+=("${friendly[$i]}${wireless_flags[$i]}")
    done

    # names that aren't unique on their own get the serial appended
    local base=("${displays[@]}")
    local i j dupe sameSerial
    for i in "${!base[@]}"; do
        dupe=""
        sameSerial=""
        for j in "${!base[@]}"; do
            if [ "$j" = "$i" ] || [ "${base[$j]}" != "${base[$i]}" ]; then continue; fi
            dupe="1"
            if [ "${serials[$j]}" = "${serials[$i]}" ]; then sameSerial="1"; fi
        done
        if [ -n "$dupe" ]; then
            # two connections to one device share a serial - fall back to the fuller id
            if [ -n "$sameSerial" ]; then
                displays[$i]="${base[$i]} [$(deviceLongId "${ids[$i]}")]"
            else
                displays[$i]="${base[$i]} [${serials[$i]}]"
            fi
        fi
    done

    echo "-------------"
    echo "select device"
    echo "-------------"
    for i in "${!displays[@]}"; do
        if [ $i -eq $defaultIndex ]; then
            echo "$((i + 1))) ${displays[$i]}${offline_flags[$i]}   <- last used"
        else
            echo "$((i + 1))) ${displays[$i]}${offline_flags[$i]}"
        fi
    done
    if [ -n "$allowExit" ]; then echo "x) exit"; fi

    # NOTE: a plain read, not 'select' - select can't make Enter mean "the default"
    local promptText="Select device number: "
    if [ $defaultIndex -ge 0 ]; then promptText="Select device number [$((defaultIndex + 1))]: "; fi
    local answer=""
    printf "%s" "$promptText"
    # end of input is 'x' too, or a caller looping on -x would pick the default forever
    if ! read -r answer && [ -n "$allowExit" ]; then answer="x"; fi

    if [ -n "$allowExit" ] && [ "$answer" = "x" ]; then
        return 2
    fi

    local index=-1
    if [ -z "$answer" ]; then
        index=$defaultIndex
    elif [ "$answer" -ge 1 ] 2>/dev/null && [ "$answer" -le $NUM_DEVICES ] 2>/dev/null; then
        index=$((answer - 1))
    fi
    if [ $index -lt 0 ]; then
        echo "invalid selection"
        return 1
    fi
    if [ -n "${offline_flags[$index]}" ]; then
        echo "device is offline: ${displays[$index]}"
        return 1
    fi

    export ADB_DEVICE="${ids[$index]}"
    export ADB_PORT="-p $RANDOM"
    saveLastDevice "$ADB_DEVICE"
    refreshDeviceName "$ADB_DEVICE" "${serials[$index]}"
    echo "using device: ${friendly[$index]} (${transports[$index]})${wireless_flags[$index]} ($ADB_DEVICE), port: $ADB_PORT"
    return 0
}

selectDevice "$@"
