# 아가랑 SDK·권한·실패 처리 매트릭스

최초 확인일: 2026-11-10, 내비·시설 후속 확인: 2026-11-13. 공식 문서를 필요한 범위만 읽고 직접 요약했다. 원문이 최종 기준이며 계정의 승인 내역을 조회한 결과는 아니다.

## 공식 확인 근거

1. [프로젝트와 SDK 사용 신청](https://document.pleos.ai/docs/pleos-only/create-my-project)
2. [NaviHelper 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
3. [차종별 API 가용성](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability)

Vehicle State와 NaviHelper는 문서상 신청 없이 사용 가능하다. FusedLocation, TTS/STT, Vehicle Control은 준비 중으로 안내되어 있다.
SDK 사용 가능 안내와 특정 앱의 권한 허용·서명·capability·서비스 연결 성공을 같은 것으로 취급하지 않는다.
Gleo의 서드파티 연동 방법은 위 세 문서에서 확인되지 않았다.

## 요구 데이터와 사용 경계

| 요구 데이터/기능 | 후보 API·권한 | 현재 앱/환경 확인 | P0 처리와 실패 대안 |
| --- | --- | --- | --- |
| 수유·기저귀·수면·월령 | 보호자 입력, 로컬 저장 | 제출 앱 구현, GCP 빌드·Pleos 에뮬레이터 기록/복원·실패 처리·주행 제한 검증(#3, 2026-11-11) | 서버 없이 앱 전용 파일에 저장. 복원·취소·저장 오류 처리. [구현·검증 결과](care-records.md) |
| 주행 중 UI 제한 | AAOS CarUxRestrictionsManager, 별도 차량 도메인 권한 없음 | 앱 구독 구현, 에뮬레이터에서 주차 DO=false 확인 | 기존 경로 유지. 연결 실패는 제한 모드로 처리 |
| 배터리 | Vehicle EvBattery, CAR_ENERGY | 이 권한만 앱에 선언되어 있음 | 기존 연결 유지. 예보 핵심의 필수 입력으로 사용하지 않음 |
| 경로·목적지·ETA 읽기 | NaviHelper get*Info, NAVI_ROUTE | 초기 #5에서 ETA·재연결 확인, #6 읽기 실패 뒤 11/13 기본 내비 ‘다시 시도’로 EMPTY·ETA·재연결 다시 확인. 초·미터 단위 공식 확인 | 연결·관측·최신성·안내 모드 구분. 구형 완료 콜백과 isRouting 불일치 처리. [후속 실측](navigation-repro.md) |
| 경로·경유지 변경 | NaviHelper addWaypoint, NAVI_ROUTE_SEARCH | 2026-11-13 에뮬레이터 실측으로 서비스(`NavigationUseCase.getWaypointRoutePointItem`)가 경유지 조회 좌표를 `(0, 0)`으로 생략하고 `poiId`·이름·남은 거리/시간만 반환함을 확인. 공식 POI ID(`36khp2hdj2cfcm5nqhe3`) 경유지의 실제 추가·재조회 등록 성공·자동 취소 복원 검증 완료 | 호환 POI ID가 없으면 전송 전 거부. `poiId == null`인 `(0, 0)` 응답은 차단하고 ETA 유지. `poiId`가 포함된 `(0, 0)` SDK 응답은 동일 목적지 재조회와 `poiId` 일치(비영 좌표 반환 시 좌표+`poiId` 동시 일치)로 확정. 두 슬롯 덮어쓰기·결과 미확인 재전송 금지 |
| 내비 화면 요청 | NAVI_CUSTOM_MAP | 현재 사용하지 않음 | 첫 MVP에 권한 추가하지 않음 |
| 시설·수유실 정보 | 공공데이터 → 서버 정규화·앱 캐시 + 경로 공급자 | 한국도로공사 공개 시설 468건, 정규화·API·캐시·정차 엔진·실패 화면 검증. 공개 SDK에는 현재 경로 전체·방향별 시설 접근성 근거가 없고 외부 공급자는 미구성 | 실경로 접근성은 미확인으로 유지. 좌표·노선명으로 접근·시간·마지막 휴게소를 추정하지 않음. 공급자의 POI ID도 접근 근거와 별도로 전달. [시설 계약](facilities.md) |
| 착석 시간 | 보호자가 시작한 주행/케어 세션 | 센서 기반 아기 감지 없음 | DriveSession 기준. 시동 시간과 아기 착석 시간을 동일시하지 않음 |
| 급감속·조향 | 표준 AAOS API 또는 지원 Vehicle 도메인, 각 권한/capability 확인 필요 | 관련 권한 미선언·호출 미구현 | F3 후속. 권한/실기 검증 전에는 데모 입력만 사용 |
| 공조 상태 | 지원 Vehicle 도메인·CAR_CLIMATE 등 API별 권한 검증 | 권한 미선언·미검증 | F3 후속. 상태 조회와 설정 제어를 분리. 미지원이면 안내 생략 |
| 현재 위치 | 승인된 NaviHelper 정보 또는 FusedLocation | 위치 서비스 설치됨. FusedLocation은 문서상 준비 중, 앱의 자동 병합 권한도 제거됨 | 독립 위치 SDK 호출을 P0 필수로 만들지 않음. 위치 없는 기록 유지 |
| TTS/STT | TTS_SERVICE/STT_SERVICE, 자격 증명·모드 조건 확인 | CAAS 서비스 설치됨. SDK는 문서상 준비 중, 앱 권한 미선언 | #14 승인 후 확장. 버튼·텍스트 경로 유지, 키는 비공개 설정 |
| Gleo | 공식 서드파티 연동 계약 미확인 | 모델 공급 패키지는 있으나 연동/권한 증거가 아님 | P0 필수 조건에서 제외하고 공식 확인 과제로 유지 |
| 휠 미디어 제어 | 표준 미디어 서비스/세션·오디오 포커스 | 미구현 | #10에서 검증. 앱 기능을 커스텀 휠 키에 직접 할당하지 않음 |
| 차량 제어 | Vehicle Control/CONTROL_CAR_* | 문서상 준비 중, 앱 권한 없음 | 이 MVP에서 호출·권한 추가하지 않음 |

NaviHelper는 내비 탑재 차량에서 전체 호환된다. Vehicle은 도메인별 capability, FusedLocation은 isSupported 확인이 필요하다.
NaviHelper의 API별 정확한 권한은 API Reference의 RequiresPermission도 확인한다. 선언만으로 허용되는지 여부를 이 문서만으로 단정하지 않는다.

## 런타임 확인과 남은 검증

기존 Pleos AVD를 Cold boot한 상태에서 ADB로 다음을 확인했다. 리소스를 설치하거나 미승인 SDK API를 호출한 검증은 아니다.

- 주차 상태: `DO: false`, `UxR: 0`.
- 내비 서비스 `ai.pleos.playground.service.navi` 및 내비 앱 설치.
- Vehicle 서비스, FusedLocation 서비스, CAAS 및 Gleo 모델 공급 패키지 설치.
- 최초 점검에서는 Android API 34/Build Tools 34.0.0을 확인했다. 이후 사용자 승인으로 GCP에 platform 37과 Build Tools 36.0.0·37.0.0을 추가하고 #3의 앱 debug 빌드를 통과했다(2026-11-11).

패키지 설치는 앱 사용 승인·API 성공·Gleo 연동 증거가 아니다. 실제 콜백, OTA/서비스 버전, 내비 실패, 권한 거부, 차종 capability는 해당 구현 이슈에서 검증한다.

## 단위 변환과 승인 순서

1. 필요한 데이터와 API를 선택한다.
2. 공식 제공 상태·API별 권한·프로젝트 허용 범위·capability를 확인한다.
3. 승인된 권한만 선언하고 연결 성공 후 콜백을 등록한다.
4. 실제 출력의 시간/거리/좌표 단위를 확인해 공용 계약의 epoch ms·초·미터·도 단위로 변환한다.
5. 실패·해제·서비스 재연결을 검증한다. raw ETA의 단위 미확인 상태는 READY로 내보내지 않는다.

엔진·서버는 Pleos SDK 객체를 직접 참조하지 않는다. 원시 자료가 없거나 사용할 수 없을 때 `DataSnapshot`의 상태를 전달하고 예보·화면이 축소 동작하도록 한다.
