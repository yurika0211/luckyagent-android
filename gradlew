#!/bin/sh
#
# Gradle start-up script for POSIX generated for luckyagent-android.
#
dir=$(cd "$(dirname "$0")" && pwd)
APP_HOME=$dir
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ ! -f "$CLASSPATH" ]; then
  echo "gradle-wrapper.jar missing. Run: gradle wrapper --gradle-version 8.11.1" >&2
  echo "Or open this project in Android Studio once." >&2
  exit 1
fi
# Determine Java
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVACMD=$JAVA_HOME/bin/java
elif command -v java >/dev/null 2>&1; then
  JAVACMD=java
else
  echo "ERROR: JAVA_HOME is not set and no java in PATH." >&2
  exit 1
fi
exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
