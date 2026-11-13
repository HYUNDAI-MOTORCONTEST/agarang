# NaviHelper 에뮬레이터 후속 검증 기록

검증 시작: 2026-11-12, 후속 확인: 2026-11-13. 개인 계정·연결 주소·키는 기록하지 않는다.

## 환경과 기본 내비 복구

- Pleos Connect v2.1.0의 공식 기기 프로필: 2560×1440·160dpi, 강제 크기/밀도 변경 없음.
- NaviHelper 3.0.5, navi-data 3.0.4, 에뮬레이터 Navi Service 3.0.4, 기본 내비 앱 3.3.2-rc5-hotfix.3.
- 앱의 차량 권한은 기존 CAR_ENERGY·NAVI_ROUTE·NAVI_ROUTE_SEARCH이며 새 권한을 추가하지 않았다.
- 기본 내비는 ‘실행이 지연되고 있습니다’ 화면에 머물렀다. 같은 사용자 프로필의 내비 서비스·내비 앱 프로세스는 실행 중이었다.
- 기본 내비의 ‘다시 시도’ 이후 초기 화면으로 진입했고, SDK 읽기 검증에서 EMPTY·SAFE_GUIDING을 확인했다. 이 관측은 초기화 화면 정체와 조회 응답 실패가 함께 회복됐다는 뜻이며 내부 원인을 확정한 것은 아니다. 약관·계정·SDK 이미지·기존 기록은 변경하지 않았다.

## 경로·경유지 실측

실제 차량이 아닌 테스트 에뮬레이터에서 수행했다. 변경 검증은 `mutateEmulatorRoute=true`가 명시되고, 경로가 비어 있거나 이전 검증이 만든 동일한 공개 예제라고 확인된 경우에만 진행한다. 다른 기존 경로를 덮어쓰지 않는다.

1. 공식 [경유지 가이드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/waypoint-settings)의 공개 예제 목적지를 설정했다. 좌표는 위도 37.48544722·경도 127.03636666이며 POI ID는 예제 값을 사용했다.
2. READY·ETA 수신과 활성 경로 재연결을 다시 확인했다. 첫 응답은 남은 60초·50m였다. 단위 변환·전달을 확인한 결과이며 실제 도로상의 시간·거리 정확도를 검증한 것은 아니다.
3. 기존 좌표 전용 경유지 요청(POI ID 없음)은 추가 변경 이벤트 뒤 `WaypointInfo`에 0,0을 반환했다. 전송 후 결과는 OUTCOME_UNKNOWN으로 남겼으며 성공으로 표시하거나 재전송하지 않았다.
4. SDK가 경유지를 추가하는 중 ROUTE_GUIDING → SAFE_GUIDING → ROUTE_GUIDING으로 전환하고 취소·재탐색 이벤트를 보내는 경우를 관측했다. 같은 목적지의 재탐색과 다른 목적지로의 전환을 구분해 결과를 확인해야 한다.
5. 공식 POI ID와 좌표를 함께 사용하는 후속 검증에서는 반환 목적지의 ID·이름은 예제와 일치했지만 위도 37.41289678·경도 127.09383464로 돌아왔다. 표시용 좌표는 null이었다. 문서 예제의 좌표와 다르다는 관측이며, 그 원인을 POI 변경이나 SDK 오류로 확정하지 않았다. 이전 READY만 확인하던 검증과 별도로, 빈 경로에서 이 실행이 생성했는지와 반환 ID·유효한 지리 좌표를 검사하도록 강화했다.
6. 공식 경유지 예제의 POI ID(`36khp2hdj2cfcm5nqhe3`)·좌표를 함께 보낸 뒤 `adb logcat`과 계측 콜백으로 확인한 결과, 기본 내비 앱(`ai.umos.maps.android.navigation.app`)의 `NavigationUseCase.getWaypointRoutePointItem` 자체가 경유지 `RoutePointItem(..., point=GeoPoint(latitude=0.0, longitude=0.0), poiID=36khp2hdj2cfcm5nqhe3, remainTimeSec=3, remainDist=12.1)`을 생성하고 이를 그대로 `WaypointInfo(name=소프트웨어드림센터, address=수정구 시흥동, displayLatitude=null, displayLongitude=null, longitude=0.0, latitude=0.0, remainDistance=10, remainTime=60, poiId=36khp2hdj2cfcm5nqhe3, poiSubId=null)`로 내려 보내고 있었다. 경유지가 없을 때는 `onWayPointInfo`가 빈 목록(`[]`)을 반환하며, 등록 직후에는 `onRouteCancelled`/`SAFE_GUIDING` → `onWaypointChanged(FIRST, ADD, start=소프트웨어드림센터/10m/60s)` → `ROUTE_GUIDING`(목적지 남은 거리 50m → 90m) 순서로 재탐색된 뒤 위 `WaypointInfo`가 수신된다.

이 원인이 확정됨에 따라, 어댑터/저장소는 `poiId == null`인 미확인 `(0, 0)` 응답은 계속 `FAILED`/`REQUEST_FAILED`로 차단해 ETA를 보존하되, `DataOrigin.NAVI_SDK`에서 유효한 `poiId`가 채워진 `(0, 0)` `WaypointInfo`가 **전송 전 목적지와 동일한 목적지 키** 위에서 재조회될 때는 `poiId` 일치로 슬롯을 식별하고 `CONFIRMED`로 확정하도록 보완했다. 비영 좌표가 반환되는 서비스에서는 여전히 좌표와 `poiId`가 모두 일치해야 확정한다. 개인 돌봄·준비물 파일(`personalDataPreserved=true`)과 기본 내비 설정·약관은 검증 전후 그대로 유지됐다.

2차 검증 중 `NavigationSmokeInstrumentation`에 추가했던 `requestTrip` 진단 프로브는 `CAUSE_SEARCH_TRIP` 오류로 공유 `NaviHelper`를 해제해 `finally`의 `cancelRoute()`를 막는 부작용을 확인했고, 프로브를 제거한 최종 `NavigationSmokeInstrumentation`에서는 `mutateEmulatorRoute=true`, `officialPoiWaypoint=true` 실행이 `initialRoute=EMPTY`에서 출발해 `confirmedWaypoint=passed`와 `fixtureCleanup=passed`(`15-fresh-empty-safe-guiding-replies`)까지 모두 통과했다.

## 이번 보완

- 경로 공급자가 내비용 POI ID를 제공하면 시설 접근 근거 → 후보 → SDK 요청·결과 비교까지 보존한다. 공공시설 ID를 내비 POI ID로 변환하거나 만들어 넣지 않는다.
- 현재 Pleos 어댑터는 내비 POI ID가 없는 실제 경유지 요청을 로컬 사전 검사에서 차단한다. 좌표 전용 요청의 지원 여부가 공식 문서에 없고 현재 환경에서 올바른 결과를 확인하지 못했기 때문이다. 일반 공급자·데모 계약의 좌표 처리까지 일괄 금지한 것은 아니다.
- 로컬 검사로 미전송을 확정한 실패는 FAILED로 표시하고 ETA·읽기 연결은 유지한다. SDK 전송 뒤 발생한 불명확한 실패는 계속 OUTCOME_UNKNOWN이며 자동 재전송하지 않는다.
- 결과 확인은 목적지와 경유지 두 조회가 끝나고 전송 전 목적지 키와 현재 목적지 키가 일치한 경우만 수행한다. SDK가 같은 목적지의 안내를 잠시 종료·재생성(`SAFE_GUIDING` → `ROUTE_GUIDING`)해도 확인할 수 있고, 다른 목적지에 같은 경유지가 나타났다고 이전 요청을 성공 처리하지 않는다.
- 제공된 `poiId`가 없거나 불일치한 `(0, 0)` 경유지 응답은 계속 차단/미확인으로 유지한다. `DataOrigin.NAVI_SDK`에서 `(0, 0)` 좌표에 `poiId`가 채워져 반환되면 동일 목적지 재조회와 `poiId` 일치(및 기존 슬롯 1 보존)로 등록 성공(`CONFIRMED`)을 확정하며, 비영 좌표가 반환될 때는 진입 좌표와 `poiId`가 모두 일치해야 확정한다. 선택한 시설의 `poiId`가 바뀌어도 이전 선택은 무효화한다.

## #6의 경로 접근성 공급자 경계

공개 SDK 표면의 `TripInfo.summary`는 교통 요약과 `RoutePointInfo` 목록을 제공한다. 확인한 공개 메서드에는 현재 안내 경로의 전체 폴리라인·방향별 휴게소 목록·시설 진입 가능성·남은 모든 휴게소의 완전한 범위를 제공하는 인터페이스가 없다. 내부 API를 호출하거나 공공시설 중심 좌표에서 이 정보를 추정하지 않는다.

이후 #6에서 백엔드 `/api/facilities/route-access`(`RouteAccessCatalog`)와 앱 `RouteAccessRepository`·`MainViewModel`·`DepartureController` 파이프라인을 연결해, 목적지 POI ID·목적지 좌표·남은 거리로 식별되는 검증 회랑에 한해 지나친 시설 제외·반대 방향 제외·`navigationLocation`/`navigationPoiId` 보존·`NaviHelper.addWaypoint` 경유지 등록까지 동작하도록 구현·검증했다(`routeAccessPlannerPipeline=passed`). 미등록 임의 경로는 여전히 `UNSUPPORTED`(`ACCESS_UNVERIFIED`)로 안전하게 유지하며, 외부 길찾기 경로를 차량 내비의 현재 경로와 같다고 자동 가정하지 않는다. [시설 데이터와 정차 계획](facilities.md).

## 공식 문서의 범위

[NaviHelper 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)와 [경유지 가이드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/waypoint-settings)는 결과를 콜백으로 확인하도록 하며 가이드 예제에는 POI ID가 있다. 빈 ID·경유지 없음의 구체적 응답·0,0 처리 계약은 설명하지 않는다.

[RequestWaypointInfo](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/request-waypoint-info/entry)와 [WaypointInfo](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-info/entry)의 필드 표는 브라우저에서 렌더링한 뒤 확인했다. 처음 텍스트 추출에 표가 없었던 것은 문서가 비어 있다는 증거가 아니었다.

문서는 `poiId`를 지도상 특정 POI의 식별자로, `poiProvider`를 공급자 이름으로 설명한다. 요청의 ID는 기본값 없는 문자열이고 공급자는 빈 문자열 기본값이 있다. 위경도는 도 단위의 지리 좌표이며, 응답의 표시용 위경도는 별도 nullable 필드다. 지리 좌표가 시설의 진입 지점이거나 도로에 맞춘 지점이라는 보장은 이 설명에 없다. 빈 ID 허용·0,0의 의미·표시용 좌표 대체 규칙도 확인되지 않았다. 필요한 페이지와 캐시 AAR의 공개 시그니처만 확인했으며 원문·SDK 구현을 복제하지 않았다.

## 검증 결과

- GCP 임시 폴더에서 `:shared:build`, `:backend:build`, `:frontend:app:assembleDebug`, `:frontend:app:assembleDebugAndroidTest`를 통과했다. **공용(`:shared:test`) 189개 테스트 완료(실패 0)** 및 `:backend:test` 통과. `NavigationRepositoryTest`에는 `(0, 0)` + `poiId == null` 차단 유지(`placeholderWaypointBlocksWritesWithoutDiscardingEta`), `poiId`가 포함된 `(0, 0)` SDK 응답의 재탐색 후 등록 확정·중복 방지·1번 슬롯 보존(`sdkPoiIdentifiedZeroCoordinateWaypointConfirmsRegistrationAndPreventsDuplicate`), `poiId` 미일치·누락 차단(`sdkZeroCoordinateWaypointWithoutMatchingPoiIdStaysUnconfirmed`), 비영 좌표 충돌 시 `poiId`가 같아도 미확정 유지(`sdkNonZeroConflictingCoordinateWithMatchingPoiIdDoesNotConfirm`)를 포함한다.
- Pleos Connect v2.1.0 에뮬레이터에서 실제 경유지 변경 계측(`NavigationSmokeInstrumentation -e mutateEmulatorRoute true -e officialPoiWaypoint true`)을 빈 경로(`initialRoute=EMPTY`)에서 실행해 목적지 생성·ETA 수신(`60s`, `50m`)·활성 경로 재연결·공식 `poiId`(`36khp2hdj2cfcm5nqhe3`) 경유지 실제 등록 성공(`confirmedWaypoint=passed`)·자동 취소 후 15회 `EMPTY`/`SAFE_GUIDING` 복원(`fixtureCleanup=passed`)까지 `result=passed`(`INSTRUMENTATION_CODE: -1`)를 확인했다.
- 이어서 실행한 읽기 전용 계측(`NavigationSmokeInstrumentation` 기본 실행)은 `initialRoute=EMPTY`, `passive=passed`, `result=passed`(`INSTRUMENTATION_CODE: -1`)로 통과했고, 실제 Android `CryResponseSmokeInstrumentation` 회귀 검증도 `result=passed`, `personalDataPreserved=true`, `fixtureCleaned=true`(`INSTRUMENTATION_CODE: -1`)로 통과했다.
- 검증 종료 후 에뮬레이터 상태 `2560×1440`, `160dpi`, `DO: false`, `UxR: 0`, `Night mode: no`, 선언 권한 3종(`CAR_ENERGY`, `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`) 부여를 확인했고 임시 검증 디렉터리를 정리했다. 실제 차량·실제 도로 시설 접근성(#6 범위)은 여전히 별도 검증 범위다.

산출물 위치: `.dev/verification/navigation/2026-11-13/`(Git 제외). `summary.json`, `final-device-summary.json`, `mutation-instrument.txt`, `passive-instrument.txt`, `cry-regression-instrument.txt`, `waypoint-probe-logcat.txt` 및 이전 단계의 `verification/`·`earlier-phases/` 기록을 보관한다.
