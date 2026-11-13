# 아가랑

> 현대자동차그룹 차량용 앱 개발 공모전 출품용 Pleos Connect(Android Automotive OS) 앱 모노레포.
> 팀명·앱 이름: 아가랑. 개인 실명·계정 정보는 이 저장소에 넣지 않습니다(공모전 익명성 규정).

## 1. 앱 이름 및 한 줄 소개

- **앱 이름**: 아가랑
- **한 줄 소개**: 아기와 함께 차로 나설 때, 수유·기저귀·수면 기록으로 출발 시점과 준비물을 예보하고 아기가 울면 가까운 정차지를 내비 경유지로 연결해 주는 Pleos Connect 앱

## 2. 실행 방법

### 2.1 APK로 바로 실행

```bash
adb devices                                  # Pleos Connect 에뮬레이터(emulator-5554 등) 확인
adb install -r apk/아가랑_아가랑_release.apk    # 제출용 release APK 설치(서버 없이 동작)
adb shell am start -n com.hmgchallenge.app/.MainActivity
```

### 2.2 개발 환경 빠른 시작 (클론 → 에이전트·IDE로 열기 → 자동 세팅)

```bash
git clone <저장소 URL> && cd <저장소>
claude        # 또는 codex, 또는 VS Code·Cursor·Android Studio·IntelliJ IDEA로 폴더 열기
```

어느 도구로 열어도 `scripts/setup.sh`(기본 모드)가 자동으로 실행됩니다(수 초, 실패해도 세션·빌드는 계속됨).

- JDK(Android Studio 내장 JBR 등)·Android SDK를 찾고 `local.properties`(`sdk.dir`)를 만듭니다.
- SDK 패키지·Pleos 시스템 이미지·AVD·Gradle wrapper·Context7 MCP 상태를 ✅/⚠️/❌로 요약합니다. 빠진 것은 설치하지 않고 명령만 안내합니다.
- 준비돼 있으면 에뮬레이터(Cold boot)와 백엔드(`:backend:bootRun`, 8081)를 백그라운드로 띄웁니다. 이미 실행 중이면 건너뜁니다.
  로그는 `.dev/`(git 제외), 백엔드 중지는 `scripts/dev-down.sh`(에뮬레이터까지 끄려면 `--emu`). 자동 기동을 끄려면 `HMG_AUTOSTART=0`.

| 도구 | 자동으로 되는 것 | 처음 1회 할 일 |
| --- | --- | --- |
| Claude Code · Codex | 세션 시작 훅이 `scripts/setup.sh` 실행, 요약이 에이전트 컨텍스트에 들어감 | 아래 4번(폴더·훅 신뢰) |
| VS Code | 폴더를 열면 작업 `HMG: 개발환경 세팅`(`.vscode/tasks.json`, `runOn: folderOpen`)이 `scripts/setup.sh` 실행. 추천 확장(Kotlin by JetBrains, Gradle for Java) 설치 알림 | 작업 영역 **신뢰(Trust)** → 자동 작업 질문에서 **허용(Allow)**. 놓치거나 거부했으면 명령 팔레트 `Tasks: Manage Automatic Tasks` → Allow |
| Cursor | VS Code와 같은 `.vscode/tasks.json` | 자동 실행이 안 되면 명령 팔레트 `Tasks: Run Task` → `HMG: 개발환경 세팅` |
| Android Studio · IntelliJ IDEA | Gradle 동기화(첫 import 포함)가 끝날 때마다 Gradle 태스크 `devSetup`이 `scripts/setup.sh` 실행(idea-ext `afterSync`). 공유 실행 구성 `.run/*.run.xml`이 실행 목록에 뜸 | 처음 열 때 **Trust Project**(신뢰하지 않으면 동기화·`devSetup`이 안 돔). 자동 실행이 안 보이면 실행 구성 **`Dev setup (점검 + 자동 기동)`** 1클릭 |

- VS Code·Cursor 작업 목록(`Tasks: Run Task`): 개발환경 세팅/점검, 앱 설치·실행, 백엔드 실행·중지, 에뮬레이터, Virtual Console, 화면 캡처(`.dev/screenshots/`), 전체 빌드(`Cmd/Ctrl+Shift+B`), 전체 테스트.
- Android Studio·IntelliJ 실행 구성(`.run/`): `App (Android)`(Android Studio에서 `:frontend:app` 실행), `App (run-app.sh)`(Android 플러그인이 없거나 AGP를 지원하지 않는 IntelliJ용), `Backend (bootRun :8081)`, `Backend stop (dev-down.sh)`, `Dev setup`, `Setup check`, `Pleos Emulator`, `Virtual Console`, `Build all`.
- `Backend (bootRun :8081)`은 setup.sh가 이미 띄운 백엔드와 포트가 겹칩니다. 디버거로 띄우려면 먼저 `Backend stop`을 실행합니다.
- IDE에서 자동 기동을 끄려면 `~/.gradle/gradle.properties`에 `hmg.autostart=false`(Dock에서 띄운 IDE는 셸의 `HMG_AUTOSTART`를 못 볼 수 있음). 명령줄 `./gradlew build` 등에서는 `devSetup`이 실행되지 않습니다.

처음 1회 사람이 할 일

1. **Android Studio 설치** 후 첫 실행 마법사(Standard)로 Android SDK를 받습니다. SDK Tools에서 **Android SDK Command-line Tools (latest)** 도 설치합니다.
2. **Pleos 시스템 이미지 설치**(라이선스 동의가 필요해서 자동화하지 않음): Android Studio > SDK Manager > SDK Update Sites에 `Pleos Connect System Image`를 추가합니다.
   - macOS(Apple Silicon): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   - Windows·Linux(x86_64): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - SDK Platforms에서 Show Package Details → Android 34의 **Pleos Connect v2.1.0 APIs** 를 체크하고, **42dot 라이선스(pleos-connect-sdk-license)를 직접 읽고 동의**합니다. 다운로드 약 4.7GB(arm64)·4.1GB(x86_64).
   - 원문 절차: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
3. 터미널에서 **`scripts/setup.sh --install`** 을 실행합니다. 빠진 SDK 패키지(platform-tools, emulator, `platforms;android-37.0`, build-tools)를 설치하고(라이선스 질문에 직접 답함), AVD `Pleos_Connect_v2.1.0`(RAM 4GB, Cold boot)을 만들고, Gradle 배포판을 받습니다.
4. 에이전트·IDE에서 프로젝트를 신뢰합니다(IDE는 위 표). Claude Code는 처음 실행 때 폴더 신뢰 창에서 승인(그래야 훅·Context7 자동 승인이 적용됨), Codex는 프로젝트를 trusted로 두고 `/hooks`에서 SessionStart 훅을 검토·신뢰합니다.

에이전트 없이 확인만 하려면 `scripts/setup.sh --check`(점검만, 파일 생성·자동 기동 없음).

### 2.3 소스에서 실행

```bash
# 1) 백엔드 서버 (포트 8081) — setup.sh 기본 모드가 이미 띄웠으면 건너뜀
./gradlew :backend:bootRun
curl http://localhost:8081/api/health

# 2) 에뮬레이터 실행(Cold boot, 이미 실행 중이면 건너뜀) → 앱 빌드·설치·실행
scripts/pleos-emu.sh --wait
scripts/run-app.sh
```

debug 빌드는 에뮬레이터에서 호스트 PC의 백엔드에 `http://10.0.2.2:8081`으로 접속합니다(HTTP 평문은 debug 빌드에만 허용).

8081 포트를 다른 프로세스가 쓰고 있으면 포트를 바꿔 실행합니다.

```bash
./gradlew :backend:bootRun --args='--server.port=9090'
scripts/run-app.sh -Phmg.backendUrl=http://10.0.2.2:9090
```

## 3. 테스트 환경 및 필요한 설정

| 항목 | 값 |
| --- | --- |
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (AVD `Pleos_Connect_v2.1.0`, API 34, Automotive, Cold boot, RAM 4GB) |
| 앱 SDK | minSdk 28 / compileSdk 37 / targetSdk 36 |
| Pleos SDK | Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2` (Maven: `https://nexus-playground.pleos.ai/repository/maven-releases/`) |
| 사용 권한 | `pleos.car.permission.CAR_ENERGY`(배터리 잔량), `NAVI_ROUTE`(경로·ETA 조회), `NAVI_ROUTE_SEARCH`(정차지 경유지 등록). 운전 중 제한(CarUxRestrictions) 구독은 권한이 필요 없음 |
| 차량 API | AAOS `android.car` (`useLibrary("android.car")`, Activity `distractionOptimized` 선언) |
| 글꼴 | Asta Sans v1.000 (42dot, SIL OFL 1.1) 앱 내장 — 아래 "라이선스" 참고 |
| JDK | 17 이상 (개발 환경 확인: Android Studio 내장 JBR 25) |
| 빌드 도구 | Gradle 9.8.2(wrapper), AGP 9.4.1, Kotlin 2.4.21 |
| 백엔드 | Spring Boot 4.1.2, 포트 8081 (개발용. 제출 release APK는 공개 시설·경로 접근성 데이터를 앱에 내장해 서버 없이 동작) |

필요한 설정

1. Android Studio 설치 → SDK Manager에 Pleos Connect System Image(API 34) 추가 → Automotive AVD `Pleos Connect v2.1.0` 생성 (Default boot: **Cold** 필수 — quick boot에서는 VHAL을 쓸 수 없음). 자세한 순서는 2.2 "처음 1회 사람이 할 일". AVD는 `scripts/setup.sh --create-avd`로도 만들 수 있습니다.
2. 레포 루트의 `local.properties`(git에 포함하지 않음)는 `scripts/setup.sh`가 만듭니다. 직접 만들 때:
   ```properties
   sdk.dir=/Users/<사용자>/Library/Android/sdk
   ```
3. JDK 17 이상: `scripts/*.sh`는 `JAVA_HOME` → Android Studio 내장 JBR → `/usr/libexec/java_home` 순으로 찾습니다. 직접 Gradle을 돌릴 때 `JAVA_HOME`이 없으면:
   ```bash
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ```
4. 차량 상태 시뮬레이션은 Virtual Console 사용: `scripts/pleos-console.sh` (주행 상태로 바꾸면 앱 상단 배너가 "주행 중 · 제한 적용"으로 바뀜).
5. 주간/야간 테마는 시스템 야간 모드를 따른다: `adb shell cmd uimode night yes|no`.

## 4. APK 파일 위치 또는 GitHub Release 링크

- 저장소 내 `apk/` 폴더: [`apk/아가랑_아가랑_release.apk`](apk/아가랑_아가랑_release.apk) (release 빌드, v2·v3 서명, 약 49MB)
- GitHub 파일 크기 한도(100MB) 안이라 별도 Releases 링크는 쓰지 않습니다.

---

## 모노레포 구조

```
.
├── apk/                 # 제출용 release APK (apk/아가랑_아가랑_release.apk)
├── frontend/app/        # :frontend:app  Android Automotive 앱 (Kotlin + Jetpack Compose)
├── frontend/sandbox/    # :frontend:sandbox  로컬 테스트용 샌드박스 앱 (아가랑 미니), 제출 대상 아님
├── backend/             # :backend       Spring Boot(Kotlin) API 서버
├── shared/              # :shared        앱·서버 공용 DTO (순수 Kotlin JVM, kotlinx.serialization)
├── scripts/             # 에뮬레이터·Virtual Console·캡처·앱 실행 스크립트
├── .vscode/             # VS Code·Cursor 공용 tasks·추천 확장·설정
├── .run/                # Android Studio·IntelliJ 공유 실행 구성
├── licenses/            # 서드파티 라이선스 전문 (Asta Sans OFL)
├── gradle/libs.versions.toml   # 버전 카탈로그
├── settings.gradle.kts  # 모듈·저장소(Pleos Maven 포함) 설정
└── build.gradle.kts
```

API

| 메서드 | 경로 | 응답 (shared `HealthResponse`) |
| --- | --- | --- |
| GET | `/api/health` | `{"status":"UP","service":"hmg-backend","version":"0.0.1","timestamp":"..."}` |

## 개발 명령

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# 전체 빌드
./gradlew :frontend:app:assembleDebug :backend:build :shared:build

# release APK (서명 설정 후) — 산출물: frontend/app/build/outputs/apk/release/
./gradlew :frontend:app:assembleRelease

# 백엔드 실행
./gradlew :backend:bootRun

# 로그 확인 / Gradle 데몬 종료
adb logcat -s HmgApp
./gradlew --stop
```

에뮬레이터·앱 스크립트(`scripts/`, bash, macOS·Linux 공용). `ANDROID_HOME`이 없으면 macOS `~/Library/Android/sdk`, Linux `~/Android/Sdk` 순으로 찾습니다.

| 스크립트 | 하는 일 |
| --- | --- |
| `scripts/setup.sh [--check \| --install \| --create-avd]` | 개발환경 점검(JDK, SDK, `local.properties`, SDK 패키지, Pleos 이미지, AVD, Gradle wrapper, Context7 MCP)과 ✅/⚠️/❌ 요약. 기본 모드는 `local.properties` 생성 + 에뮬레이터·백엔드 백그라운드 기동(이미 실행 중이면 건너뜀). `--check` 점검만, `--install` 대화형 설치, `--create-avd` AVD만 생성. 환경변수 `HMG_AUTOSTART=0`, `PLEOS_AVD`, `HMG_BACKEND_PORT` |
| `scripts/dev-down.sh [--emu] [--dry-run]` | setup.sh가 띄운 백엔드(및 포트 8081을 듣는 이 레포 backend 프로세스) 중지. `--emu`면 에뮬레이터도 끔 |
| `scripts/pleos-emu.sh [--wait] [emulator 인자...]` | Pleos AVD를 Cold boot(`-no-snapshot-load -no-snapshot-save`)로 백그라운드 실행하고 로그 경로를 출력. 이미 실행 중이면 안내만 하고 종료. `--wait`이면 부팅 완료까지 대기. AVD 이름은 `PLEOS_AVD`로 바꿈 |
| `scripts/pleos-console.sh` | `adb wait-for-device` → `adb forward tcp:8088 tcp:8088` → 브라우저로 Virtual Console(`http://127.0.0.1:8088/`) 열기 |
| `scripts/screenshot.sh [파일]` | 화면 캡처(PNG). 캡처는 git에 넣지 않습니다 |
| `scripts/run-app.sh [gradle 인자...]` | `./gradlew :frontend:app:installDebug` 후 앱 실행 |
| `./gradlew devSetup` | `scripts/setup.sh` 기본 모드를 Gradle 태스크로 실행. IntelliJ·Android Studio가 Gradle 동기화 후 자동 실행(idea-ext `afterSync`). `-Phmg.autostart=false`면 자동 기동 끔 |

## Claude Code·Codex 개발 환경(하네스)

레포를 받으면 아래 파일이 Claude Code와 OpenAI Codex 양쪽에서 그대로 동작합니다. 키가 없어도 됩니다.

| 파일 | 역할 |
| --- | --- |
| `AGENTS.md` | 에이전트 지침 원본(프로젝트 규칙, 명령, 공모전 필수 요건, 문서 우선순위). Codex가 직접 읽음 |
| `CLAUDE.md` | `@AGENTS.md` import + Claude Code 전용 메모 |
| `docs/guides/` | Pleos·Google 차량 가이드 요약(원문 URL 포함, 원문이 최종 기준) |
| `docs/contest/rules.md` | 공모전 규칙·일정·제출물·심사 기준·제출 전 체크리스트 |
| `.mcp.json` | Claude Code용 Context7 문서 MCP(원격 HTTP, `https://mcp.context7.com/mcp`) |
| `.claude/settings.json` | Claude Code 공용 설정: SessionStart 훅(`scripts/setup.sh`), `context7` MCP 자동 승인 |
| `.codex/config.toml` | Codex 프로젝트 설정: Context7 MCP, SessionStart 훅(`scripts/setup.sh`) |
| `.claude/skills/vehicle-ui/` | Compose 차량 UI 작업 절차 스킬(원본) |
| `.agents/skills/vehicle-ui` | Codex용 스킬 경로(원본으로 가는 심볼릭 링크) |

- Claude Code: 처음 `claude`를 실행하면 폴더 신뢰 창이 뜹니다. 승인하면 SessionStart 훅과 `context7` 자동 승인이 적용됩니다. `claude mcp list`로 연결을 확인합니다.
- Codex: 프로젝트를 신뢰(trusted)해야 `.codex/config.toml`이 로드되고, 훅은 `/hooks`에서 검토·신뢰한 뒤 실행됩니다. 훅이 안 돌면 `AGENTS.md` 규칙대로 에이전트가 `scripts/setup.sh --check`를 먼저 실행합니다. `codex mcp list`로 연결을 확인합니다.
- 환경변수(선택): `CONTEXT7_API_KEY` — 없으면 무료·익명으로 동작하고, 있으면 요청 한도가 늘어납니다.
  1. https://context7.com 에서 로그인 후 대시보드에서 API 키(`ctx7sk`로 시작)를 발급합니다.
  2. 셸 설정에 추가합니다(키는 레포에 넣지 않습니다).
     ```bash
     echo 'export CONTEXT7_API_KEY="ctx7sk-..."' >> ~/.zshrc && source ~/.zshrc
     ```
- 개인 설정은 `.claude/settings.local.json`에 둡니다(git에 포함되지 않음).

## 라이선스

- 글꼴 **Asta Sans** (`frontend/app/src/main/res/font/asta_sans_variable.ttf`): Copyright 2024 The Asta Sans Project Authors, SIL Open Font License 1.1. 전문은 [`licenses/AstaSans-OFL.txt`](licenses/AstaSans-OFL.txt).
  - 출처: https://github.com/42dot/Asta-Sans (Google Fonts 배포본 `ofl/astasans/AstaSans[wght].ttf`, google/fonts 커밋 `db2611132856bfec1fa194602b4d1aa6ec1c09e8`), 폰트 버전 1.000, 가변 축 wght 300~800.

## 참고

- release 서명 키는 저장소에 포함하지 않습니다(`*.jks`, `*.keystore`, `keystore.properties`는 gitignore).
- release 빌드는 서버를 쓰지 않습니다(`BUNDLED_FACILITY_DATA=true`, `backend/src/main/resources/facilities/*.json`을 assets로 포함). debug에서 같은 동작을 보려면 `-Phmg.bundledData=true`.
- release 서명은 `HMG_RELEASE_STORE_FILE`·`HMG_RELEASE_STORE_PASSWORD`·`HMG_RELEASE_KEY_ALIAS`·`HMG_RELEASE_KEY_PASSWORD` 환경변수가 모두 있을 때만 적용됩니다.
