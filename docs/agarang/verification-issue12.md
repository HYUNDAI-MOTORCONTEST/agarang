# 핵심 시나리오·Pleos 주행·폼팩터·오프라인 통합 검증 (#12)

검증일: 2026-11-13. 이 문서는 이슈 `#12`의 단위·번들 통합 테스트, 에뮬레이터 실기기 계측, 실제 UI·주행 제한·리소스·오프라인·Back 검증 결과와 미검증 한계를 기록한다.

## 검증 기준 지문 및 환경

| 항목 | 실측 값 |
| --- | --- |
| 기준 Git HEAD | `50953d204067b24557c649f6f30c4875621d0436` |
| 검증 소스 아카이브 SHA-256 | `23c750b68153d2bea8666b960cbc7e5903d2e724a3232d257537ec6cb5755869` |
| 검증 디버그 APK SHA-256 (`app-debug.apk`) | `686b0313d0cf8a9896af36b28e9965ce6df6f6ee39ebdfb136edec6686766b9c` |
| JVM / Gradle | OpenJDK 21 (`Java 21.0.13`) · `./gradlew --rerun-tasks --no-daemon` |
| 에뮬레이터 환경 | `pleos_car` AVD (`Android 14 / API 34`, `Pleos Connect v2.1.0 APIs`, `2560×1440`, `160dpi`, Cold boot `-no-snapshot-load -no-snapshot-save`, `-no-window -gpu swiftshader_indirect`) |
| 로컬 캡처 아카이브(Git 제외) | `.dev/verification/issue12/2026-11-13/` (`issue12-ui`, `issue12-ui-b`, `issue12-ui-c`) |

## 1. 단위·번들 통합 테스트

새로 추가한 고정 시계(`1_794_450_000_000L`)·고정 여정·번들 데이터셋 테스트를 포함해 캐시를 끈 상태(`--rerun-tasks`)로 실행해 모두 통과했다.

- `ScenarioIntegrationTest` (`shared/src/test/kotlin/com/hmgchallenge/shared/ScenarioIntegrationTest.kt`, 4개):
  - `scenarioALongDistanceForecastPackingStopPlanningAndLastRestAreaAlert`
  - `scenarioBSoloDriverShortDriveSingleConfirmationStopAndCooldown`
  - `scenarioCCongestionDelayTwoStageStopExpirationAndCareReturn`
  - `offlineCacheStaleFallbackZeroRecordsAndUnverifiedNavigationStaySafe`
- `ScenarioBundleIntegrationTest` (`backend/src/test/kotlin/com/hmgchallenge/backend/ScenarioBundleIntegrationTest.kt`, 2개):
  - `bundledPublicDatasetsDriveLongDistanceCongestionAndTwoStageStopsWithFixedClock` (번들 `public-catalog.json` 468건 + `route-access.json` `gyeongbu-busan-cheonan` 회랑)
  - `bundledPleosGuideCorridorMatchesVerifiedNavigationPoiAndRejectsUnknownDestination` (`pleos-sdk-guide-corridor` 공식 POI 대조 및 미확인 목적지 거부)
- 실행 결과 합계:
  - `:shared:test`: `195 tests, 0 failures, 0 errors, 0 skipped` (13개 테스트 스위트 파일)
  - `:backend:test`: `12 tests, 0 failures, 0 errors, 0 skipped` (3개 테스트 스위트 파일)
  - `:frontend:app:assembleDebug`: `BUILD SUCCESSFUL`

## 2. 라이브 백엔드 + 에뮬레이터 계측 4종

백엔드 서버(`:backend:bootRun`, 포트 `8081`)를 띄워 `GET /api/health`(`UP`), `GET /api/facilities?mode=public`(`PUBLIC_DATA READY`, 468건), `GET /api/facilities/route-access?mode=public`(`NAVI_SDK READY`, `['gyeongbu-busan-cheonan', 'pleos-sdk-guide-corridor']`) 응답을 확인한 뒤, `frontend/app/build.gradle.kts`의 `-Phmg.testRunner`로 4개 실제 계측 러너를 개별 설치·실행했다.

| 계측 클래스 | 주요 실행 인자 | 실측 결과 (`INSTRUMENTATION_CODE: -1`) |
| --- | --- | --- |
| `ScenarioVerificationInstrumentation` | `-e liveBackendUrl http://10.0.2.2:8081` | `result=passed`, `liveHttpBackendSync=passed`, `offlineCacheFallback=passed`, `permissionDeniedSafe=passed`, `zeroRecordNavFailAndRecreation=passed`, `controllerMultiStageFlow=passed`, `fixtureCleaned=true`, `personalDataPreserved=true` |
| `DepartureSmokeInstrumentation` | 기본 실행 | `result=passed`, `corruptCache=passed`, `drivingGuard=passed`, `persistedChecklist=passed`, `fixtureCleaned=true`, `personalCarePreserved=true` |
| `CryResponseSmokeInstrumentation` | 기본 실행 | `result=passed`, `actualAtomicRecordThenReturn=passed`, `demoCannotRequestRealNavigation=passed`, `demoRecordingAndIsolation=passed`, `drivingRecordGuard=passed`, `routeAccessProviderIntegration=passed`, `uncertainWriteKeepsResponseOpen=passed`, `fixtureCleaned=true`, `personalDataPreserved=true` |
| `NavigationSmokeInstrumentation` | `-e mutateEmulatorRoute true -e officialPoiWaypoint true` + 후속 패시브 | `result=passed`, `activeRouteReconnect=passed`, `confirmedWaypoint=passed`, `coordinateOnlyPreflight=blocked-before-ipc`, `fixtureCleanup=passed`, `routeAccessPlannerPipeline=passed`, `routeIdentity=passed`, `routeRead=passed`, 후속 `initialRoute=EMPTY`, `passive=passed` |

## 3. 실제 `MainActivity` UI·폼팩터·주행 제한·오프라인·Back·리소스 실측

| 항목 | 기대 동작 | 실제 관찰 결과 |
| --- | --- | --- |
| 창 크기 전환(`portrait`/`landscape`) | `screenOrientation` 고정 없이 2종 창(`841×1210dp` / `1682×1210dp`) 적응, 겹침·클리핑 0, 터치 영역 `>= 64dp` | `portrait`(`841×1210dp`, `minTouch=101×72dp`)과 `landscape`(`1682×1210dp`, `minTouch=101×72dp` / `96×65dp`) 모두 `overlaps=0`, `clipped=0`. 세로 초기 화면 스크롤 하단 경계 버튼(`준비물 확인하기`)도 스크롤 후 전체 높이(`>= 64dp`) 확인 (`portrait_day_zero`, `portrait_day_zero_scrolled`, `landscape_day_zero`). |
| 주간 / 야간 테마 | `cmd uimode night no/yes`에서 글자 대비·배경 토큰 일관 유지 | 양 창 크기에서 주간/야간 전환 시 겹침·클리핑 0 및 고대비 다크 서피스 확인 (`landscape_night_home`, `portrait_night_home`). |
| 기록·체크리스트·재실행 복원 | 돌봄 기록 및 준비물 체크 상태가 `force-stop` 재시작 뒤에도 보존 | 홈에서 `수유 기록` 후 `준비물`에서 `수유용품` 체크(`준비물 1 / 5 확인`) → `am force-stop` → 재실행 뒤 `준비물 1 / 5 확인` 유지 확인 (`landscape_day_checklist_checked`, `landscape_day_restored`). |
| 오프라인 폴백 | 백엔드 중단 시 앱 크래시 없이 오류 안내 및 캐시 데이터 유지 | `차량·연결 상태`에서 라이브 `UP · hmg-backend v0.0.1`·공공시설 468건 확인 후 백엔드 종료 → `다시 확인` 탭 시 `오류: ConnectException: Failed to connect to /10.0.2.2:8081` 및 캐시된 `468곳` 표시 유지 (`landscape_day_connections_scrolled_live`, `landscape_day_connections_scrolled_offline`). |
| 주행 중 제한 (`CarUxRestrictions`) | `DO: true UxR: 80` 시 설정/준비물 탭 이탈, 최소 주행 요약 및 단일 선택 울음 대응 표시 | Virtual Console(`http://127.0.0.1:8088/`)로 `Gear=Drive, speed=15` 전환(`DO: true UxR: 80`) 시 `준비물`에서 즉시 이탈해 최소 주행 홈(`13 texts`, `5 clicks`, `minTouch=170×72dp`) 및 세로 주행 화면 전환. `울음 대응`에서 `가까운 정차 선택 · 약 3분` 단일 탭 선택(`데모 졸음쉼터`) 후 `Park`/`speed=0`(`DO: false UxR: 0`) 복귀 확인 (`landscape_day_driving_home`, `portrait_day_driving_home`, `landscape_day_driving_response_selected`). |
| 최상위 `Back` 정책 | 최상위 `HOME`에서 Back 시 `finish()` 없이 `moveTaskToBack(true)`로 숨김 | `KEYCODE_BACK` 전달 시 `finishing=false` 유지, 포커스가 `ai.umos.drivingview/.DrivingViewActivity`로 이동(`mFocusedApp`), `am start` 재호출 시 기존 인스턴스로 `62ms` 내 복귀 확인. 단, Pleos multi-window 우측 슬롯에 다른 표준 앱이 없을 때 시각적 창 자체가 빈 화면으로 사라지지는 않는 다중 창 동작이 관찰됨 (`back_summary.json`, `landscape_day_after_root_back`, `landscape_day_resumed_after_back`). |
| CPU / RAM 한도 | 주행 중 `CPU < 5%`, 주차 중 `CPU <= 25%`, `RAM PSS <= 400 MB` | 주차(`parked`): `PSS 105.82 MB`(`108,357 KB`) / `90.82 MB`(`93,001 KB`), 주행 해제 후 `89.07 MB`(`91,203 KB`), CPU `0.0%`. 주행(`DO: true UxR: 80`): `PSS 90.63 MB`(`92,805 KB`), CPU `0.0%`. |

검증 종료 후 원격 임시 작업공간, 에뮬레이터 내부 앱 파일(`/data/data/com.hmgchallenge.app/files`), 기어/속도(`Park` / `0 km/h`, `DO: false UxR: 0`), 테마(`Night mode: no`)를 초기 상태로 되돌리고 패시브 `NavigationSmokeInstrumentation`(`initialRoute=EMPTY`, `passive=passed`)까지 재확인했다.

## 4. 미검증 항목 (실제 환경 한계)

- **Pleos FormFactorConsole (Host Display Bridge 기반 폼팩터 콘솔)**: 원격 헤드리스 에뮬레이터(`-no-window`, `-gpu swiftshader_indirect`)에는 Host Display Bridge와 FormFactorConsole이 없어 공식 콘솔 UI를 통한 멀티 폼팩터 전환은 직접 검증하지 못했다(`am task resize`를 통한 `841×1210dp` ↔ `1682×1210dp` 창 크기 전환으로 대체 검증).
- **실차 하드웨어·실도로 주행·제출용 release APK**: 실차량 VHAL/센서·실도로 주행 중 턴바이턴 안내와 서명된 release APK 검증은 이번 범위에 포함되지 않았으며, release APK 생성 및 제출 자료 최종화는 `#13` 범위다.
