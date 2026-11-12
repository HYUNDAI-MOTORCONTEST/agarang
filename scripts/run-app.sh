#!/usr/bin/env bash
# debug 앱을 빌드·설치하고 실행한다.
# 사용법: scripts/run-app.sh [app|sandbox] [gradle 추가 인자...]
#   예) scripts/run-app.sh                                   # 본 앱(:frontend:app) — 기본
#   예) scripts/run-app.sh -Phmg.backendUrl=http://10.0.2.2:9090
#   예) scripts/run-app.sh sandbox                           # 로컬 테스트용 샌드박스 앱(:frontend:sandbox, 제출 대상 아님)
#   예) HMG_APP=sandbox scripts/run-app.sh                   # 환경변수로 골라도 된다(첫 인자가 우선)
# JDK는 JAVA_HOME(17+) → Android Studio 내장 JBR → java_home 순으로 찾는다(_common.sh find_java_home).
set -euo pipefail
source "$(dirname "$0")/_common.sh"

APP="${HMG_APP:-app}"
if [[ $# -gt 0 && "$1" != -* ]]; then
  APP="$1"
  shift
fi

case "$APP" in
  app)
    MODULE=":frontend:app"
    APP_ID="com.hmgchallenge.app"
    LOG_TAG="HmgApp"
    ;;
  sandbox)
    MODULE=":frontend:sandbox"
    APP_ID="com.hmgchallenge.sandbox"
    LOG_TAG="AgarangMini"
    ;;
  *)
    die "알 수 없는 앱입니다: $APP (app 또는 sandbox)"
    ;;
esac
ACTIVITY="$APP_ID/.MainActivity"

setup_android_env
require_cmd adb "Android SDK의 Platform-Tools를 설치하세요."

JAVA_HOME="$(find_java_home)" ||
  die "JDK 17 이상을 찾지 못했습니다. JAVA_HOME을 지정하거나 Android Studio를 설치하세요."
export JAVA_HOME

adb get-state >/dev/null 2>&1 || die "연결된 기기가 없습니다. 에뮬레이터를 먼저 실행하세요(scripts/pleos-emu.sh --wait)."

cd "$REPO_ROOT"
./gradlew "$MODULE:installDebug" "$@" || die "빌드·설치에 실패했습니다."
# Pleos v2.1.0의 Cold boot 직후에는 sys.boot_completed=1이어도 차량 홈의 창 배치가 아직 없을 수 있다.
# 이때 앱을 먼저 열면 Driving view 영역까지 쓰는 일반 전체 화면으로 뜬 것을 확인했다(pleos-dev.md 6장).
# 설치된 Pleos 홈에서만 표준 HOME 인텐트로 초기화하고, 운전 화면 Activity가 생긴 뒤 런처 경로로 연다.
if adb shell pm path ai.umos.homescreen 2>/dev/null | grep '^package:' >/dev/null; then
  if ! adb shell dumpsys activity activities | grep -F 'ai.umos.drivingview/' >/dev/null; then
    adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME ||
      die "Pleos 차량 홈을 열지 못했습니다."
    attempt=0
    until adb shell dumpsys activity activities | grep -F 'ai.umos.drivingview/' >/dev/null; do
      if [[ "$attempt" -ge 30 ]]; then
        die "Pleos 차량 홈의 창 배치가 준비되지 않았습니다. 홈 화면을 확인한 뒤 다시 실행하세요."
      fi
      sleep 1
      attempt=$((attempt + 1))
    done
  fi
fi
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$ACTIVITY" ||
  die "앱 실행에 실패했습니다: $ACTIVITY"
echo "실행: $ACTIVITY  (로그: adb logcat -s $LOG_TAG)"
