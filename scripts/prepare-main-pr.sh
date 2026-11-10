#!/usr/bin/env bash
# 개인 환경 커밋을 포함하지 않는 main 기반 PR 브랜치를 만든다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR/.." rev-parse --show-toplevel)"

die() { printf '%s\n' "$*" >&2; exit 1; }
usage() {
  printf '사용법: scripts/prepare-main-pr.sh 새-PR-브랜치 기능-커밋 [기능-커밋 ...]\n'
  printf '선택한 기능 커밋을 origin/main 기반의 별도 작업 폴더에 복사합니다. 푸시·PR 생성·병합은 하지 않습니다.\n'
}

if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then usage; exit 0; fi
[[ $# -ge 2 ]] || { usage >&2; exit 1; }
pr_branch="$1"
shift
git check-ref-format --branch "$pr_branch" >/dev/null || die '유효하지 않은 PR 브랜치 이름입니다.'
[[ "$pr_branch" != main && "$pr_branch" != thingk0 ]] || die 'main·thingk0와 다른 PR 브랜치 이름을 지정하세요.'
git -C "$REPO_ROOT" show-ref --verify --quiet "refs/heads/$pr_branch" && die '이미 존재하는 로컬 브랜치입니다.'
[[ -z "$(git -C "$REPO_ROOT" status --porcelain)" ]] || die '현재 변경을 먼저 커밋한 뒤 실행하세요.'

is_personal_path() {
  case "$1" in
    .env.example|*/.env.example) return 1 ;;
    .dev|.dev/*|scripts/gcp-trigger.sh|scripts/prepare-main-pr.sh|docs/gcp-development.md|local.properties|.claude/settings.local.json|.env|.env.*|*/.env|*/.env.*|*.jks|*.keystore|keystore.properties) return 0 ;;
    *) return 1 ;;
  esac
}

commits=()
for requested in "$@"; do
  commit="$(git -C "$REPO_ROOT" rev-parse --verify --end-of-options "$requested^{commit}")" || die '커밋을 찾지 못했습니다.'
  git -C "$REPO_ROOT" merge-base --is-ancestor "$commit" HEAD || die '현재 개인 브랜치에 없는 커밋입니다.'
  parents="$(git -C "$REPO_ROOT" rev-list --parents -n 1 "$commit")"
  [[ "$(printf '%s\n' "$parents" | awk '{print NF}')" == 2 ]] || die '루트·머지 커밋 대신 개별 기능 커밋을 지정하세요.'
  while IFS= read -r -d '' changed_path; do
    if is_personal_path "$changed_path"; then
      die "개인용 파일이 포함된 커밋입니다: $commit ($changed_path). 기능 변경을 별도 커밋으로 나눠 주세요."
    fi
  done < <(git -C "$REPO_ROOT" diff --name-only --no-renames -z "$commit^" "$commit")
  commits+=("$commit")
done

# main을 전환하거나 갱신하지 않고 origin/main만 받는다.
git -C "$REPO_ROOT" -c 'url.https://github.com/.insteadOf=git@github.com:' \
  fetch --no-tags origin 'refs/heads/main:refs/remotes/origin/main'
base="$(git -C "$REPO_ROOT" rev-parse origin/main)"
worktree="$REPO_ROOT/.dev/pr-worktrees/$pr_branch"
[[ ! -e "$worktree" ]] || die "작업 폴더가 이미 있습니다: $worktree"
mkdir -p "$(dirname "$worktree")"
git -C "$REPO_ROOT" worktree add -b "$pr_branch" "$worktree" "$base"

if ! git -C "$worktree" -c user.name=Developer \
  -c user.email=developer@users.noreply.github.com -c commit.gpgsign=false \
  cherry-pick "${commits[@]}"; then
  printf '충돌 또는 빈 커밋으로 중단했습니다. 개인 브랜치는 그대로이며, PR 작업 폴더에서 확인하세요: %s\n' "$worktree" >&2
  exit 1
fi

while IFS= read -r -d '' changed_path; do
  if is_personal_path "$changed_path"; then
    die "PR 변경에 개인용 파일이 있습니다: $changed_path"
  fi
done < <(git -C "$worktree" diff --name-only --no-renames -z "$base" HEAD)

printf '\nPR 브랜치 준비 완료: %s\n작업 폴더: %s\n' "$pr_branch" "$worktree"
git -C "$worktree" diff --stat "$base" HEAD
printf '\n아직 푸시·PR 생성·main 병합은 하지 않았습니다. 변경을 검토한 뒤 요청하세요.\n'
