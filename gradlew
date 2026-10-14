#!/usr/bin/env sh
# Lightweight Gradle bootstrap for GitHub Actions when the standard wrapper is not generated yet.
set -eu
GRADLE_VERSION="8.7"
CACHE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/bootstrap/gradle-${GRADLE_VERSION}"
GRADLE_BIN="${CACHE_DIR}/gradle-${GRADLE_VERSION}/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$CACHE_DIR"
  ARCHIVE="${CACHE_DIR}/gradle-${GRADLE_VERSION}-bin.zip"
  if [ ! -f "$ARCHIVE" ]; then
    curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "$ARCHIVE"
  fi
  unzip -q -o "$ARCHIVE" -d "$CACHE_DIR"
fi

exec "$GRADLE_BIN" "$@"
