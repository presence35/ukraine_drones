#!/bin/sh
# Standard Gradle wrapper launcher. If this is missing pieces, open the project
# in Android Studio once and it will regenerate/repair the wrapper automatically.
DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$DIR/gradle/wrapper/gradle-wrapper.jar" "$@" 2>/dev/null || {
  echo "Open this project in Android Studio — it will auto-download/repair the Gradle wrapper on first sync."
  exit 1
}
