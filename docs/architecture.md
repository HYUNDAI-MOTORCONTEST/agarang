# 구조 설명서 (2차 PT 기술 Q&A 대비)

> 공모전은 생성형 AI 사용을 허용하지만, 참가자가 전체 구조와 로직을 이해하고 2차 PT 기술 Q&A에서 직접 설명할 수 있어야 한다(`docs/contest/rules.md` 1장).
> 이 문서는 팀원이 코드를 열지 않고도 "무엇이 어디서 어떻게 도는지"를 말로 설명할 수 있게 정리한 것이다. 코드가 바뀌면 같이 고친다.
> 확인일: 2026-11-14

## 1. 한 장 요약

```
[Pleos Connect 에뮬레이터 / 차량]
   │  Car 서비스(AAOS)        Pleos 서비스(Vehicle·Navi)
   ▼                          ▼
frontend/app  ── MainActivity(Compose 화면) ── MainViewModel(상태 보관)
   │                ├ UxRestrictionsMonitor   : 주행/주차 제한 구독 (android.car)
   │                ├ CareRepository          : 돌봄 기록·프로필(앱 내부 파일)
   │                ├ DepartureController     : 출발 예보·출발 선택·준비물·울음 대응
   │                ├ NavigationRepository    : NaviHelper로 경로·ETA 조회, 확인 후 경유지 추가(PleosNavigationClient)
   │                ├ Facility/RouteAccess    : 공개 시설·경로 접근성 데이터(release는 APK assets, debug는 백엔드)
   │                ├ Vehicle SDK             : EV 배터리 잔량 조회·구독
   │                └ PleosSdkCheck (debug만) : NaviHelper·FusedLocation·TTS·STT 연결 확인
   ▼
backend (Spring Boot, 8081, debug 개발용) ── shared (앱·서버 공용 DTO·예보·정차 계획 로직)
```

- 앱은 **일반 Android Activity 앱**이다. Android for Cars 템플릿 앱이 아니다.
- 제출 APK(release)는 **서버 없이** 동작한다. 시설·경로 접근성 데이터는 빌드할 때 APK assets로 넣는다(`BuildConfig.BUNDLED_FACILITY_DATA`).
- 제출 앱이 쓰는 Pleos SDK는 Vehicle(EV 배터리)과 NaviHelper(경로·경유지)뿐이다. FusedLocation·TTS·STT는 debug 빌드의 연결 확인에만 쓴다.

## 2. 모듈별로 무엇을 하나

| 모듈 | 언어·기술 | 역할 | 설명할 때 핵심 |
| --- | --- | --- | --- |
| `frontend/app` | Kotlin, Jetpack Compose | 제출용 차량 앱 | 화면은 상태(StateFlow)를 그리기만 하고, SDK 호출·상태 변경은 ViewModel·Controller가 한다 |
| `frontend/sandbox` | Kotlin, Compose | 로컬 테스트용 앱(제출 대상 아님) | 화면·흐름을 빠르게 실험하는 곳 |
| `backend` | Kotlin, Spring Boot 4 | 개발용 API 서버(포트 8081) | `/api/health`, `/api/facilities`, `/api/facilities/route-access` 3개. 에뮬레이터에서는 `10.0.2.2:8081`. release는 부르지 않는다 |
| `shared` | Kotlin(JVM), kotlinx.serialization | 앱·서버 공용 | 데이터 형식과 예보·정차 계획 로직. 단위 테스트가 여기에 모여 있다 |

## 3. 앱이 켜질 때 일어나는 일

1. `MainActivity.onCreate`
   - Back 정책을 등록한다: 최상위 화면에서 Back이 오면 앱을 끝내지 않고 `moveTaskToBack(true)`로 숨긴다(Pleos 필수 규칙).
   - `setContent`로 Compose 화면(`DepartureApp`)을 띄운다. `VehicleTheme`이 시스템 야간 모드에 맞춰 Pleos 주간/야간 색과 Asta Sans 글꼴을 고른다.
   - `onStart`/`onStop`에서 내비 구독을 시작·중지한다.
2. `MainViewModel` 생성(`init`)
   - `UxRestrictionsMonitor.start()`: Car 서비스에 연결해 주행 제한 상태를 구독한다.
   - 돌봄 기록과 출발 준비 상태를 앱 내부 파일에서 읽는다.
   - 백엔드 상태 확인은 debug(서버 모드)에서만 한다. release는 내장 데이터를 쓰므로 서버를 부르지 않는다.
   - Vehicle SDK `initialize()` → EV 배터리 잔량을 한 번 읽고(실패하면 1.5초 뒤 한 번 더) 변화 리스너를 등록한다.
   - debug 빌드에서만 `PleosSdkCheck.start()`: SDK 4종을 초기화만 해 보고 결과를 표시한다(10초 안에 응답이 없으면 "응답 없음"). release에는 이 코드가 없다.
3. 화면은 `collectAsStateWithLifecycle()`로 상태를 받아 다시 그린다.
4. 화면이 완전히 끝나면 `onCleared()`에서 리스너를 해제하고 SDK를 `release()`한다.

## 4. 운전 중 제한은 어떻게 동작하나

- 플랫폼(Car 서비스)이 주행 상태에 따라 `CarUxRestrictions`를 내려준다. 앱은 지역·속도를 직접 판단하지 않고 **플래그만 본다**.
- `driving/UxRestrictions.kt`가 이 값을 `UxRestrictionsState`로 바꾸고, 화면은 `LocalUxRestrictions.current`로 읽는다.
- 제한 중이면: 상세 정보를 숨기고 한 줄로 줄인다, 로딩 애니메이션 대신 정적 글자, 문자열 길이 제한(LIMIT_STRING_LENGTH)을 지킨다. 키보드·설정·영상은 막는다.
- Car 서비스에 연결하지 못하면 **안전하게 제한 상태로 본다**.
- 시험 방법: Virtual Console에서 기어 D·속도를 주면 배너가 "주행 중 · 제한 적용"으로 바뀐다.

## 5. Pleos SDK를 부르는 공통 방식

- 순서: 객체 생성 → `initialize`(결과 콜백) → 리스너 등록 → 조회·요청 → 결과는 **비동기 콜백**으로 온다 → 리스너 해제 → `release()`.
- 모든 호출을 예외 처리하고 `onFailed`/`onError`에서 화면에 실패를 보여 준다. SDK 서비스가 없는 기기에서도 앱이 죽지 않는다.
- 권한: 쓰는 SDK의 권한을 `AndroidManifest.xml`에 선언한다. 공모전에서는 선언만으로 쓸 수 있고, App Market 배포 때는 승인된 권한만 남긴다.
- SDK별 세부(권한, 시그니처, 오류 코드)는 `docs/guides/pleos-sdk.md`, Vehicle SDK는 `docs/guides/pleos-dev.md` 5장.

## 6. 화면 규칙

- 색·글자 크기는 `ui/theme`의 토큰만 쓴다(하드코딩 금지). Pleos 디자인 가이드 수치 근거는 `docs/guides/pleos-ui.md`.
- 가로(window 2개)·세로(window 1개) 모두 지원하고 화면 방향을 고정하지 않는다.
- 버튼은 주요 버튼 72dp, 그 밖 64dp 이상이다. 누르면 즉시 95%로 줄고 색이 바뀐다(Pleos 인터랙션 규칙).
- 공용 컴포넌트는 `ui/components/`에 모았다. 앞으로 만드는 화면은 이것을 먼저 쓴다. 현재 화면(`care/`·`departure/`)은 각 폴더의 컴포넌트와 `CareGlyph`를 쓰고, 공용 컴포넌트는 `MainActivity`의 버튼에만 쓰였다.

| 파일 | 컴포넌트 | 기획에서 쓸 곳(미구현 기능 포함) |
| --- | --- | --- |
| `Buttons.kt` | `PleosButton`(Filled·Basic, 로딩), `PleosIconButton`, `PleosTileButton`, 누름 효과 `pleosPressable`·`pressScale` | F1 원탭 기록 타일, 모든 버튼 |
| `Layout.kt` | `PleosCard`, `PageNavigation`(하위 화면 Back 처리), `PleosTabs`, `AdaptivePanes`(세로 1단·가로 2단), `ParkedOnly`(운전 중 막기), `SelectableOptionCard`, `SectionTitle` | F2 출발안 비교, F4 정차 플랜, 설정 화면 |
| `Controls.kt` | `PleosCheckRow`, `PleosSwitchRow`, `PleosToggleButton`, `PleosStepper` | F2 준비물, F5 챙김 체크, F3 백색소음·공조, 분유 양 |
| `Feedback.kt` | `PleosToastHost`, `PleosPopup` | 기록 완료 알림, 경유지 추가 확인 |
| `Agarang.kt` | `ForecastLevel`·`ForecastBadge`(여유·주의·임박), `GlanceBar`(주행 중 한 줄 + 버튼 1개), `TimelineRow`, `MetricTile` | F4 울음 대응, F3 방지턱 예고, F5 회고, 다음 수유·착석 시간 |
| `CareGlyph.kt`(팀) | `CareGlyph`·`CareSymbol`: 아가랑 자체 아이콘(수유·기저귀·수면 등, `docs/agarang/icons.md`) | 현재 돌봄·출발 화면 |

- 보조 설명이 있는 컴포넌트(타일·카드·선택 카드·체크/스위치 행·한 줄 안내·타임라인 등)는 `LocalUxRestrictions`를 읽어 운전 중에는 보조 설명을 숨기고 글자 수를 줄인다. 색은 `PleosColors` 토큰만 쓰고, 상태는 색과 글자·아이콘으로 함께 보인다.
- 견본 화면(debug 빌드 전용, 제출 APK에는 없음): `adb shell am start -n com.hmgchallenge.app/.ComponentGalleryActivity`.

## 7. 개발에 쓴 도구 (질문 대비)

- 개발환경 세팅·에뮬레이터 실행·캡처는 `scripts/`의 bash 스크립트로 자동화했다.
- 코드 작성에 AI 코딩 도구를 보조로 썼다. 에이전트용 지침은 `AGENTS.md`·`CLAUDE.md`, 공식 문서 요약은 `docs/guides/`에 있다.
- PT 전에 팀원 각자 이 문서의 3~5장을 말로 설명해 보고, 막히는 곳은 해당 코드를 같이 읽는다.
