# 개인 GCP 개발·테스트 흐름

**로컬의 `thingk0`에서 개발 → 커밋·푸시 → GCP에서 같은 커밋을 빌드·테스트**한다.
GCP 작업 폴더는 푸시된 소스를 받아 검증하는 데 사용한다.
개인 연결 정보는 `.dev/`에 두고 이 문서·스크립트는 개인 브랜치에서 관리한다. 팀원의 GCP 설정은 필요 없다.
커밋·푸시는 사용자가 요청할 때만 진행한다. 이 스크립트는 커밋하거나 푸시하지 않는다.

## 평소 작업 순서

1. 로컬의 개인 작업 브랜치에서 앱·서버 코드를 수정한다.
2. 변경 내용을 검토하고 필요한 파일만 커밋한 뒤 `git push`로 반영한다.
3. `scripts/gcp-trigger.sh --build`로 푸시한 커밋을 GCP에서 빌드·테스트한다.
4. 로그에 표시된 커밋 SHA와 테스트 결과를 확인하고, 수정이 필요하면 로컬에서 반복한다.

로컬의 현재 브랜치에 미커밋·미푸시 변경이 있으면 빌드 요청을 중단한다.
다른 브랜치를 `--branch 이름`으로 지정하면 해당 GitHub 브랜치의 커밋을 사용한다.

## 사용법

```bash
scripts/gcp-trigger.sh --check          # SSH·원격 저장소·JDK·SDK 확인
scripts/gcp-trigger.sh --build          # 현재 작업 브랜치 동기화 → 앱 빌드·단위 테스트 + 서버·공용 빌드·테스트
scripts/gcp-trigger.sh --jvm-only       # 같은 커밋의 서버·공용 빌드·테스트(Android SDK 불필요)
scripts/gcp-trigger.sh --check --jvm-only # SSH·원격 저장소·JDK 확인
scripts/gcp-trigger.sh --start-vm       # 중지된 VM도 시작해서 같은 작업 실행
scripts/gcp-trigger.sh --branch main    # main을 명시해서 빌드할 때
```

기본 동작은 `--build`이며, 로컬의 현재 브랜치를 사용한다. `--branch 이름`으로 다른 브랜치를 지정할 수 있다.
원격 작업 폴더가 깨끗하면 GitHub에서 해당 브랜치를 받은 뒤 자동으로 전환하고 fast-forward로 갱신한다.

빌드는 **GitHub에 반영된 코드**를 사용한다. 로컬의 미커밋·미푸시 변경은 전송하지 않는다.
요청 시점의 GitHub 커밋 SHA를 원격 동기화 후에도 확인한다. 요청 도중 GitHub 브랜치가 바뀌거나
VM 브랜치에 미푸시 커밋이 있어 요청한 커밋과 달라지면 빌드하지 않는다.
공개 GitHub 저장소는 HTTPS로 읽기 때문에 GCP에 GitHub SSH 키를 추가하지 않아도 된다.
원격에 수정 사항이 있거나 브랜치가 서로 갈라졌으면 동기화를 중단한다.
이 트리거로 실행한 빌드는 동일 작업 폴더당 한 번에 하나만 실행된다.

개인 문서·스크립트는 `thingk0`에서 관리한다. 앱 기능과 개인 환경 변경은 별도 커밋으로 나눈다.
`thingk0`를 `main`에 직접 PR·병합하지 않고, 아래 절차로 main 기반 PR 브랜치에 기능 커밋만 반영한다.
다른 PC에서는 GitHub에 반영된 본인 브랜치를 받은 뒤 gcloud 로그인과 개인 연결 설정을 준비한다.

## main에 보낼 변경 준비

`.dev/`는 처음부터 Git에서 제외되므로 개인 연결 정보·로그·이미지는 PR에 들어가지 않는다.
이미 커밋된 파일은 `.gitignore`를 추가해도 추적이 유지된다. 특정 파일만 자동으로 제외하고
브랜치를 병합하는 설정 대신, `origin/main`에서 PR 브랜치를 만들고 기능 커밋만 복사한다.

```bash
scripts/prepare-main-pr.sh pr/agarang-p0-contracts 57a85e2
```

- 현재 `thingk0`와 로컬 `main`을 그대로 두고 `.dev/pr-worktrees/`에 별도 Git 작업 폴더를 만든다.
- 현재 개인 브랜치의 개별 기능 커밋을 순서대로 지정한다. `origin/main`을 기준으로 새 커밋이 만들어진다.
- `.dev/`, 이 문서, `scripts/gcp-trigger.sh`, `scripts/prepare-main-pr.sh` 또는 로컬 설정·키 파일을
  건드린 커밋은 PR 브랜치를 만들기 전에 거부한다. 기능과 개인 변경이 섞였으면 먼저 커밋을 나눈다.
- 충돌·빈 커밋이면 PR 작업 폴더에서 멈춘다. 원래 개인 브랜치는 바뀌지 않는다.
- 준비한 PR의 파일 목록·코드를 검토한 다음 푸시·PR 생성을 요청한다. 이 명령은 푸시·PR 생성·병합을 하지 않는다.
- `AGENTS.md`, `.mcp.json`, `.codex/`처럼 이미 main에 있는 공용 프로젝트 설정은 일괄 제외하지 않는다.

이 방식은 개인 도구 파일과 개인 환경 커밋 이력을 `main`에 가져오지 않는다.
`thingk0`에서 main으로 직접 PR을 만들면 개인 도구도 포함되므로 항상 준비한 PR 브랜치를 사용한다.

참고: [Git ignore의 추적 파일 처리](https://git-scm.com/docs/gitignore),
[선택한 커밋을 복사하는 cherry-pick](https://git-scm.com/docs/git-cherry-pick).

## 로컬 연결 설정

먼저 Google Cloud CLI를 설치하고 `gcloud init`으로 계정·프로젝트를 선택한다.
저장소의 `.dev/gcp-trigger.env`에 연결 정보를 둔다. 이 파일은 `.gitignore`의 `.dev/` 규칙으로 제외된다.
계정명·IP·키를 추적되는 파일에 넣지 않는다.

```bash
HMG_GCP_PROJECT=your-project-id
HMG_GCP_ZONE=your-zone
HMG_GCP_INSTANCE=your-vm
HMG_GCP_RUN_AS=your-sdk-owner
HMG_GCP_REMOTE_DIR=/absolute/path/to/your-checkout
```

`HMG_GCP_RUN_AS`를 지정하면 SSH 계정에서 해당 SDK 소유 계정으로 `sudo -n -H -u` 전환한다.
이 경우 SSH 계정에 해당 전환 권한이 있어야 한다. 생략하면 SSH 계정으로 실행한다.
프로젝트·zone은 생략하면 gcloud의 현재 설정을 사용하며 VM 이름 기본값은 `pleos-emu`다.

## VM 준비와 결과

- 원격 경로에 Git 작업 폴더와 `origin`이 있어야 한다.
- JDK 21 이상, 버전 카탈로그의 compileSdk에 맞는 Android SDK와 Build Tools가 필요하다.
- VM의 `.dev/gcp-env.sh`가 있으면 해당 환경 설정을 읽는다. 없으면 기존 환경·SDK 기본 경로를 사용한다.
- SDK 설치·라이선스 동의는 사람이 직접 진행한다. 이 스크립트는 SDK를 설치하지 않는다.
- VM이 꺼져 있으면 기본적으로 중단한다. `--start-vm`을 명시하면 시작하며 실행 중에는 VM 요금이 발생한다.

현재 버전 카탈로그의 compileSdk 37에 필요한 패키지는 `platforms;android-37.0`과 `build-tools;37.0.0`이다.
VM의 SDK 소유 계정으로 로그인해 다음을 직접 실행하고 라이선스를 확인한다.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-37.0" "build-tools;37.0.0"
```

실행하는 Gradle 태스크는 `:frontend:app:assembleDebug :frontend:app:testDebugUnitTest :backend:build :shared:build`다.
앱 debug APK와 각 모듈 테스트 결과는 VM 작업 폴더의 각 `build/` 아래에 남는다.
앱 단위 테스트가 아직 없으면 해당 태스크는 `NO-SOURCE`로 표시된다.
`--jvm-only`는 `:backend:build :shared:build --configure-on-demand`를 실행한다. 순수 Kotlin 계약·판단 로직을
검증할 때 사용하며, 앱 빌드·설치·차량 UI 검증을 통과했다는 의미로 사용하지 않는다.
앱 설치·화면 조작·주행 제한 검증은 이 빌드 트리거와 별도로 실행한다.

참고: [gcloud compute ssh](https://docs.cloud.google.com/sdk/gcloud/reference/compute/ssh),
[gcloud compute instances start](https://docs.cloud.google.com/sdk/gcloud/reference/compute/instances/start).
