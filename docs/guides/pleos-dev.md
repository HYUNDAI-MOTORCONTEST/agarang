# Pleos 차량 앱 기획·개발 요약 (주행 규제, 화면 모델, Vehicle SDK, 에뮬레이터)

> 직접 쓴 요약이다. 원문을 옮기지 않았고, 최종 기준은 각 항목의 원문 URL이다.
> 확인일: 2026-10-25 (한국어 페이지 기준)
> 표기: **[문서]** 원문에 있는 내용, **[문서에 없음]** 다른 근거로 정한 팀 권장(근거를 같이 적음).
> 관련 요약: 오디오 포커스·CCP·IME·성능·보안·FAQ·ADB는 [pleos-platform.md](pleos-platform.md), NaviHelper·FusedLocation·TTS·STT와 Vehicle 오류 코드는 [pleos-sdk.md](pleos-sdk.md).

원문 약칭 (모두 `https://document.pleos.ai` 아래)

| 약칭 | 페이지 |
| --- | --- |
| DD | `/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline` (주행 안전을 고려한 설계) |
| POLICY | `/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy` (지역별 주행 규제 정책, URL 철자 원문 그대로) |
| CATEGORY | `/docs/pleos-only/vehicle-app-planning-guide/app-category` |
| SCREEN | `/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model` (앱 구조 및 동작 모델) |
| DR | `/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide` (주행 규제 개발 가이드) |
| VEH | `/docs/pleos-only/connect-sdk-pleos/Vehicle/intro` |
| PERM | `/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission` |
| SDK | `/docs/pleos-only/connect-sdk-pleos/` |
| PROJ | `/docs/pleos-only/create-my-project` |
| EMU | `/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator`, `.../configure-pleos-connect-emulator`, `.../use-vehicle-emulator` |
| FF | `/docs/pleos-only/app-test-guide/form-factor-test-guide` |

---

## 1. 앱 구조·화면 모델 [문서] — SCREEN

- 화면 = Driving view(고정, 앱이 가리거나 끌 수 없음) + 앱 영역(window 1개 또는 2개).
- `screenOrientation`을 지정하지 않으면 portrait로 실행되고 window 1개를 쓴다. landscape로 지정하면 window 2개를 쓴다. 방향 동작은 `AndroidManifest.xml`의 `screenOrientation`을 따른다.
- portrait에는 핵심 기능·주요 정보, landscape에는 넓어진 공간에 부가 정보·확장 기능을 둔다. 전환할 때 우선순위와 배치가 자연스럽게 바뀌도록 반응형으로 만든다.
- 포그라운드·백그라운드 전환이 잦다(화면 분할, window 이동, 다른 앱 실행, 주행 상황 변화).
  - 포그라운드로 돌아오면 상태를 복원하고 현재 크기·방향에 맞게 UI를 다시 구성한다.
  - 백그라운드로 가면 화면 갱신, 애니메이션, 불필요한 센서 수집을 멈춘다.
  - 백그라운드에서 계속할 작업(오디오, 통신, 다운로드)은 실행·중단·재개 조건을 정해 둔다.
  - 백그라운드 상주, 타이머, 폴링, 네트워크 요청을 최소화한다.
  - 프로세스가 다시 만들어져도 사용자 데이터와 진행 상태를 복원한다.

Compose 적용: 상태는 ViewModel + `SavedStateHandle`/저장소에 두고, 차량 구독은 `Lifecycle.State.STARTED` 기준으로 시작·중지한다(`collectAsStateWithLifecycle`, `repeatOnLifecycle`). [문서에 없음 — Android 표준 방식]

## 2. 주행 규제 (CarUxRestrictions)

### 2.1 앱 사용 가능 여부 [문서] — POLICY
- 규제는 차량 신호(속도·기어·브레이크)와 지역 규정으로 정해진다. 플랫폼 버전·양산 차량마다 다를 수 있다.
- **Distraction Optimization(DO)을 선언하지 않은 앱: 주차 중에는 사용 가능, 주행 중에는 사용 불가.**
- DO를 선언한 앱도 규제가 켜지면 `CarUxRestrictions`와 `ActiveRestrictions`를 확인해 기능별 제한을 직접 적용해야 한다(DR).
- DO 선언 방법은 Pleos 문서에 없다. [문서에 없음] AAOS 기준으로 Activity의 `<meta-data android:name="distractionOptimized" android:value="true"/>`를 쓴다(`google-cars.md` 4장). 주행 중에도 쓰게 할 화면에만 선언하고, 그 화면은 아래 제한을 모두 처리해야 한다.

### 2.2 구현 [문서] — DR
- `CarUxRestrictionsManager`에 리스너를 등록하고 `onUxRestrictionsChanged()`로 바뀐 값을 받는다.
- `isRequiresDistractionOptimization()`이 참이면 `getActiveRestrictions()` 비트마스크를 보고 기능을 막는다(예: `UX_RESTRICTIONS_NO_VIDEO`이면 영상 정지).
- 지역별 규제는 플랫폼이 `ActiveRestrictions`로 내려주므로 앱은 받은 값만 반영하면 된다. 지역을 직접 판단하지 않는다.
- UX 규제 Figma 사양서는 공개되어 있지 않다.
- [문서에 없음] `CarUxRestrictionsManager`를 얻는 Gradle·코드 설정은 Pleos 문서에 없다. AAOS 방식으로 구현했다(2026-10-25):
  - Gradle: `android { useLibrary("android.car") }` (AGP 9.4.1에서 동작 확인). 매니페스트 `<uses-library android:name="android.car" android:required="false"/>` — API 34 이미지는 car 모듈이 bootclasspath에 있어 없어도 되지만 예전 이미지 대비.
  - 코드: `Car.createCar(context, handler, CAR_WAIT_TIMEOUT_DO_NOT_WAIT, listener)` → `getCarManager(Car.CAR_UX_RESTRICTION_SERVICE)` → `registerListener`. `frontend/app/.../driving/UxRestrictions.kt`.
  - 권한: UX 제한 조회·구독에는 `android.car.permission.*`이 필요 없다. 추가하지 않았다.
  - `android.car`가 없거나 연결에 실패하면 앱은 죽지 않고 "주행 상태 확인 불가 · 제한 모드"로 표시한다(모르면 안전한 쪽).

### 2.3 기능별 플래그와 지역별 허용 [문서] — POLICY

| 기능 | 플래그(값) | 북미 주행 중 | 유럽 주행 중 | 그 외 지역 주행 중 |
| --- | --- | --- | --- | --- |
| 키보드 | NO_KEYBOARD(8) | 제한 | 허용 | 허용 |
| 다이얼패드 | NO_DIALPAD(1) | 제한 | 허용 | 허용 |
| 통합 검색 | NO_KEYBOARD(8), NO_FILTERING(2) | 음성만 | 허용 | 허용 |
| 미디어 앱 로그인·설정·검색 | NO_SETUP(64), NO_KEYBOARD(8), NO_FILTERING(2) | 제한 | 제한 | 허용 |
| 디지털키·도어·시트·차량 설정 | NO_SETUP(64) | 제한 | 제한 | 제한 |
| 동영상 시청 | NO_VIDEO(16) | 제한 | 제한 | 제한 |
| 내비 검색 | NO_KEYBOARD(8), NO_FILTERING(2) | 제한 | 허용 | 허용 |
| QR 로그인 | NO_SETUP(64) | 제한 | 제한 | 제한 |
| 매뉴얼(긴 텍스트) | LIMIT_CONTENT(32), LIMIT_STRING_LENGTH(4) | 제한 | 제한 | 제한 |

- 주차 중에는 모든 지역에서 전부 허용.
- [문서에 없음] 한국이 "그 외 지역"에 들어가는지는 명시되지 않았다. 지역별 분기 대신 플래그만 보고 처리하면 지역과 무관하게 맞게 동작한다.
- 참고: FAQ(`/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq`)는 지역 코드 프로퍼티 `product.sw.target_region`(한국 `KOR`, 유럽 `EUR`, 북미 `USA`·`CAN`)을 안내한다. 우리는 주행 규제에 이 값을 쓰지 않는다(`pleos-platform.md` 13장).

### 2.4 우리 앱 규칙 [문서에 없음 — 위 문서들에서 정한 팀 규칙]
- 플래그별 처리: NO_VIDEO → 영상 정지·재생 버튼 비활성, NO_KEYBOARD → 텍스트 입력 필드 숨김(프리셋·최근 항목·음성으로 대체), NO_SETUP → 설정·로그인 진입 막고 "주차 후 이용" 안내, LIMIT_STRING_LENGTH·LIMIT_CONTENT → 긴 텍스트·긴 목록 축약.
- 애니메이션·자동 스크롤은 `isRequiresDistractionOptimization()`이 참이면 끈다.
- 규제 상태를 ViewModel의 `StateFlow`로 노출해 Compose가 한 곳에서 읽게 한다(`LocalUxRestrictions`).
- 현재 화면 적용(MainActivity): 제한 중이면 상단 배너를 반전색 "주행 중 · 제한 적용"으로 바꾸고 활성 플래그를 짧게 표시, 카드의 상세 정보(출처·버전·시각)를 숨기고 핵심 값만, 로딩 스피너 대신 정적 텍스트. 버튼은 필수 조작이라 유지. 수동 스크롤은 막지 않는다(금지 대상은 자동 스크롤이고, 작은 window에서 필수 버튼에 닿지 못할 수 있음).
- DO 선언: `MainActivity`에 `distractionOptimized` meta-data를 넣었다. 위 처리를 화면이 직접 하므로 주행 중에도 화면이 유지된다(에뮬레이터 확인).

## 3. 주의 분산 방지 원칙 [문서] — DD

- 운전에 필요한 정보는 짧은 시간에 이해되게 간결하게. HUD·클러스터·OSD를 쓰면 그 영역 정책을 따른다.
- 주행 중 동영상·애니메이션·움직이는 이미지를 줄이거나 막는다. 장식용·반복 움직임은 쓰지 않는다.
- 주행 중 필요한 기능을 우선 배치하고 메뉴 구조를 단순하게. 자주 쓰는 기능은 첫 화면·상단에. 드롭다운·다단 메뉴 남용 금지. 자동 전환·반복 화면 전환 최소화.
- 중요한 오디오 안내가 나오면 오디오 포커스 정책대로 미디어를 멈추거나 줄인다(오디오 포커스 개발 가이드, 요약은 `pleos-platform.md` 6장).
- 큰 터치 영역, 높은 대비, 적은 텍스트. 버튼 크기·간격은 UI 디자인 가이드 기준(수치는 `pleos-ui.md` 10장의 팀 규칙).
- 터치 조작을 줄이고 음성·물리 버튼 등 차량 입력과 연계한다.
- 게임, 인터넷 서핑, 피트니스처럼 주의를 빼앗는 활동을 유도하지 않는다.
- 검색·로그인에서도 키보드보다 프리셋, 최근 항목, 음성 입력을 먼저.

## 4. 앱 카테고리 [문서] — CATEGORY

- 카테고리: Maps/Navigation, Mobility Services, Lifestyle, Entertainment, Business, Music/Audio, Kids, Social/Communication. 설계 초기에 정한다.
- 주행 중 제한적으로 허용될 수 있는 예(실제 적용은 주행 상태·지역 정책에 따름):
  - Music/Audio: 앨범 아트 같은 단순 시각 정보, 재생·일시정지·곡 넘기기, 스트리밍·라디오·오디오북.
  - Maps/Navigation: 경로 안내, 실시간 교통, 간결한 지도, TBT 음성 안내.
  - Mobility Services: 주행 관련 차량 관리, 현재 위치 기반 정보, EV 충전소·주유소·주차장 찾기.

## 5. Vehicle SDK 사용 패턴 [문서] — VEH, PERM, SDK

- 구조: 3rd party 앱 → Vehicle SDK → Pleos Vehicle Service(AAOS 위). 문서 기준 SDK v2.3.2 / Vehicle Service v2.3.21.
- 진입점 `Vehicle` 인스턴스에서 `getDoor()`, `getEvBattery()`처럼 `getXxx()`로 도메인 객체를 얻는다(직접 생성 금지).
- 도메인 객체 API 4종: capability(`checkXXXCapabilities`, 지원 여부·값 범위), get, set, callback(리스너 등록).
- `initialize()`로 연결, 다 쓰면 `release()`. IPC라 결과는 `onComplete`/`onFailed` 비동기 람다로 온다. **비동기 콜백이 끝나기 전에 `release()`하지 않는다.**
- 이 레포의 예: `frontend/app/src/main/java/com/hmgchallenge/app/MainViewModel.kt` (ViewModel 수명 동안 유지, 첫 호출 실패 시 1회 재시도, `onCleared()`에서 리스너 해제 후 `release()`).

### 권한 매핑 [문서] — PERM

| 도메인 | 조회 권한 (`pleos.car.permission.`) |
| --- | --- |
| Door | `CAR_DOORS` |
| Window, Wiper | `CAR_WINDOWS` |
| HVAC | `CAR_CLIMATE` |
| DrivingMode, Brake, Odometer | `CAR_DRIVE` |
| EvBattery, Fuel | `CAR_ENERGY` (현재 선언) |
| CarInfo, Display | `CAR_INFO` |
| Light, TurnSignal | `CAR_LIGHTS` |
| SideMirror | `CAR_MIRRORS` |
| Safety, Tire | `CAR_SAFETY` |
| Seat | `CAR_SEATS` |
| Toll | `CAR_TOLL` |

- 제어(set)는 `CONTROL_CAR_*` 계열(예: `CONTROL_CAR_DOORS`).
- 권한은 Pleos App Market과 사전 협의·승인된 범위에서만 쓴다. 승인되지 않은 권한 선언이나 API 호출은 심사 탈락 사유다(SDK).
- 권한이 없으면 `onFailed`로 예외가 오고, `VehicleException`이면 `ERROR_SECURITY_PERMISSION`일 수 있다.
- Playground 프로젝트 기준 사용 가능: Vehicle State(조회), NaviHelper. 준비 중: Vehicle Control, FusedLocation, TextToSpeech, SpeechToText(PROJ). 제어 API 사용은 현재 막혀 있다고 보고 설계한다.
- 표는 도메인별 "대표" 권한이다. 세부 API마다 요구 권한이 다를 수 있으니 호출 전에 API 레퍼런스의 `@RequiresPermission`(PleosPermission 상수)을 확인한다(PERM).
- [문서에 없음] Steeringwheel 도메인은 대표 권한 표에 없다. 쓰기 전에 해당 API의 `@RequiresPermission`으로 확인한다. [팀 확인] Vehicle 2.3.2 AAR에서 `getCurrentSteeringWheelAngle`·`registerSteeringWheelAngle`은 `CAR_DRIVE`다(2026-10-25 javap).
- 오류 코드(`VehicleException.errorCode`)와 capability 모델은 `pleos-sdk.md` 5장.

## 6. 에뮬레이터 주의점

- [문서] Cold boot 필수. quick boot면 VHAL을 쓸 수 없다(EMU). 이전 버전 이미지는 지우고 설치한다.
- [문서] GPU 필요(권장·최소 사양은 NVIDIA/AMD 기준으로만 적혀 있음). 기본 언어는 한국어(차량 설정 > 일반 설정 > 언어).
- [팀 확인, 2026-11-12] 수동으로 만든 AVD의 화면 규격을 시스템 이미지와 혼동하지 않는다. 설치된 Pleos v2.1.0 이미지의 `devices.xml`에서 `Pleos_Connect_v2_0` 프로필은 **2560×1440, mdpi(160dpi)**다. 이는 이 기본 프로필의 값이며 모든 Pleos 폼팩터를 이 해상도로 고정하라는 뜻이 아니다. 공식 [설치 안내](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)도 Automotive의 Pleos 기기 프로필을 선택하게 한다(웹 본문에는 해상도·밀도 수치가 없음).
  - GCP의 수동 AVD가 1920×1080·320dpi로 만들어져 있었다. 기본 프로필보다 작은 화면에 dp·sp가 2배 픽셀 크기로 표시돼, 공조·차량 시스템 UI의 겹침과 앱의 좁은 표시 영역을 함께 확인했다. 11/11의 해당 캡처를 정상 화면 규격의 시각 검증 근거로 사용하지 않는다.
  - 겹침·과도한 글자 크기가 보이면 먼저 `adb shell wm size`, `adb shell wm density`, `adb shell settings get system font_scale`과 AVD의 `hw.lcd.*`를 확인하고 선택한 기기 프로필과 비교한다. 앱 글자 크기를 줄이는 것으로 환경 오류를 보정하지 않는다.
  - `wm density`만 실행 중에 바꾸면 차량 시스템 바의 위치·앱 영역이 함께 갱신되지 않을 수 있었다. 프로필을 고칠 때는 원래 AVD 설정을 백업하고 Cold boot한 뒤 시스템 UI까지 다시 캡처한다. 종료된 에뮬레이터가 잠시 ADB 목록에 남을 수 있으므로 프로세스 종료뿐 아니라 ADB 연결 해제도 확인한 뒤 다시 띄운다.
  - Cold boot 직후 `sys.boot_completed=1`만 확인하고 Activity를 먼저 열면 Driving view까지 덮는 일반 전체 화면으로 뜰 수 있었다. 차량 HOME을 열어 창 배치를 초기화한 뒤 MAIN/LAUNCHER 경로로 앱을 열면 정상 앱 창에 배치됐다. `scripts/run-app.sh`는 설치된 Pleos 홈과 운전 화면 Activity 준비를 확인한다. 패키지명 확인은 이 이미지에서 관찰한 개발 도구용 처리이며 공식 SDK 계약으로 간주하지 않는다.
- [문서] Virtual Console: `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`. Vehicle, Motion, ADAS, Convenience, Safety, Energy, Climate 상태를 바꿀 수 있다. 주행 규제 테스트도 여기서 차량 상태를 바꿔서 한다.
- [문서] FormFactorConsole(폼팩터 테스트): Platform-Tools, Go 1.22+, 8095·8096 포트, Host Display Bridge. 에뮬레이터를 별도 창으로 실행해야 가상 디스플레이가 보인다(Android Studio 내장 창·headless 불가). 가상 디스플레이를 지우면 그 위 앱은 강제 종료된다.
- [팀 관찰, 공식 문서 아님] 에뮬레이터에서 **VIN 조회 결과가 "invalid"** 로 나온다. 이미지의 SELinux 설정 문제로 보인다. **앱 로직이 VIN에 의존하지 않게** 하고, VIN이 없거나 이상해도 정상 동작하게 만든다.
- [팀 관찰] EV 배터리 잔량(`EvBattery`) 조회는 에뮬레이터에서 정상 동작한다. 값 변경 테스트는 Virtual Console Energy 도메인에서 한다.
- [팀 관찰, 2026-10-25] 에뮬레이터 v2.1.0은 user 빌드라 `adb root`, `cmd car_service inject-vhal-event` 등 car_service 셸 명령이 막혀 있다. 주행 상태는 Virtual Console로만 바꾼다.
  - 화면에서: Vehicle(Motion) 영역의 기어 D + 가속 페달로 속도를 올린다. 되돌릴 때는 P.
  - 자동화(비공식, 바뀔 수 있음): Virtual Console 페이지가 쓰는 HTTP 호출. 브라우저 세션이 살아 있어야 값이 반영되므로(`POST /browser/session?client=<id>`를 1초마다 보냄) 그동안 `PUT /vehicle/gear?gear=Drive|Park`, `PUT /vehicle/controls?speed=<kph>&accelerator=<0~100>&brake=<0~100>`를 부른다. 세션이 끊겨도 마지막 값은 유지됐다.
  - [문서] ADB 가이드(`/docs/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide`)에 `adb shell cmd hmg_car_service --service drivingstate driving_regulation on|off`("모든 차종 공통")가 있다. 이 에뮬레이터에서 되는지는 아직 시험하지 않았다(2026-10-28).
  - 확인: `adb shell dumpsys car_service --services CarUxRestrictionsManagerService | grep 'Display id'` → 주차 `DO: false UxR: 0`, 주행(D, 10km/h) `DO: true UxR: 80`(= NO_VIDEO 16 + NO_SETUP 64). 이 이미지의 기본 설정은 idling·moving 모두 0x50이고 문자열 제한 길이 120.
