#!/bin/bash
#######################################################################################################################################
# Run the desktop app from source.
#
# Requires the API keys in env or ~/.gradle/gradle.properties (see CLAUDE.md):
#   LOGGING_URL, LOGGING_API
#######################################################################################################################################

set -e

cd "$(dirname "$0")/.."

trap './gradlew --stop >/dev/null 2>&1' EXIT INT TERM
exec ./gradlew :composeApp:run
