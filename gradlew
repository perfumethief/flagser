#!/usr/bin/env sh
set -eu
GRADLE_VERSION=8.9
WRAPPER_ROOT="${HOME}/.gradle/flagser-wrapper"
GRADLE_HOME="${WRAPPER_ROOT}/gradle-${GRADLE_VERSION}"
ZIP_FILE="${WRAPPER_ROOT}/gradle-${GRADLE_VERSION}-bin.zip"
if [ ! -x "${GRADLE_HOME}/bin/gradle" ]; then
  mkdir -p "${WRAPPER_ROOT}"
  echo "Preparing Gradle ${GRADLE_VERSION}..."
  if command -v curl >/dev/null 2>&1; then
    curl -L "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "${ZIP_FILE}"
  else
    wget "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -O "${ZIP_FILE}"
  fi
  unzip -o "${ZIP_FILE}" -d "${WRAPPER_ROOT}" >/dev/null
fi
exec "${GRADLE_HOME}/bin/gradle" "$@"
