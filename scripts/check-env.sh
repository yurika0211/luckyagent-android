#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ok() { printf '[ok] %s
' "$*"; }
warn() { printf '[!!] %s
' "$*"; }
fail=0

echo "== luckyagent-android env check =="

if command -v java >/dev/null 2>&1; then
  ok "java: $(java -version 2>&1 | head -1)"
else
  warn "java not found (need JDK 17+)"
  fail=1
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
  ok "JAVA_HOME=$JAVA_HOME"
else
  warn "JAVA_HOME unset"
fi

SDK=""
if [[ -f local.properties ]]; then
  SDK=$(grep -E '^sdk.dir=' local.properties | head -1 | cut -d= -f2- | tr -d '\r' || true)
fi
SDK=${SDK:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}
if [[ -n "$SDK" && -d "$SDK" ]]; then
  ok "Android SDK: $SDK"
  [[ -d "$SDK/platforms" ]] && ok "platforms present" || warn "no platforms under SDK"
else
  warn "Android SDK not found (set sdk.dir in local.properties or ANDROID_SDK_ROOT)"
  fail=1
fi

if [[ -f gradle/wrapper/gradle-wrapper.jar ]]; then
  ok "gradle-wrapper.jar present"
else
  warn "gradle-wrapper.jar missing — run: gradle wrapper --gradle-version 8.11.1"
  fail=1
fi

if [[ -x ./gradlew ]]; then
  ok "gradlew executable"
else
  warn "gradlew missing or not executable"
  fail=1
fi

if [[ $fail -eq 0 ]]; then
  echo "Environment looks ready for ./gradlew :app:assembleDebug"
  exit 0
else
  echo "Environment incomplete; open in Android Studio or install JDK/SDK/wrapper."
  exit 1
fi
