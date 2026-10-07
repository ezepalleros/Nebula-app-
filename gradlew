#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$WRAPPER_JAR" ]; then
  exec java -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi

# Fallback bootstrap for this generated starter: useful if the wrapper JAR is not present.
GRADLE_VERSION=8.11.1
BOOT="$APP_HOME/.gradle-bootstrap"
DIST="$BOOT/gradle-$GRADLE_VERSION"
ZIP="$BOOT/gradle-$GRADLE_VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
mkdir -p "$BOOT"
if [ ! -x "$DIST/bin/gradle" ]; then
  if [ ! -f "$ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -L --fail "$URL" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget "$URL" -O "$ZIP"
    else
      echo "No se encontró curl/wget. Abrí el proyecto en Android Studio y sincronizá Gradle desde el IDE." >&2
      exit 1
    fi
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$ZIP" -d "$BOOT"
  else
    echo "No se encontró unzip. Abrí el proyecto en Android Studio y sincronizá Gradle desde el IDE." >&2
    exit 1
  fi
fi
exec "$DIST/bin/gradle" "$@"
