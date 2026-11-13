# AGENTS.md

현대자동차그룹 차량용 앱 개발 공모전 출품작. Pleos Connect(Android Automotive OS 기반 인포테인먼트)용 Kotlin 모노레포다.
팀명·앱 이름은 **아가랑**이다(README 1항).

> 이 파일이 에이전트 지침의 **단일 원본**이다. Codex는 이 파일을 바로 읽고, Claude Code는 `CLAUDE.md`의 `@AGENTS.md` import로 읽는다.
> 공통 규칙은 여기에만 쓰고, 도구 전용 내용만 `CLAUDE.md`(Claude Code)·`.codex/config.toml`(Codex)에 둔다.

## 세션 시작 시 먼저 할 일

- Claude Code·Codex는 세션 시작 훅으로 `scripts/setup.sh`(기본 모드)를 실행하고, `[개발환경 점검]`으로 시작하는 요약이 컨텍스트에 들어온다.
- **그 요약이 컨텍스트에 없으면** 작업 전에 `scripts/setup.sh --check`를 먼저 실행해 결과를 확인한다(훅이 꺼졌거나, Codex에서 프로젝트·훅을 아직 신뢰하지 않은 경우).
- ❌·⚠️의 "사람이 할 일"(Android Studio 설치, SDK 패키지 설치, Pleos 시스템 이미지 설치·42dot 라이선스 동의)은 사용자에게 전달만 한다. 에이전트가 `sdkmanager --licenses`, `yes |` 등으로 라이선스에 대신 동의하거나 Pleos 이미지를 내려받지 않는다.
- 에뮬레이터·백엔드는 훅이 이미 띄웠을 수 있다. 다시 띄우기 전에 요약의 "실행 중" 줄을 본다. 백엔드를 끌 때는 `scripts/dev-down.sh`.

## 모듈 구조

| 경로 | Gradle | 내용 |
| --- | --- | --- |
| `frontend/app/` | `:frontend:app` | 차량 앱. Kotlin + Jetpack Compose, 일반 Activity 앱. minSdk 28, Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2` |
| `frontend/sandbox/` | `:frontend:sandbox` | 로컬 테스트용 샌드박스 앱 "아가랑 미니"(`com.hmgchallenge.sandbox`), 제출 대상 아님. 실행 `scripts/run-app.sh sandbox` |
| `backend/` | `:backend` | Spring Boot(Kotlin) API 서버, 포트 8081 |
| `shared/` | `:shared` | 앱·서버 공용 DTO(순수 Kotlin JVM, kotlinx.serialization) |
| `apk/` | - | 제출용 release APK(`apk/아가랑_아가랑_release.apk`) |
| `docs/guides/` | - | 차량 UI·개발 가이드 요약(직접 쓴 요약 + 원문 URL) |
| `docs/contest/` | - | 공모전 규칙·일정·제출물·심사 기준·제출 전 체크리스트(`rules.md`) |
| `scripts/` | - | 개발환경 세팅·에뮬레이터·Virtual Console·캡처·앱 실행 스크립트(bash) |
| `licenses/` | - | 서드파티 라이선스 전문(Asta Sans OFL). 번들 글꼴·에셋을 추가하면 여기에 둔다 |
| `gradle/libs.versions.toml` | - | 버전 카탈로그. 의존성 버전은 여기서만 바꾼다 |

## 주요 명령

```bash
scripts/setup.sh                     # 개발환경 점검 + local.properties 생성 + 에뮬레이터·백엔드 자동 기동(훅이 실행)
./gradlew devSetup                   # 같은 setup.sh를 Gradle 태스크로(IntelliJ·Android Studio가 동기화 후 자동 실행)
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # JDK 17+ (setup.sh 요약의 JDK 경로)

./gradlew :frontend:app:assembleDebug :backend:build :shared:build   # 전체 빌드
./gradlew :frontend:app:assembleRelease                              # release APK (서명 설정 필요)
./gradlew :backend:bootRun                                           # 서버 8081 (curl localhost:8081/api/health)

scripts/pleos-emu.sh --wait          # 에뮬레이터 Cold boot 실행 + 부팅 대기 (이미 실행 중이면 안내만)
scripts/run-app.sh                   # installDebug 후 앱 실행(am start)
scripts/pleos-console.sh             # Virtual Console: adb forward 8088 + 브라우저
scripts/screenshot.sh "$TMPDIR/screen.png"   # 화면 캡처 (git에 넣지 않는 경로에 저장)
scripts/dev-down.sh [--emu]          # setup.sh가 띄운 백엔드 중지(--emu면 에뮬레이터도)
adb logcat -s HmgApp                 # 앱 로그
```

| 스크립트 | 하는 일 |
| --- | --- |
| `scripts/setup.sh` | 멱등 점검(JDK, Android SDK, `local.properties`, SDK 패키지, Pleos 이미지, AVD, Gradle wrapper, Context7 MCP) 후 ✅/⚠️/❌ 요약. 기본 모드는 `local.properties`를 만들고 에뮬레이터·백엔드를 백그라운드로 띄운다(이미 실행 중이면 건너뜀). 설치는 안 하고 명령만 안내 |
| `scripts/setup.sh --check` | 점검만(파일 생성·자동 기동 없음) |
| `scripts/setup.sh --install` | 터미널에서 대화형 설치: 빠진 SDK 패키지(sdkmanager, 라이선스는 직접 동의), AVD 생성, Gradle 배포판. Pleos 이미지는 안내만 |
| `scripts/setup.sh --create-avd` | Pleos 이미지가 있으면 AVD `Pleos_Connect_v2.1.0`만 생성(RAM 4096, Cold boot, device `Pleos_Connect_v2_0`) |
| `scripts/dev-down.sh [--emu] [--dry-run]` | 백엔드(`.dev/backend.pid` + 포트 8081을 듣는 이 레포 backend 프로세스) 중지. `--emu`면 에뮬레이터도 끔 |
| `scripts/pleos-emu.sh [--wait]` | Pleos AVD Cold boot 백그라운드 실행. 이미 실행 중이면 안내만 |
| `scripts/pleos-console.sh` | Virtual Console 포워딩 + 브라우저 |
| `scripts/screenshot.sh [파일]` | 화면 캡처(PNG) |
| `scripts/run-app.sh [gradle 인자...]` | `installDebug` 후 앱 실행 |
| `./gradlew devSetup` | 루트 `build.gradle.kts`의 Exec 태스크로 `scripts/setup.sh` 기본 모드 실행. idea-ext `afterSync`로 IDE 동기화 후에만 자동 실행되고 명령줄 빌드에는 끼지 않는다. `-Phmg.autostart=false`면 `HMG_AUTOSTART=0` |

- `scripts/*.sh`는 bash(macOS·Linux 공용, macOS 기본 bash 3.2 호환)다. `ANDROID_HOME`이 없으면 macOS `~/Library/Android/sdk`, Linux `~/Android/Sdk` 순으로 찾고, JDK는 `JAVA_HOME`(17+) → Android Studio 내장 JBR → `/usr/libexec/java_home` 순으로 찾는다(`scripts/_common.sh`).
- `scripts/setup.sh` 환경변수: `HMG_AUTOSTART=0`(자동 기동 끔, 하나만 끄려면 `HMG_AUTOSTART_EMU=0`/`HMG_AUTOSTART_BACKEND=0`), `PLEOS_AVD`(AVD 이름), `HMG_BACKEND_PORT`(기본 8081), `HMG_DEV_DIR`(로그·PID, 기본 `.dev/`, git 제외). 종료 코드는 항상 0이다.
- IDE 설정: VS Code·Cursor `.vscode/tasks.json`(폴더 열 때 setup.sh, 작업 목록), Android Studio·IntelliJ `.run/*.run.xml`(공유 실행 구성) + `devSetup`. 사람용 안내는 README 2.2 표.
- 자동 기동 로그: 백엔드 `.dev/backend.log`, 에뮬레이터 `.dev/pleos-emu.log`, 마지막 점검 결과 `.dev/setup-last.txt`.
- `scripts/pleos-emu.sh`: Cold boot 필수(`-no-snapshot-load -no-snapshot-save`, quick boot에서는 VHAL을 못 쓴다). AVD 이름은 `PLEOS_AVD`(기본 `Pleos_Connect_v2.1.0`), 로그는 `PLEOS_EMU_LOG`(기본 `$TMPDIR/pleos-emu.log`)로 바꾼다. 나머지 인자는 `emulator`에 그대로 넘긴다.
- 에뮬레이터에서 호스트 서버는 `http://10.0.2.2:8081`(debug 빌드만 평문 HTTP 허용). 포트를 바꾸려면 `scripts/run-app.sh -Phmg.backendUrl=http://10.0.2.2:9090`.
- 주행 상태 바꾸기(운전 중 제한 테스트): `scripts/pleos-console.sh`로 연 Virtual Console에서 기어 D·속도를 준다. 이미지가 user 빌드라 `cmd car_service inject-vhal-event`는 막혀 있다(`docs/guides/pleos-dev.md` 6장).

## 공모전 필수 요건 (어기면 안 됨)

전체 규칙·일정·제출 방법·심사 기준·제출 전 체크리스트는 **`docs/contest/rules.md`** 가 기준이다. 제출물·README·익명성과 관련된 작업 전에는 반드시 읽는다.

- **Kotlin만 사용**한다(앱·서버·공용 모두). Java 소스를 추가하지 않는다.
- **minSdk 28 이상**(차량 보안 요건: 서명 키 로테이션·리니지).
- **가로·세로 레이아웃 모두 지원**: `screenOrientation`을 고정하지 않는다. Pleos는 portrait(window 1개)와 landscape(window 2개)를 오가며, 사용자가 window 크기를 바꿀 수 있다.
- **승인된 차량 권한만 선언**: `pleos.car.permission.*`은 쓰는 도메인 것만 선언한다. 승인되지 않은 권한을 선언하거나 해당 API를 부르면 심사를 통과하지 못한다. 현재 선언: `CAR_ENERGY`, `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`(NaviHelper 별도 신청 불필요는 공식 문서 확인, 설치별 실제 권한은 호출 전 확인).
- **제출 APK**: 실행 가능한 release 빌드를 `apk/아가랑_아가랑_release.apk`로 둔다(용량 초과 시 GitHub Releases).
- **README 필수 4항목**: 앱 이름·한 줄 소개 / 실행 방법 / 테스트 환경·필요한 설정 / APK 위치 또는 Release 링크. 이 4개 섹션을 지우거나 순서를 바꾸지 않는다.
- **익명성·보안**: 레포·APK·커밋 메시지·스크린샷에 실명, 연락처, 이메일, 소속, 계정 정보, API 키, 서명 키를 넣지 않는다. 키는 환경변수로만 다루고 `.env*`, `*.jks`, `keystore.properties`는 커밋하지 않는다.

## 문서 우선순위 (충돌하면 위가 이긴다)

1. **Pleos Connect UI 디자인 가이드** — https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro
2. **Pleos 차량 앱 기획·개발 가이드, Pleos SDK 레퍼런스** — https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/intro , https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/intro , https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/
3. **Google Design for Driving / Android for Cars** — https://developer.android.com/design/ui/cars , https://developer.android.com/training/cars
4. **AAOS 운전자 주의 분산 가이드라인** — https://source.android.com/docs/automotive/driver_distraction/guidelines
5. 일반 Android / Jetpack Compose 문서

주의: Pleos Connect 앱은 Android for Cars App Library(템플릿) 앱이 아니라 **일반 Activity 앱**이다.
구글 문서의 템플릿 제약(템플릿 종류, 단계 수 제한, 템플릿 전용 컴포넌트, "자체 Activity 금지" 같은 카테고리 규칙)을 그대로 적용하지 않는다.
구글 문서는 Pleos 문서에 수치나 규칙이 없을 때 보조 근거로만 쓰고, 그렇게 썼다는 사실을 코드 주석이나 PR 설명에 남긴다.

## 문서 조회 방법

1. **먼저 `docs/guides/`의 요약을 읽는다** — 색인 `docs/guides/README.md`.
   - `pleos-ui.md`: 화면 구조, 색·글자·아이콘, Compose 실무 수치
   - `pleos-dev.md`: 주행 규제(CarUxRestrictions), 주의 분산, 화면 모델, Vehicle SDK·권한, 에뮬레이터 주의점
   - `pleos-components.md`: 디자인 가이드 나머지(운전용 설계 원칙, 모션·그림자·인터랙션, 컴포넌트 전체)
   - `pleos-platform.md`: 기획·개발 가이드 나머지(성능 한도, 보안·개인정보, 오디오 포커스·음성 안내, CCP·Back, IME, FAQ, ADB)
   - `pleos-sdk.md`: NaviHelper·FusedLocation·TTS·STT 사용법과 Vehicle SDK 보충
   - `google-cars.md`: Google/AAOS 가이드 요약(보조 근거)
2. 요약에 없거나 세부 API가 필요하면 **Context7 MCP**(서버 이름 `context7`)로 찾는다. 등록: Claude Code `.mcp.json`, Codex `.codex/config.toml`.
   - 도구: `resolve-library-id`(라이브러리 ID 찾기), `query-docs`(질의). Claude Code에서는 `mcp__context7__resolve-library-id`, `mcp__context7__query-docs`로 보인다.
   - Pleos 문서(영어판, 부분 색인): `/websites/document_pleos_ai_en`
     예) `query-docs(libraryId="/websites/document_pleos_ai_en", query="EvBattery getEvBatteryStateOfCharge callback")`
   - AAOS(source.android.com): `/websites/source_android_automotive`
     예) `query-docs(libraryId="/websites/source_android_automotive", query="CarUxRestrictionsManager registerListener")`
   - 그 밖의 라이브러리(Compose 등)는 `resolve-library-id`로 ID를 먼저 찾는다.
   - Context7 색인은 비공식·부분 색인이다. 결과의 `Source:` URL을 원문에서 확인하고 판단한다.
3. 한국어 원문이나 디자인 가이드 세부가 필요하면 해당 URL 한 페이지만 웹 조회 도구로 읽는다.
4. **Pleos 문서 취급 규칙**(Pleos Playground 이용약관 제10조②, 제14조⑤⑪):
   - 원문을 레포에 복사하거나 길게 인용하지 않는다. 직접 쓴 요약 + 원문 URL만 둔다.
   - sitemap 순회 같은 대량·자동 수집을 하지 않는다. 필요한 페이지만 사람이 읽는 수준으로 연다.
5. 요약은 참고용이다. 요약과 원문이 다르면 원문이 맞고, 요약을 고친다.

## 코드 작업 규칙

- 차량 UI(Compose 화면·컴포넌트)를 만들거나 고칠 때는 **`vehicle-ui` 스킬**의 절차를 따른다. 원본은 `.claude/skills/vehicle-ui/SKILL.md`이고, Codex는 같은 폴더를 가리키는 심볼릭 링크 `.agents/skills/vehicle-ui`로 읽는다. 스킬을 고칠 때는 원본만 고친다.
- Vehicle SDK: `Vehicle(context).initialize()` → `getXxx()`로 도메인 객체 획득 → capability/get/set/callback. 결과는 비동기(`onComplete`/`onFailed`)다. 콜백이 끝나기 전에 `release()`하지 않는다. 예: `frontend/app/.../MainViewModel.kt`.
- SDK가 없는 환경이나 권한 오류에서도 앱이 죽지 않게 `onFailed`와 예외를 처리하고, 실패 상태를 화면에 보여 준다.
- 에뮬레이터에서 VIN은 "invalid"로 나온다(이미지 쪽 문제로 보임). 앱 로직이 VIN 값에 의존하지 않게 한다.
- 운전 중 제한: `CarUxRestrictions`를 구독해 동영상·애니메이션·자동 스크롤·키보드 입력·설정 화면을 줄이거나 막는다(`docs/guides/pleos-dev.md`). 구독은 `driving/UxRestrictions.kt`, 화면은 `LocalUxRestrictions.current`로 읽는다.
- 꼭 지킬 플랫폼 규칙(`docs/guides/pleos-platform.md` 0장): 최상위 화면 Back은 `finish()` 대신 `moveTaskToBack(true)`, CCP(로터리) 미지원이면 매니페스트에 선언하고 터치만으로 조작이 끝나야 함, 성능 한도(주행 중 CPU 5% 미만·정차 중 25% 이하·RAM 400MB 이하), 소리를 내면 오디오 포커스 요청·변경 처리 필수.
- Pleos SDK(NaviHelper·FusedLocation·TTS·STT)를 처음 쓸 때는 `docs/guides/pleos-sdk.md`의 상태·권한·초기화·해제 규칙을 먼저 읽는다. TTS·STT 클라이언트 ID·시크릿은 `local.properties`나 환경변수로만 다룬다.
- 테마: 색은 `ui/theme/PleosColors.kt`(Pleos 토큰 주간/야간), 글자는 `ui/theme/Type.kt`(Asta Sans + Pleos 위계). 화면 코드에 색·`fontSize`를 하드코딩하지 않는다.
- 변경 후 최소 검증: `./gradlew :frontend:app:assembleDebug`(앱), `./gradlew :backend:build`(서버), `./gradlew :shared:build`(공용).
- git commit·push는 사용자가 요청할 때만 한다.

## 에이전트 하네스 (Claude Code · Codex)

| 항목 | Claude Code | Codex |
| --- | --- | --- |
| 지침 | `CLAUDE.md`(첫 줄 `@AGENTS.md` import + Claude 전용 메모) | `AGENTS.md`(이 파일) |
| Context7 MCP | `.mcp.json` + `.claude/settings.json`의 `enabledMcpjsonServers: ["context7"]` | `.codex/config.toml`의 `[mcp_servers.context7]` |
| 스킬 | `.claude/skills/vehicle-ui/`(원본) | `.agents/skills/vehicle-ui` → 원본 심볼릭 링크 |
| 세션 시작 훅 | `.claude/settings.json` `SessionStart`(matcher `startup`) → `scripts/setup.sh` | `.codex/config.toml` `[[hooks.SessionStart]]`(matcher `startup`) → `scripts/setup.sh` |
| 처음 1회 | 폴더 신뢰(workspace trust) 창에서 승인해야 프로젝트 설정·MCP 자동 승인이 적용됨 | 프로젝트를 신뢰(trusted)해야 `.codex/` 설정이 로드되고, `/hooks`에서 훅을 검토·신뢰해야 실행됨 |

- `CONTEXT7_API_KEY`(선택): 셸 환경변수로만 준다. 없으면 무료·익명으로 동작한다.
- Windows에서 클론하면 심볼릭 링크가 일반 파일이 될 수 있다(`core.symlinks`). 팀 개발 환경은 macOS·Linux 기준이다.
