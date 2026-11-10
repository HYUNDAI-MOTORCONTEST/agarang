#!/usr/bin/env bash
# 로컬 gcloud 인증으로 GCP의 소스를 동기화하고 빌드·테스트한다.
# macOS 기본 bash 3.2와 Linux bash에서 실행한다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/_common.sh"

usage() {
  cat <<'USAGE'
사용법: scripts/gcp-trigger.sh [--check | --build] [--branch 이름] [--start-vm]

  --check      VM 접속·원격 저장소·빌드 도구만 확인한다. 소스를 갱신하지 않는다.
  --build      푸시된 커밋으로 원격 브랜치를 갱신하고 앱·서버·공용을 빌드·테스트한다(기본).
  --branch     빌드할 브랜치(기본 로컬의 현재 작업 브랜치).
  --start-vm   중지된 VM을 시작한다. VM 실행 중에는 사용 요금이 발생한다.

설정은 환경변수 또는 git에서 제외되는 .dev/gcp-trigger.env로 지정한다.
  HMG_GCP_PROJECT     프로젝트 ID(생략하면 gcloud의 현재 프로젝트)
  HMG_GCP_ZONE        VM zone(생략하면 gcloud의 현재 zone)
  HMG_GCP_INSTANCE    VM 이름(기본 pleos-emu)
  HMG_GCP_RUN_AS      SDK를 설치한 원격 계정(필요하면 sudo로 전환)
  HMG_GCP_REMOTE_DIR  원격 저장소 절대 경로(생략하면 원격 계정의 workspaces 폴더)
USAGE
}

if [[ -f "$REPO_ROOT/.dev/gcp-trigger.env" ]]; then
  source "$REPO_ROOT/.dev/gcp-trigger.env"
fi

MODE=build
BRANCH=""
START_VM=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --check) MODE=check; shift ;;
    --build) MODE=build; shift ;;
    --start-vm) START_VM=1; shift ;;
    --branch)
      [[ $# -ge 2 && -n "$2" ]] || die "--branch 다음에 브랜치 이름을 지정하세요."
      BRANCH="$2"
      shift 2
      ;;
    -h|--help) usage; exit 0 ;;
    *) die "알 수 없는 인자입니다: $1 (사용법: --help)" ;;
  esac
done

[[ "$MODE" != check || "$START_VM" -eq 0 ]] || die "--check와 --start-vm은 함께 사용할 수 없습니다."
require_cmd gcloud "Google Cloud CLI를 설치하고 gcloud init을 실행하세요."
require_cmd git "Git을 설치하세요."
CURRENT_BRANCH="$(git -C "$REPO_ROOT" symbolic-ref --quiet --short HEAD || true)"
if [[ -z "$BRANCH" ]]; then
  [[ -n "$CURRENT_BRANCH" ]] || die "현재 HEAD가 브랜치에 연결돼 있지 않습니다. --branch로 지정하세요."
  BRANCH="$CURRENT_BRANCH"
fi
git check-ref-format --branch "$BRANCH" >/dev/null || die "유효하지 않은 브랜치 이름입니다."

EXPECTED_COMMIT=""
if [[ "$MODE" == build ]]; then
  if [[ "$BRANCH" == "$CURRENT_BRANCH" ]]; then
    [[ -z "$(git -C "$REPO_ROOT" status --porcelain)" ]] ||
      die "로컬 변경 사항이 있습니다. 테스트할 변경을 커밋·푸시한 뒤 다시 실행하세요."
  fi
  EXPECTED_COMMIT="$(git -C "$REPO_ROOT" -c 'url.https://github.com/.insteadOf=git@github.com:' \
    ls-remote --exit-code origin "refs/heads/$BRANCH" | cut -f1)" ||
    die "GitHub에서 $BRANCH 브랜치를 확인하지 못했습니다. 푸시 여부와 접근 권한을 확인하세요."
  [[ "$EXPECTED_COMMIT" =~ ^[0-9a-f]{40,64}$ ]] || die "원격 커밋 SHA를 확인하지 못했습니다."
  if [[ "$BRANCH" == "$CURRENT_BRANCH" ]]; then
    [[ "$(git -C "$REPO_ROOT" rev-parse HEAD)" == "$EXPECTED_COMMIT" ]] ||
      die "로컬 HEAD와 GitHub 브랜치가 다릅니다. 먼저 브랜치를 갱신하거나 변경을 푸시하세요."
  fi
  printf '빌드 요청: %s (%s)\n' "$EXPECTED_COMMIT" "$BRANCH"
fi

PROJECT="${HMG_GCP_PROJECT:-$(gcloud config get-value project 2>/dev/null)}"
ZONE="${HMG_GCP_ZONE:-$(gcloud config get-value compute/zone 2>/dev/null)}"
INSTANCE="${HMG_GCP_INSTANCE:-pleos-emu}"
[[ -n "$PROJECT" && "$PROJECT" != '(unset)' ]] || die "HMG_GCP_PROJECT 또는 gcloud 프로젝트를 설정하세요."
[[ -n "$ZONE" && "$ZONE" != '(unset)' ]] || die "HMG_GCP_ZONE 또는 gcloud compute/zone을 설정하세요."

VM_STATE="$(gcloud compute instances describe "$INSTANCE" --project="$PROJECT" --zone="$ZONE" --format='value(status)')"
if [[ "$VM_STATE" == TERMINATED && "$START_VM" -eq 1 ]]; then
  gcloud compute instances start "$INSTANCE" --project="$PROJECT" --zone="$ZONE" --quiet
elif [[ "$VM_STATE" != RUNNING ]]; then
  die "VM 상태: $VM_STATE. VM을 켜거나 --build --start-vm으로 실행하세요."
fi

# 원격 명령의 인자는 아래에서 printf %q로 인코딩한다. 사용자 설정을 코드에 삽입하지 않는다.
REMOTE_SCRIPT="$(cat <<'REMOTE'
set -euo pipefail
mode="$1"
branch="$2"
repo_dir="${3:-$HOME/workspaces/HYUNDAI_MOTOR_CONTEST}"
expected_commit="${4:-}"
[[ -e "$repo_dir/.git" ]] || { printf '원격 Git 저장소가 없습니다: %s\n' "$repo_dir" >&2; exit 1; }
cd "$repo_dir"
git check-ref-format --branch "$branch" >/dev/null
if [[ -f .dev/gcp-env.sh ]]; then source .dev/gcp-env.sh; fi

if [[ "$mode" == build ]]; then
  command -v flock >/dev/null || { printf '원격 VM에 flock이 필요합니다.\n' >&2; exit 1; }
  mkdir -p .dev
  exec 9>.dev/gcp-trigger.lock
  flock -n 9 || { printf '다른 원격 빌드가 실행 중입니다.\n' >&2; exit 1; }
  [[ -z "$(git status --porcelain)" ]] || { printf '원격 저장소에 변경 사항이 있습니다. 먼저 확인하세요.\n' >&2; exit 1; }
  # 공개 GitHub 저장소는 HTTPS로 읽는다. SSH 키나 원격 origin 설정을 바꾸지 않는다.
  git -c 'url.https://github.com/.insteadOf=git@github.com:' fetch --no-tags origin "refs/heads/$branch:refs/remotes/origin/$branch" ||
    { printf 'GitHub에서 %s 브랜치를 받지 못했습니다. 푸시 여부와 접근 권한을 확인하세요.\n' "$branch" >&2; exit 1; }
  [[ -n "$expected_commit" && "$(git rev-parse "refs/remotes/origin/$branch")" == "$expected_commit" ]] ||
    { printf '빌드 요청 후 GitHub 브랜치가 변경됐습니다. 로컬 브랜치를 확인하고 다시 실행하세요.\n' >&2; exit 1; }
  if [[ "$(git symbolic-ref --quiet --short HEAD || true)" != "$branch" ]]; then
    if git show-ref --verify --quiet "refs/heads/$branch"; then
      git switch "$branch"
    else
      git switch -c "$branch" --track "origin/$branch"
    fi
  fi
  git merge --ff-only "refs/remotes/origin/$branch"
  [[ "$(git rev-parse HEAD)" == "$expected_commit" ]] ||
    { printf '원격 작업 브랜치에 GitHub와 다른 커밋이 있습니다. 변경을 확인한 뒤 다시 실행하세요.\n' >&2; exit 1; }
fi

source scripts/_common.sh
setup_android_env
JAVA_HOME="$(find_java_home)" || die "JDK 21 이상이 필요합니다."
export JAVA_HOME
[[ "$(java_major "$JAVA_HOME")" -ge 21 ]] || die "앱·서버 전체 빌드에는 JDK 21 이상이 필요합니다."
compile_sdk="$(sed -n 's/^android-compileSdk[[:space:]]*=[[:space:]]*"\([0-9.]*\)".*/\1/p' gradle/libs.versions.toml | head -1)"
[[ -n "$compile_sdk" ]] || die "compileSdk 버전을 찾지 못했습니다."
missing_sdk=()
if [[ ! -f "$ANDROID_HOME/platforms/android-$compile_sdk/android.jar" && ! -f "$ANDROID_HOME/platforms/android-$compile_sdk.0/android.jar" ]]; then
  missing_sdk+=("platform $compile_sdk")
fi
build_tools_version="${compile_sdk%%.*}.0.0"
if [[ ! -x "$ANDROID_HOME/build-tools/$build_tools_version/aapt2" ]]; then
  missing_sdk+=("build-tools $build_tools_version")
fi
if [[ ${#missing_sdk[@]} -gt 0 ]]; then
  die "SDK 패키지 없음: ${missing_sdk[*]}. VM 터미널에서 sdkmanager로 직접 설치하고 라이선스를 확인하세요."
fi
printf '소스: %s (%s)\n' "$(git rev-parse HEAD)" "$(git branch --show-current)"
printf 'JDK: %s / Android SDK: %s\n' "$(java_major "$JAVA_HOME")" "$compile_sdk"

if [[ "$mode" == check ]]; then
  printf '원격 빌드 환경 확인 완료\n'
  exit 0
fi

./gradlew :frontend:app:assembleDebug :frontend:app:testDebugUnitTest :backend:build :shared:build -Pandroid.builder.sdkDownload=false 9>&-
printf 'APK: %s/frontend/app/build/outputs/apk/debug/app-debug.apk\n' "$repo_dir"
REMOTE
)"

REMOTE_ARGS=(bash -c "$REMOTE_SCRIPT" -- "$MODE" "$BRANCH" "${HMG_GCP_REMOTE_DIR:-}" "$EXPECTED_COMMIT")
if [[ -n "${HMG_GCP_RUN_AS:-}" ]]; then
  REMOTE_ARGS=(sudo -n -H -u "$HMG_GCP_RUN_AS" "${REMOTE_ARGS[@]}")
fi
printf -v REMOTE_COMMAND '%q ' "${REMOTE_ARGS[@]}"
gcloud compute ssh "$INSTANCE" --project="$PROJECT" --zone="$ZONE" --quiet \
  --ssh-flag='-o ConnectTimeout=20' --ssh-flag='-o ConnectionAttempts=5' --ssh-flag='-o BatchMode=yes' \
  --command="$REMOTE_COMMAND"
