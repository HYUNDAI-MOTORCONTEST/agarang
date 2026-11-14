# Pleos SDK 요약 (NaviHelper, FusedLocation, TextToSpeech, SpeechToText, Vehicle 보충)

> 직접 쓴 요약이다. 원문을 옮기지 않았고, 최종 기준은 각 항목의 원문 URL이다. 코드 스케치는 팀이 새로 쓴 것이다.
> 확인일: 2026-10-28 (한국어 페이지 기준). 시그니처는 Gradle 캐시의 AAR을 `javap`로 확인했다(NaviHelper 3.0.5 + navi-data 3.0.4, FusedLocation 1.1.3 + fused-location-data 0.0.3, TextToSpeech 3.3.1, SpeechToText 3.3.1).
> 표기: **[문서]** 원문에 있는 내용, **[문서에 없음]** 다른 근거로 정한 팀 권장(근거를 같이 적음), **[AAR]** AAR 클래스·매니페스트에서 직접 확인한 것.
> Vehicle SDK 기본 패턴·권한 표는 [pleos-dev.md](pleos-dev.md) 5장, 오디오 포커스·선행 묵음은 [pleos-platform.md](pleos-platform.md) 6·8장.
> **2026-11-03 갱신:** 온라인 설명회(10.28)에서 "공모전에서는 SDK 5종을 권한 신청·승인 없이 매니페스트 선언만으로 쓸 수 있다"고 안내했다(`docs/contest/rules.md` 1장). 아래의 "준비 중(신청 불가)"은 Playground·App Market 배포 기준 표기이고, **공모전 앱에서는 TTS·STT·FusedLocation을 써도 된다.** 쓰기 시작할 때 해당 권한을 매니페스트에 선언하고(FusedLocation은 `tools:node="remove"` 줄도 지운다), 에뮬레이터에서 서비스가 실제로 응답하는지 먼저 확인한다.

원문 약칭 (모두 `https://document.pleos.ai/docs/pleos-only` 아래)

| 약칭 | 페이지 |
| --- | --- |
| PROJ | `/create-my-project` (SDK 사용 신청 상태) |
| RN | `/release-note/sdk-261015` (SDK 릴리스 노트) |
| COMPAT | `/vehicle-app-planning-guide/api-compatibility-availability` |
| N-* | `/connect-sdk-pleos/NaviHelper/` + `intro`, `importing-sdk`, `declaring-permissions`, `initialization-and-release`, `routes`, `destinations`, `route-with-waypoints`, `waypoint-settings`, `event-listeners`, `error-handling` |
| F-* | `/connect-sdk-pleos/FusedLocation/` + `intro`, `project-setup`, `declaring-permissions`, `initialization-and-release`, `location-data-access`, `error-handling` |
| T-* | `/connect-sdk-pleos/TextToSpeech/` + `intro`, `app-registration`, `importing-sdk`, `declaring-permissions`, `initialization-and-release`, `set-locale`, `start-and-stop`, `event-listeners`, `error-code` |
| S-* | `/connect-sdk-pleos/SpeechToText/` + `intro`, `app-registration`, `importing-sdk`, `declaring-permissions`, `initialization-and-release`, `set-locale`, `start-and-stop`, `sending-audio-data`, `result-listeners`, `error-code` |
| V-* | `/connect-sdk-pleos/Vehicle/` + `check-capability`, `obtain-domain-object`, `get-property`, `set-property`, `callback`, `vehicle-error-code` |

예: `N-routes` = `/connect-sdk-pleos/NaviHelper/routes`.

---

## 0. 한눈에

| SDK | 버전 (`libs.versions.toml` alias) | Playground 상태 (PROJ, 2026-10-28) | 매니페스트 권한 | 차량 호환 (COMPAT) |
| --- | --- | --- | --- | --- |
| Vehicle (조회) | 2.3.2 (`libs.pleos.vehicle`) | 사용 가능, 신청 불필요 | `pleos.car.permission.CAR_*` (도메인별) | 모델마다 다름 → `checkXxxCapabilities` |
| NaviHelper | 3.0.5 (`libs.pleos.navihelper`) | 사용 가능, 신청 불필요 | `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `NAVI_CUSTOM_MAP` | 내비 탑재 차량만 |
| FusedLocation | 1.1.3 (`libs.pleos.fusedlocation`) | **준비 중(Coming Soon, 신청 불가)** | `FUSED_LOCATION` (dangerous) | 모델마다 다름 → `isSupported()` |
| TextToSpeech | 3.3.1 (`libs.pleos.tts`) | **준비 중(신청 불가)** | `TTS_SERVICE` | 전체 호환 |
| SpeechToText | 3.3.1 (`libs.pleos.stt`) | **준비 중(신청 불가)** | `STT_SERVICE` | 전체 호환 |
| Vehicle Control (set) | — | **준비 중(신청 불가)** | `CONTROL_CAR_*` | — |

- 저장소: 모든 SDK가 `https://nexus-playground.pleos.ai/repository/maven-releases/`(문서). 이 레포는 루트 `settings.gradle.kts`에 이미 등록돼 있고, 버전은 `gradle/libs.versions.toml`에서만 바꾼다.
- [팀 확인 2026-11-03] `NAVI_ROUTE`·`NAVI_ROUTE_SEARCH`(main)와 `FUSED_LOCATION`·`TTS_SERVICE`·`STT_SERVICE`·`FUSED_LOCATION_BINDING`(debug 매니페스트에만, 제출 release에는 없음)을 선언한 debug 빌드로, 에뮬레이터 v2.1.0에서 NaviHelper·TTS·STT는 초기화 성공 콜백을 받았고, FusedLocation은 결과 콜백이 없는 `initialize()`를 예외 없이 호출한 데까지만 확인했다(`frontend/app/src/debug/.../sdk/PleosSdkCheck.kt`, TTS·STT는 `ON_DEVICE` 모드, `FUSED_LOCATION` 런타임 권한 요청은 하지 않음). 에뮬레이터 서비스 버전: Navi Service 3.0.4, FusedLocation Service 1.0.0, `ai.pleos.playground.caas` 4.2.10. 실제 발화·인식·위치 조회는 아직 시험하지 않았다.
- [AAR] 4개 SDK 모두 AAR 매니페스트 minSdk 23(우리 minSdk 28에 문제 없음). 각 AAR은 대응 서비스 최소 버전을 meta-data로 적어 둔다: NaviHelper → Navi Service 2.0.1 이상, FusedLocation → FusedLocation Service 0.5.0 이상, TTS·STT → `ai.pleos.playground.caas` 4.1.29 이상.
- [AAR] TTS·STT AAR은 `INTERNET` 권한을 자동으로 합친다(우리는 이미 선언). TTS·STT는 `ai.pleos.playground.security:universal-access:0.6.0`(앱 인증용, native 라이브러리 포함)에 의존한다.
- 공통 패턴: 객체 생성 → `initialize`(가능하면 결과 콜백 있는 오버로드) → 리스너 등록 → 요청 → 콜백으로 결과 → 리스너 해제 → `release()`. `release()` 뒤에는 같은 객체를 다시 쓰지 않고 새로 만든다(N, F, T, S 문서 공통).

## 1. NaviHelper [문서] — N-*

시스템 내비 앱(Navi Service)에 목적지·경유지·경로 옵션을 요청하고, 내비 정보를 이벤트로 받는다. 요청 결과와 주행 중 변화는 모두 `NaviHelperEventListener`로 온다. 실제로 어떤 이벤트가 오는지는 내비 앱 구현·상태에 따라 다르다.

### 1.1 권한 (N-declaring-permissions)

| 권한 (`pleos.car.permission.`) | API |
| --- | --- |
| `NAVI_ROUTE` | `get*Info()`, `requestTrip()` |
| `NAVI_ROUTE_SEARCH` | `requestRoute()`, `requestRouteWithWaypoints()`, `cancelRoute()`, `requestReRoute()`, `addWaypoint()`, `removeWaypoint()`, `changeRouteOption()` |
| `NAVI_CUSTOM_MAP` | `requestNavigationScreen()` |

- API별 정확한 요구 권한은 API 레퍼런스의 `@RequiresPermission`이 기준이다.
- [문서에 없음] 우리는 쓰는 기능만 선언한다. 경유지 추가가 핵심이면 `NAVI_ROUTE` + `NAVI_ROUTE_SEARCH`. 내비 화면을 직접 띄우지 않으면 `NAVI_CUSTOM_MAP`은 선언하지 않는다. 현재 #5 어댑터가 조회·경유지 추가 권한 두 개를 선언하며 설치별 실제 허용은 호출 전에 확인한다.

### 1.2 초기화·해제 (N-initialization-and-release, N-error-handling)

[AAR] 시그니처
```kotlin
class NaviHelper(context: Context) {
    fun initialize()
    fun initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit)
    fun release()
    fun addListener(listener: NaviHelperEventListener)
    fun removeListener(listener: NaviHelperEventListener)
}
```
- 앱 시작·생성 때 객체를 만들고 `initialize`. 콜백 오버로드 권장: 첫 서비스 연결 성공 시 `onConnected`, 초기 연결 재시도가 모두 실패하면 `onFailed`. 리스너 등록과 API 호출은 `onConnected` 이후에 한다.
- 해제: 리스너 제거 → `release()`.
- 초기 연결 실패는 `onFailed(Throwable)`, 내비 앱의 처리 오류는 리스너 `onError(NaviErrorCode)`로 따로 온다.

### 1.3 목적지·경로 (N-destinations, N-routes)

[AAR] 주요 API (모두 반환값 없음, 결과는 이벤트로)

| API | 하는 일 | 결과 이벤트 |
| --- | --- | --- |
| `requestRoute(RouteInfo)` | 목적지 설정(다시 부르면 변경) | `onRouteStarted`, `onRoutingCompleted`, `onDestinationChanged`, `onError` |
| `getDestinationInfo()` | 목적지 조회 | `onDestinationInfo(DestinationInfo)` |
| `cancelRoute()` | 목적지 취소 | `onRouteCancelled`, `onDestinationChanged` |
| `changeRouteOption(RouteOption)` | 경로 옵션 변경(활성 경로가 있을 때) | `onRouteOptionChanged` |
| `requestReRoute()` | 현재 위치 기준 재탐색(활성 경로가 있을 때) | `onReRouteEnded` |

- `requestRoute()`가 예외 없이 끝나도 탐색 완료가 아니다. 최종 결과는 이벤트로 확인한다. 조회 API도 값을 반환하지 않고 대응 이벤트로 준다.
- `RouteOption`: `RECOMMENDED`, `SHORT_DISTANCE`, `SHORT_TIME`, `FREE`(무료 도로), `HIGHWAY`, `COMFORTABLE`, `SCHOOL_ZONE`(스쿨존 고려).
- [AAR] `RouteInfo` 생성자 순서: `latitude, longitude, displayLatitude?, displayLongitude?, poiName, poiId, address, poiSubId, routeOption, poiProvider, isMobileRequest`(뒤쪽은 기본값 있음). **원문 Java 예제 중 일부는 경도를 첫 인자로 넘기는데, 실제 첫 인자는 위도다.** Kotlin에서는 이름 있는 인자로 넘긴다.
- [AAR] `DestinationInfo`: 위경도, 표시 좌표, `name`, `address`, `remainDistance`, `remainTime`, `tollFare`, `poiId`·`poiSubId`·`poiProvider`.
- [문서, 2026-11-12 추가 확인] [DestinationInfo API Reference](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/entry)는 `remainDistance`를 **미터**, `remainTime`을 **남은 초**, 위경도를 **도**로 설명한다. `remainTime`을 epoch 시각으로 넣지 않고 관측 시각에 더한다. `TripInfo.eta` 등 다른 필드의 단위로 확장 적용하지 않는다.

### 1.4 경유지 (N-waypoint-settings, N-route-with-waypoints)

- **경유지는 최대 2개**: `WaypointIndex.FIRST`, `SECOND`. `ALL`은 전체 삭제용.
- `addWaypoint(RequestWaypointInfo)`로 추가, `removeWaypoint(WaypointIndex)`로 삭제, `getWaypointInfo()` → `onWayPointInfo(List<WaypointInfo>)`. 경유지 도착 시 `onWayPointArrived(WaypointArrivedInfo)`, 변경 시 `onWaypointChanged(WaypointChangedInfo)`.
- 목적지와 경유지를 한 번에: `requestRouteWithWaypoints(RouteWithWaypointsRequest(destination: RouteInfo, waypoints: List<RequestWaypointInfo>))`. 권한 `NAVI_ROUTE_SEARCH`. 결과는 `onRouteStarted`·`onRoutingCompleted`·`onError`.
- [AAR] `RequestWaypointInfo(latitude, longitude, poiName, waypointIndex, poiId, address, poiSubId, poiProvider)`.
- [문서, 2026-11-13 추가 확인] [요청 필드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/request-waypoint-info/entry)·[응답 필드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-info/entry): `poiId`는 특정 지도 POI의 식별자이며 요청에는 기본값이 없다. `poiProvider`는 공급자 이름이고 기본값은 빈 문자열이다. 위경도는 도 단위 지리 좌표, `displayLatitude`·`displayLongitude`는 별도의 nullable 표시 좌표다. 지리 좌표가 시설 진입 지점이라는 보장은 없으며 빈 ID 허용·0,0 처리·표시 좌표 대체도 명시되지 않았다.
- [팀 정책·실측, 2026-11-13] 현재 Pleos 어댑터는 호환되는 공급자가 준 POI ID가 없으면 실제 추가 요청 전에 거부한다. 에뮬레이터 실측 결과, 경유지 미등록 시 `onWayPointInfo`는 빈 목록을 반환하고, 공식 `poiId`로 등록하면 기본 내비 재탐색(`onRouteCancelled`/`SAFE_GUIDING` → `onWaypointChanged(FIRST, ADD)` → `ROUTE_GUIDING`) 후 `WaypointInfo`에 `poiId`·이름·남은 거리·시간을 담아 돌려주지만 서비스(`NavigationUseCase.getWaypointRoutePointItem`) 단계에서 위경도를 `0.0, 0.0`(`displayLatitude/Longitude = null`)으로 생략한다. 따라서 `poiId == null`인 `(0, 0)` 응답은 불확실한 슬롯으로 차단하고 ETA를 유지하며, 유효한 `poiId`가 포함된 `(0, 0)` SDK 응답은 전송 전 목적지와 일치하는 재조회 뒤 동일 `poiId` 비교(비영 좌표 반환 시에는 좌표+`poiId` 동시 비교)로 등록 성공을 확정한다. [후속 재현 기록](../agarang/navigation-repro.md).
- 원문 표기 주의: 콜백 이름이 `onWaypointInfo`/`onWayPointInfo`로 섞여 있다. [AAR] 실제는 `onWayPointInfo`, `onWayPointArrived`, `onWaypointChanged`.

### 1.5 이벤트 (N-event-listeners)

[AAR] `NaviHelperEventListener`의 메서드는 모두 default(빈 구현)라 필요한 것만 override한다.

| 분류 | 콜백 |
| --- | --- |
| 경로 | `onRouteStarted(RouteStartInfo)`, `onRoutingCompleted()`, `onRouteCancelled()`, `onReRouteEnded()`, `onRouteOptionChanged(RouteOption)`, `onRouteEnded()` |
| 목적지·경유지 | `onDestinationChanged()`, `onDestinationArrived(DestinationArrivedInfo)`, `onWaypointChanged(...)`, `onWayPointArrived(...)` |
| 조회 결과 | `onDestinationInfo`, `onWayPointInfo`, `onCurrentLocationInfo`, `onRouteStateInfo`, `onTBTInfo`, `onBookmarkInfo`, `onBookMarkChanged`, `onRecentDestinationInfo`, `onChargerOperatorInfo`, `onTripInfo`, `onNavigationStatus(NavigationStatus)`, `onDrivingInfo`, `onCurrentRoadSpeedLimit(Int)`, `onCameraAlert`, `onCautionPointInfo` |
| 오류 | `onError(NaviErrorCode)` |

- 우리 앱에 쓸모 있는 것 [AAR]: `RouteStartInfo`(duration, distance, tollFare, name), `DestinationArrivedInfo`, `TripInfo`(eta, distance, 도착 예상 SOC 등, `requestTrip(TripRequest)`로 요청), `NavigationStatus`(`INACTIVE`, `INITIALIZING`, `FREE_DRIVE`, `ROUTE_DRIVE`, RN에서 v3.0.5에 바뀜), `CautionPoint`(스쿨존·과속방지턱 등 + 남은 거리).
- [AAR·실기 추가 확인, 2026-11-12] `onRouteEnded()`는 `onRoutingCompleted()`로 대체하도록 폐기된 **탐색 완료** 콜백이다. 에뮬레이터의 내비 서비스 3.0.4가 구형 이름을 보내므로 둘 다 처리한다. 이를 경로 취소로 해석하면 후속 ETA 응답을 버리게 된다. 실제 활성 경로 종료·취소 여부는 상태 조회·취소 이벤트로 구분한다.
- [실기 추가 확인, 2026-11-12~13] 같은 서비스는 `ROUTE_GUIDING` 중 `isRouting=false`를 반환한다. 원문의 flag 설명만으로 안내 여부를 판단하지 않고 `NaviMode`를 조회한다. `NavigationStatus` 변경은 재조회 신호로 사용한다. `poiId` 없이 `(0, 0)` 좌표만 온 경유지 조회는 빈 슬롯으로 버리지 않고 불확실한 정보(`FAILED`/`REQUEST_FAILED`)로 처리해 추가를 차단한다. 반면 `poiId`·이름·남은 거리/시간이 채워진 채 좌표만 `0.0, 0.0`으로 생략돼 오는 에뮬레이터 서비스 응답은 동일 목적지 재조회와 `poiId` 일치를 확인해 등록 완료(`CONFIRMED`)로 확정한다. [현재 구현·검증 경계](../agarang/navigation.md).
- [AAR·공개 시그니처, 2026-11-13] 확인한 `NaviHelper` 공개 표면에는 현재 경로의 전체 폴리라인·방향별 휴게소 접근성을 제공하는 인터페이스가 없다. `TripInfo.summary`의 `RoutePointInfo` 목록은 전체 도로 경로·시설 진입 근거와 구분한다. 확인한 `RoutePointPoiType`은 NORMAL·EV_CHARGING이다. 내부 API는 호출하지 않았다.

### 1.6 오류 (N-error-handling)

| `NaviErrorCode` | 뜻 | 우리 처리 [문서에 없음] |
| --- | --- | --- |
| `CAUSE_LOW_BATTERY` | 배터리 부족 | "배터리가 부족해 경로를 바꿀 수 없어요" 안내 |
| `CAUSE_SEARCH_ROUTE_OVER_DISTANCE` | 탐색 가능 거리 초과 | 경유지 제안 취소 |
| `CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE` | 현재 위치와 너무 가까움 | 경유지 제안 생략 |
| `CAUSE_SEARCH_ROUTE` | 경로 탐색 서버 오류 | 1회 안내 후 수동 재시도 버튼 |
| `CAUSE_SEARCH_TRIP` | [AAR] 문서 표에는 없음. `requestTrip` 실패로 보임 | ETA 표시 생략 |

- 오류 때 확인할 것(문서): 연결 완료 후 호출했는지, 권한 선언, 좌표·POI ID·경유지 인덱스, 현재 내비 상태가 요청을 받을 수 있는지.
- `when`에는 `else` 분기를 둔다(새 코드 추가 대비).

### 1.7 에뮬레이터·제약

- 원문 Emulator setup은 일반 에뮬레이터 설치·Virtual Console·마이크·Cold boot 링크만 있다. NaviHelper 전용 에뮬레이터 동작은 문서에 없다.
- [팀 관찰] 에뮬레이터에 Navi Service(`ai.pleos.playground.service.navi` 3.0.4)와 내비 앱(`ai.umos.maps.android.navigation.app`)이 있다. 2026-11-12~13의 경로 생성·ETA·재연결 검증은 [내비 기록](../agarang/navigation.md), 경유지 0,0 응답과 초기화 지연 복구는 [후속 기록](../agarang/navigation-repro.md)을 따른다. 실제 도착 이벤트는 미검증이다.
- 내비 탑재 여부는 앱이 알 수 없다(FAQ). `onFailed`나 오류가 오면 내비 연동 UI를 숨긴다.

### 1.8 스케치 (팀 작성)
```kotlin
class NaviBridge(context: Context, private val scope: CoroutineScope) {
    private val navi = NaviHelper(context.applicationContext)
    val ready = MutableStateFlow(false)
    val lastError = MutableStateFlow<String?>(null)

    private val listener = object : NaviHelperEventListener {
        override fun onRoutingCompleted() { lastError.value = null }
        override fun onWayPointArrived(info: WaypointArrivedInfo) { /* 도착 안내 */ }
        override fun onError(errorCode: NaviErrorCode) { lastError.value = errorCode.name }
    }

    fun start() = navi.initialize(
        onConnected = { navi.addListener(listener); ready.value = true },
        onFailed = { e -> Log.w("HmgApp", "NaviHelper connect failed", e); ready.value = false },
    )

    /** 사용자가 확인 버튼을 눌렀을 때만 부른다(동의 없는 경로 변경 금지, pleos-platform.md 12장). */
    fun addRestStop(lat: Double, lon: Double, name: String, providerPoiId: String) {
        if (!ready.value || providerPoiId.isBlank()) return
        navi.addWaypoint(RequestWaypointInfo(latitude = lat, longitude = lon, poiName = name,
            waypointIndex = WaypointIndex.FIRST, poiId = providerPoiId, address = "", poiSubId = "0"))
    }

    fun stop() { navi.removeListener(listener); navi.release(); ready.value = false }
}
```
- 이 스케치는 연결·요청의 축약 예다. 운영 구현은 `NavigationRepository`의 접근 근거·기존 슬롯 보존·명시적 확인·결과 재조회를 함께 사용하며 두 슬롯이 차 있으면 추가를 거부한다. 빈 ID는 현재 Pleos 어댑터에서 전송 전에 차단한다. POI ID가 있어도 좌표·접근성이 검증되는 것은 아니다.

## 2. FusedLocation [문서] — F-*

차량 위치(위도·경도·고도·방향·신뢰도·시각)를 Pleos FusedLocation Service에서 받는다. **Playground 표기는 준비 중(신청 불가)이다.** (App Market 배포 기준. 공모전은 선언만으로 사용 가능 — `docs/contest/rules.md` 1장. 제출 앱은 쓰지 않는다.) 아래는 쓰게 될 때를 위한 정리다.

- 권한: `pleos.car.permission.FUSED_LOCATION`(보호 수준 dangerous → 런타임 요청 필요). 대상 API: `getLastKnownLocation`, `registerFusedLocationCallback`, `unregisterFusedLocationCallback`.
- Android `ACCESS_*_LOCATION` 권한 동작과 포그라운드·백그라운드 위치 정책은 이 SDK가 보장하지 않는다. Google Fused Location 문서를 Pleos 동작으로 해석하지 않는다.
- 위치 접근이 차량 개인정보 설정으로 막혀 있으면 `showLocationAccessBlockedPopup()`으로 안내만 할 수 있다(허용해 주는 API가 아님).
- 순서: 객체 생성 → `isSupported()`(false면 위치 기능을 아예 끔) → `initialize()` → 조회·구독 → `unregisterFusedLocationCallback` → `release()`. 두 해제는 서로 대체 불가. 원문 예제는 Application 범위에서 인스턴스 하나를 관리한다.
- `getLastKnownLocation`은 새로 계산하지 않고 마지막 값을 준다. 없으면 `null`.

[AAR] 시그니처
```kotlin
class FusedLocation(context: Context) {
    fun isSupported(): Boolean
    fun initialize(); fun release()
    fun getLastKnownLocation(listener: FusedLocationListener)
    fun registerFusedLocationCallback(listener: FusedLocationListener)
    fun unregisterFusedLocationCallback(listener: FusedLocationListener)
    fun addProximityAlert(request: ProximityAlertRequest, listener: ProximityAlertListener)
    fun removeProximityAlert(alertId: String)
    fun showLocationAccessBlockedPopup()
}
interface FusedLocationListener { fun onChanged(location: LocationData?); fun onError(exception: Exception) }
// LocationData(latitude, longitude, altitude: Double, heading: Float, confidence: Float, timestampMillis: Long)
// ProximityAlertRequest(latitude, longitude, radiusMeters: Float, transitionTypes: Set<ProximityTransition>)  // ENTER, EXIT
// ProximityAlertListener: onRegistered(alertId), onTransition(ProximityAlertEvent), onError(Exception)
```
- 오류: 모두 `onError(Exception)`. `null` 위치를 유효한 값으로 쓰지 않는다. [AAR] 예외는 `LocationException(errorCode)`이고 `ErrorCode` = `ERROR_INTERNAL_SERVICE`, `ERROR_SERVICE_CONNECTION_FAILURE`, `ERROR_DRIVING_RESTRICTED`, `ERROR_EMPTY_RESULT`, `ERROR_SECURITY_PERMISSION`, `ERROR_LOCATION_DISABLED`, `ERROR_UNDEFINED`(문서에는 코드 표가 없다).
- RN v1.1.3: `onChanged` 인자가 nullable로 바뀌었고 `onError` 구현이 필수가 됐다(breaking).
- Proximity alert(영역 진입·이탈)는 소개 페이지에 기능 이름만 있고 사용 가이드 페이지는 없다.
- [팀 관찰] 에뮬레이터에 FusedLocation Service 1.0.0이 있다. debug 빌드의 연결 확인(`PleosSdkCheck`)에서 `initialize()`만 호출했고 위치 조회는 하지 않았다.
- [문서에 없음] 승인 전 대안: 현재 위치가 필요하면 NaviHelper `getCurrentLocationInfo()` → `onCurrentLocationInfo(CurrentLocationInfo)`(위경도·heading·주소, `NAVI_ROUTE`)를 쓴다.

## 3. TextToSpeech [문서] — T-*

텍스트를 Pleos Speech Service에 보내 합성하고, 서비스가 차량 오디오로 재생한다. **현재 준비 중(신청 불가).**

### 3.1 모드 (T-intro, T-app-registration)

| `Mode` | 동작 | 네트워크 | 앱 등록(클라이언트 ID·시크릿) |
| --- | --- | --- | --- |
| `HYBRID` (기본) | 서버 스트리밍 우선, 네트워크 실패 시 온디바이스로 자동 전환 | 서버 쓸 때 필요 | 서버 합성을 쓰려면 필요 |
| `SERVER` | 서버만. 네트워크 실패 시 전환 없이 오류 | 필요 | 필요 |
| `ON_DEVICE` | 차량 온디바이스 엔진만 | 불필요 | **불필요** |

- 클라이언트 ID·시크릿은 Playground My Project에서 발급(PROJ: TTS·STT용).
- [문서에 없음] `ON_DEVICE`는 자격 증명 없이 동작한다고 되어 있지만, `TTS_SERVICE` 권한 자체가 Playground 승인 대상인지는 문서에 없다. "준비 중"인 동안은 선언·호출하지 않는다.

### 3.2 설정·API

- 권한: `pleos.car.permission.TTS_SERVICE` — 초기화·리스너 등록·앱 등록·합성 요청 모두에 필요. 없으면 `PERMISSION_DENIED`.
- 순서: 생성(`TextToSpeech(context, Mode)`) → `initialize(OnInitializeListener)` → `addEventListener` → `setLocale`(선택) → (`HYBRID`/`SERVER`면) `registerApp` 후 `onConnected` 기다림 → `speak` → `stop` → `removeEventListener` → `release`.
- 로케일: `ko-KR`, `en-US`, `fr-CA`, `es-US`, `es-MX`. 호출하지 않으면 같은 앱이 전에 설정한 값, 없으면 시스템 로케일.
- `speak(text, isFinal = true)`: 여러 조각을 한 발화로 보낼 때 마지막 조각만 `isFinal = true`.
- `stop()`: 합성·재생 중지 → `onStop()`.

[AAR] 시그니처
```kotlin
class TextToSpeech(context: Context, mode: Mode = Mode.HYBRID) {
    fun initialize(); fun initialize(listener: OnInitializeListener)   // onSuccess(), onFailure(ErrorType)
    fun addEventListener(l: EventListener); fun removeEventListener(l: EventListener)
    fun registerApp(clientID: String, clientSecret: String, listener: OnServerConnectionListener) // onConnected(), onFailed(ErrorType)
    fun setLocale(locale: Locale)
    fun speak(text: String, isFinal: Boolean = true)
    fun stop(); fun release()
    fun setAudioAttributes(attrs: AudioAttributes)   // 가이드 페이지에는 없음
}
interface EventListener { fun onReady(); fun onStart(); fun onUpdatedRms(rms: Double); fun onDone(); fun onStop(); fun onError(errorType: ErrorType) }
```
- [AAR] `EventListener`는 default 구현이 없어 6개를 모두 override해야 한다.
- [AAR] 가이드에 없는 공개 API도 있다: `setAudioAttributes`, `setSpeakerType`, `setAudioZoneId`, `getAmbientAi()`, UA 인증용 `registerApp` 오버로드 2개. 가이드에 없는 API는 쓰지 않는다(심사 위험). `setAudioAttributes`만 오디오 포커스 확인 뒤 필요하면 검토한다(`pleos-platform.md` 6장).

### 3.3 오류 (T-error-code)

`ErrorType`은 `OnInitializeListener`·`OnServerConnectionListener`·`EventListener`로 온다. 새 값이 추가될 수 있어 `else` 분기를 둔다.

| ErrorType | 뜻 | 대응 요지 |
| --- | --- | --- |
| `PERMISSION_DENIED` | `TTS_SERVICE` 권한 없음 | 매니페스트·실행 환경 권한 확인 |
| `UNAUTHENTICATED` | 앱 인증 실패 | 자격 증명·UA 앱 정보 확인 |
| `NETWORK_ERROR`, `TIMEOUT` | 통신 실패·시간 초과 | 네트워크 회복 후 재시도 |
| `SERVER_INTERNAL_ERROR` | Speech Service 내부 오류 | 잠시 후 재시도 |
| `ON_DEVICE_ENGINE_ERROR` | 온디바이스 엔진 오류 | 엔진 상태 확인, 다른 모드 검토 |
| `UNAVAILABLE_LANGUAGE` | 언어 미지원 | 지원 로케일로 변경 |
| `NOT_SUPPORTED_API` | 현재 모드·환경에서 미지원 | 모드·플랫폼 확인 |
| `INVALID_REQUEST_ERROR` | 요청 값·순서 오류 | 호출 순서 확인 |
| `AUDIO_ERROR`, `MIC_ERROR` | 오디오 출력·장치 오류 | 출력 장치 확인 |
| `UNKNOWN` | 미분류 | 환경 정보 수집 |

### 3.4 우리 앱 적용 [문서에 없음 — 팀 판단]

- 승인 전 시연용 대안: **미리 녹음(또는 미리 합성)한 안내 음원을 앱에 넣고 `MediaPlayer`/`AudioTrack`으로 재생**한다. 포커스(`pleos-platform.md` 6장)·선행 묵음(8장)을 앱이 직접 처리할 수 있고, 권한·자격 증명이 필요 없다. 음원은 저작권이 깨끗한 것(팀 녹음 등)만 쓰고 출처를 `licenses/`에 적는다.
- Android 표준 `android.speech.tts.TextToSpeech`가 Pleos 에뮬레이터·실차에 엔진과 함께 있는지는 문서에 없다. 쓰기 전에 확인하고, 플랫폼 API 우선 원칙(HW)과 충돌하지 않는지 설명회에서 묻는다(9장).
- 승인되면: `ON_DEVICE` 또는 `HYBRID`. 주행 중 안내는 끊기면 안 되므로 네트워크 실패에 자동 전환되는 `HYBRID`가 낫고, 자격 증명을 관리하기 싫으면 `ON_DEVICE`.
- 안내 문장은 짧은 고정 템플릿으로 만들고 `speak` 한 번에 보낸다.

### 3.5 스케치 (팀 작성, 승인 뒤)
```kotlin
class PleosSpeaker(context: Context) {
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private var ready = false
    private val events = object : EventListener {
        override fun onReady() { ready = true }
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = Unit
        override fun onStop() = Unit
        override fun onError(errorType: ErrorType) { Log.w("HmgApp", "TTS error: ${errorType.name}") }
    }
    fun start() = tts.initialize(object : OnInitializeListener {
        override fun onSuccess() { tts.addEventListener(events); tts.setLocale(Locale.KOREA) }
        override fun onFailure(errorType: ErrorType) { ready = false }
    })
    fun say(text: String) { if (ready) tts.speak(text) }   // 실패·미준비면 화면 안내로 대체
    fun stop() { tts.removeEventListener(events); tts.release() }
}
```
- `onReady`가 언제 처음 오는지(초기화 직후인지, 매 요청 전인지)는 문서에 짧게만 있다. 시험 후 고친다.

## 4. SpeechToText [문서] — S-*

음성을 텍스트로 바꿔 돌려준다. **명령 실행은 하지 않는다**(예: "음악 틀어줘"라는 글자만 준다). 앱 기능을 음성으로 실행하게 하려면 Gleo Interface를 쓰라고 안내한다. **현재 준비 중(신청 불가).**

- 모드·앱 등록: TTS와 같다(`HYBRID` 기본, `SERVER`, `ON_DEVICE`. `ON_DEVICE`만 쓰면 등록 불필요).
- 권한: `pleos.car.permission.STT_SERVICE`. 초기화·리스너·앱 등록·인식 요청·오디오 전송 모두에 필요.
- 로케일: TTS와 같은 5개, 같은 기본 동작.
- 순서: 생성 → `initialize(OnInitializeListener)` → `addListener(ResultListener)` → `setLocale` → (서버 모드면) `registerApp` → `request()`(SDK가 차량 마이크를 켬) → `stop()`(마이크 종료, `onEndedRecognition`) → `removeListener` → `release`.
- 리스너는 `request()`·`sendAudio()` 전에 등록한다(이벤트 누락 방지).
- 앱이 가진 PCM을 인식시키기: `sendAudio(ByteArray)`(여러 번 나눠 보내도 됨) → 마지막에 반드시 `completeAudioSend()`. **`ON_DEVICE` 모드에서만** 된다. 예시 기준은 16 kHz·16-bit·mono, 100 ms = 3,200 byte.

[AAR] 시그니처
```kotlin
class SpeechToText(context: Context, mode: Mode = Mode.HYBRID) {
    fun initialize(); fun initialize(listener: OnInitializeListener)
    fun addListener(l: ResultListener); fun removeListener(l: ResultListener)
    fun registerApp(clientID: String, clientSecret: String, listener: OnServerConnectionListener)
    fun setLocale(locale: Locale)
    fun request(); fun stop()
    fun sendAudio(data: ByteArray); fun completeAudioSend()
    fun release()
}
interface ResultListener {
    fun onReady(); fun onStartedRecognition()
    fun onUpdated(stt: String, completed: Boolean)      // completed = true면 최종 결과
    fun onUpdatedEpdData(on: Long, off: Long)           // 발화 시작·끝 위치(입력 버퍼 byte offset)
    fun onUpdatedRms(rms: Double)                       // 입력 크기(dB 아님)
    fun onEndedRecognition(); fun onError(errorType: ErrorType)
}
```
- 오류: `ErrorType` 값은 TTS와 같은 12개(3.3 표). STT에서 `MIC_ERROR`는 마이크 시작·처리 실패로, 센서 개인정보 설정(마이크 차단)을 확인하라고 한다. `AUDIO_ERROR`는 입력 데이터·형식 문제.
- [AAR] 가이드에 없는 `getCallApi()`, `getAmbientAi()`도 공개돼 있다. 쓰지 않는다.
- 에뮬레이터: 원문은 일반 설치 절차와 "마이크 접근 설정"(`configure-pleos-connect-emulator`) 링크만 준다. STT 시험에는 에뮬레이터 마이크 설정이 필요하다.

우리 앱 적용 [문서에 없음 — 팀 판단]
- 승인 전에는 음성 입력을 핵심 시나리오에서 뺀다. 주행 중 조작은 큰 버튼 1탭으로 끝나게 한다(NO_KEYBOARD에도 문제없음).
- 승인되면: 주행 중 "네/아니요" 같은 짧은 확인에만 쓰고, 최종 결과(`completed = true`)만 판정에 쓴다. 인식 원문은 로그에 남기지 않는다(`pleos-platform.md` 4장). 마이크를 쓰는 이유를 첫 실행 때 알린다.
- TTS 안내 직후 `request()`를 부르면 안내 음성이 인식될 수 있다. TTS `onDone` 뒤에 시작한다.

## 5. Vehicle SDK 보충 [문서] — V-* (`pleos-dev.md` 5장에 없는 것만)

- 네 가지 API 타입과 결과 전달:

| 타입 | 이름 규칙 | 결과 |
| --- | --- | --- |
| Capability | `checkXxxCapabilities` | `onComplete(config)` 또는 영역 있는 기능은 `onComplete(config, areaMap)` / `onFailed` |
| Get | `getCurrentXxx` (일부는 `getXxx`, 예: `getEvBatteryStateOfCharge`) | `onComplete(값?)` / `onFailed` |
| Set | `setXxx` | 실패할 때만 `onFailed` (성공 콜백 없음) |
| Callback | `registerXxx` / `unregisterXxx` | 리스너 `onXxxUpdated` / `onFailed` |

- Capability 모델: `CapabilityConfig.accessType`(`NONE`, `READ`, `WRITE`, `READ_WRITE`), `AreaBounds(minValue, maxValue)`, 영역별 `Map<Area, AreaBounds>`(예: `DoorArea(DoorValue.ROW_1_LEFT)`). READ 계열이 아니면 UI를 끄거나 대체 흐름으로. 문서 트리는 `CapabilityConfig`를 돌려주는 새 capability API 기준이다([팀 확인] 구형 오버로드는 deprecated).
- Get 값은 nullable(`Int?` 등)이다. `null`은 "값 없음"으로 처리한다(현재 `MainViewModel`이 그렇게 함).
- 오류 처리: `onFailed`는 Kotlin 표준 `Exception`을 받는다. `VehicleException`이면 `errorCode`(`ai.pleos.playground.vehicle.constant.ErrorCode`)로 나누고, 아닐 수도 있으니 `else`를 둔다.

| ErrorCode | 뜻 | 권장 처리 |
| --- | --- | --- |
| `ERROR_NOT_SUPPORTED_PROPERTY` | 기능 미지원 | UI 비활성·대체 흐름 |
| `ERROR_NOT_SUPPORTED_AREA` | 영역 미지원 | 다른 영역 선택 |
| `ERROR_NOT_SUPPORTED_VALUE_TYPE` | 값 타입 미지원 | 인자 타입 확인 |
| `ERROR_SECURITY_PERMISSION` | 권한 부족 | 매니페스트·`@RequiresPermission` 확인 |
| `ERROR_SERVICE_CONNECTION_FAILURE` | Vehicle 서비스 연결 실패 | `initialize`·에뮬레이터 상태 확인, 재시도 |
| `ERROR_INTERNAL_SERVICE` | 내부 오류 | 재시도·일시 오류 안내 |
| `ERROR_DRIVING_RESTRICTED` | 주행 중 기능 제한 | 주행 제한 안내 |
| `ERROR_EMPTY_RESULT` | 결과 없음 | 빈 상태 UI |
| `ERROR_UNDEFINED` | 정의 안 된 오류 | 로그·재시도 |

우리 앱 적용 [문서에 없음 — 위 표를 현재 코드에 맞춘 팀 권장]
- `MainViewModel.readBattery`는 지금 모든 실패를 같은 문구로 보여 준다. `VehicleException.errorCode`로 나눠 `ERROR_SERVICE_CONNECTION_FAILURE`·`ERROR_INTERNAL_SERVICE`만 재시도하고, `ERROR_NOT_SUPPORTED_PROPERTY`면 배터리 카드를 숨기며, `ERROR_SECURITY_PERMISSION`이면 로그를 남기고 "권한 없음"으로 표시하는 편이 낫다.
- 실차 호환을 위해 처음 한 번 `checkEvBatteryStateOfChargeCapabilities`(권한 `CAR_ENERGY`)로 READ 가능 여부를 본 뒤 조회·구독한다.
- 차속·시동 API는 Vehicle SDK에 없다([팀 확인] Vehicle 2.3.2 AAR에 해당 API 없음, `pleos-platform.md` 13장 FAQ). 속도는 AAOS `PERF_VEHICLE_SPEED`, 주행/정차는 `CarUxRestrictions`.

## 6. 공통 수명주기 규칙 [문서에 없음 — 문서들의 init/release 지침과 현재 `MainViewModel` 방식에서 정한 팀 규칙]

- SDK 객체는 ViewModel(화면 단위) 또는 Application 범위에서 하나만 만든다. Activity 재생성(회전·window 크기 변경)마다 다시 만들지 않는다. Pleos는 window 크기·방향이 자주 바뀐다.
- 해제는 "리스너 해제 → `release()`" 순서. 비동기 콜백이 끝나기 전에 `release()`하지 않는다(V-obtain-domain-object).
- 모든 SDK 호출을 `try/catch`로 감싸고 실패를 상태(`StateFlow`)로 노출해 화면에 보인다. SDK가 없는 기기에서도 앱이 죽지 않아야 한다(`AGENTS.md` 코드 규칙).
- 콜백 스레드는 문서에 없다. 콜백에서는 `MutableStateFlow` 갱신만 하고 UI 작업을 직접 하지 않는다.

## 7. 자격 증명 다루기 [문서에 없음 — `AGENTS.md` 익명성·보안 규칙에서 정한 팀 규칙]

- TTS·STT 서버 모드의 클라이언트 ID·시크릿은 **레포에 넣지 않는다**. 소스·`gradle.properties`(커밋 대상)·`strings.xml`·README·스크린샷·커밋 메시지 모두 금지.
- 넣는 경로: 개발자 PC의 `local.properties`(gitignore 대상) 또는 셸 환경변수 → Gradle에서 읽어 `BuildConfig` 필드로. 값이 없으면 빈 문자열로 빌드하고, 앱은 `ON_DEVICE` 모드로 떨어지게 한다.
  ```kotlin
  // frontend/app/build.gradle.kts 예시(아직 적용 안 함, 코디네이터 결정)
  val pleosClientId = providers.environmentVariable("PLEOS_CLIENT_ID")
      .orElse(providers.gradleProperty("pleos.clientId")).getOrElse("")
  // defaultConfig { buildConfigField("String", "PLEOS_CLIENT_ID", "\"$pleosClientId\"") }
  ```
- 한계: APK에 들어간 값은 디컴파일로 꺼낼 수 있다. 제출용 release APK에 시크릿을 넣어도 되는지는 공모전·Pleos 문서에 없다. 승인 전에는 넣지 않고, 필요해지면 설명회·문의로 확인한다.
- 시크릿이 실수로 커밋되면 Playground에서 재발급하고 git 이력에서 지운다.

## 8. 기획에 영향 주는 사실 정리

- 경유지 최대 2개, 확인한 휴게소 POI 타입 없음 → 공개 시설 목록 외에 현재 여정의 방향·진입 좌표·시간·거리와 호환되는 내비 POI ID를 검증할 공급자가 필요하다. 목록 좌표만으로 추천·등록하지 않는다.
- 내비 미탑재 차량에서는 NaviHelper가 동작하지 않고, 탑재 여부를 알 방법도 없다 → 내비 연동은 실패해도 앱 핵심이 돌아가게.
- TTS·STT·FusedLocation은 제출 앱(1차)에서 쓰지 않는다(Playground 표기 준비 중, 공모전은 선언만으로 사용 가능) → 시연은 녹음 음원 + 버튼 입력 + NaviHelper 현재 위치로 대체 가능하게 설계.
- TTS·STT `ON_DEVICE` 모드는 자격 증명 없이 동작한다고 문서에 있다 → 승인(권한)만 열리면 시크릿 관리 없이 쓸 수 있다.

## 9. 확인 못 한 것·시험할 것

- 모든 지정 페이지(N 10, F 6, T 9, S 10, V 6)는 2026-10-28에 정상으로 읽었다. 자동 생성 API 레퍼런스 하위 페이지는 읽지 않고 AAR로 대신 확인했다.
- NaviHelper: 경로 요청·ETA·재연결과 Binder 콜백 스레드는 2026-11-12~13에 확인했다. 경유지는 변경 이벤트·ID 반영은 확인했지만 0,0 응답 때문에 성공을 확정하지 못했다. 빈 ID 지원·실제 도착 이벤트·현재 경로 시설 접근성은 남았다. `DestinationInfo.remainDistance`·`remainTime`은 공식 API 레퍼런스에서 미터·초 확인(1.3절, 2026-11-12); `TripInfo.eta`는 별도 확인 필요.
- TTS·STT: 에뮬레이터에 CAAS(`ai.pleos.playground.caas` 4.1.29+)가 있는지(2026-10-25에는 이름으로 못 찾음), `TTS_SERVICE`·`STT_SERVICE`가 승인 대상인지, `ON_DEVICE` 엔진이 에뮬레이터에 있는지, 서비스가 오디오 포커스·선행 묵음을 처리하는지.
- Android 표준 TTS 엔진이 Pleos 이미지에 있는지.
- FusedLocation: 승인 시점, proximity alert 사용 조건.
