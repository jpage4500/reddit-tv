#!/bin/bash
#######################################################################################################################################
# Make a booted iOS simulator trust the corporate TLS-inspection proxy (Netskope) so HTTPS works on VPN.
#
# Usage: fix-ios-simulator.sh [device] [--name <cert name>] [--pem <file>]
#
#   device        simulator name or UDID (see: xcrun simctl list devices booted).
#                 Defaults to every booted simulator.
#   --name        match certificates in the System keychain by this name. Default "Netskope".
#   --pem         install the certificates in this PEM file instead of exporting from the keychain
#
# WHY. Netskope re-signs every HTTPS response with its own CA. The Mac trusts that CA because
# the Netskope agent installs it into the System keychain, but each simulator has its own trust
# store that knows nothing about it — so every request fails with "ATS failed system trust"
# (-9802 / NSURLErrorDomain -1200) and the cert chain in the error ends at "Netskope Certificate
# Authority (nsRootCA)".
#
# The fix is per simulator and doesn't survive "Erase All Content and Settings", so re-run this
# after erasing a device, on each new one, and whenever Netskope rotates its CAs (the agent's
# files under /Library/Application Support/Netskope/STAgent/data change date when it does).
# Relaunch the app afterwards — no rebuild needed.
#######################################################################################################################################

set -e

CERT_NAME="Netskope"
PEM_FILE=""
DEVICE=""

function usage {
    cat <<EOF
usage: fix-ios-simulator.sh [device] [--name <cert name>] [--pem <file>]

  device      simulator name or UDID (see: xcrun simctl list devices booted).
              defaults to every booted simulator.
  --name      match certificates in the System keychain by this name (default "$CERT_NAME")
  --pem       install the certificates in this PEM file instead of exporting from the keychain
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --name) CERT_NAME="${2:-}"; shift ;;
        --pem) PEM_FILE="${2:-}"; shift ;;
        -h|--help) usage; exit 0 ;;
        -*) echo "unknown option: $1"; exit 1 ;;
        *) DEVICE="$1" ;;
    esac
    shift
done

# ==========================================
# pick the simulators
# ==========================================
# simctl keychain only works on a booted device, so always match against the booted list
BOOTED=$(xcrun simctl list devices booted | grep '(Booted)' || true)
if [ -n "$DEVICE" ]; then
    BOOTED=$(echo "$BOOTED" | grep -i -- "$DEVICE" || true)
fi

if [ -z "$BOOTED" ]; then
    if [ -n "$DEVICE" ]; then
        echo "✗ no booted simulator matching '$DEVICE':"
        xcrun simctl list devices booted | grep '(Booted)' || echo "    (none booted)"
    else
        echo "✗ no booted simulator — start one first (Xcode > Open Developer Tool > Simulator)"
    fi
    exit 1
fi

# ==========================================
# collect the certificates
# ==========================================
WORK_DIR=$(mktemp -d -t simcerts)
trap 'rm -rf "$WORK_DIR"' EXIT

if [ -n "$PEM_FILE" ]; then
    if [ ! -f "$PEM_FILE" ]; then
        echo "✗ no such file: $PEM_FILE"
        exit 1
    fi
    cp "$PEM_FILE" "$WORK_DIR/all.pem"
else
    security find-certificate -a -c "$CERT_NAME" -p /Library/Keychains/System.keychain > "$WORK_DIR/all.pem" 2>/dev/null || true
fi

# split into one file per cert — each needs its own simctl call, and roots and intermediates differ
awk -v dir="$WORK_DIR" '
    /-----BEGIN CERTIFICATE-----/ { n++; out = sprintf("%s/cert-%02d.pem", dir, n) }
    out { print > out }
    /-----END CERTIFICATE-----/ { close(out); out = "" }
' "$WORK_DIR/all.pem"

CERTS=("$WORK_DIR"/cert-*.pem)
if [ ! -e "${CERTS[0]}" ]; then
    if [ -n "$PEM_FILE" ]; then
        echo "✗ no certificates found in $PEM_FILE"
    else
        echo "✗ no certificates named '$CERT_NAME' in the System keychain — is the Netskope agent installed?"
    fi
    exit 1
fi

# ==========================================
# install
# ==========================================
FAILED=0
while read -r LINE; do
    UDID=$(echo "$LINE" | grep -o '[0-9A-F-]\{36\}' | head -1)
    NAME=$(echo "$LINE" | sed 's/^ *//;s/ (.*//')
    echo "$NAME ($UDID)"
    for CERT in "${CERTS[@]}"; do
        SUBJECT=$(openssl x509 -in "$CERT" -noout -subject 2>/dev/null | sed 's/^subject= *//')
        ISSUER=$(openssl x509 -in "$CERT" -noout -issuer 2>/dev/null | sed 's/^issuer= *//')
        LABEL=$(echo "$SUBJECT" | sed 's/.*CN *= *//')
        LABEL=${LABEL:-$(basename "$CERT")}
        # add-root-cert rejects anything not self-signed (-50). only the root has to be trusted —
        # the proxy sends the intermediates in its chain — so those go in as plain certs
        if [ -n "$SUBJECT" ] && [ "$SUBJECT" = "$ISSUER" ]; then
            KIND="add-root-cert"; WHAT="trusted root"
        else
            KIND="add-cert"; WHAT="intermediate"
        fi
        if ERR=$(xcrun simctl keychain "$UDID" "$KIND" "$CERT" 2>&1); then
            echo "  ✔ $LABEL ($WHAT)"
        else
            echo "  ✗ $LABEL ($WHAT): $ERR"
            FAILED=1
        fi
    done
done <<< "$BOOTED"

if [ "$FAILED" -ne 0 ]; then
    exit 1
fi
echo "done — relaunch the app on the simulator"
