# 내비 ETA·경유지 어댑터 (#5)

구현일: 2026-11-12. `shared/journey/NavigationSource`·`NavigationRepository`와 앱의 `PleosNavigationClient`가 경계다.
실제 SDK와 데모는 같은 상태·콜백 계약을 사용하며 SDK 객체는 앱 밖으로 전달하지 않는다. 후속 확인: 2026-11-13. 최신 실측·검증 지문은 [후속 재현 기록](navigation-repro.md)을 따른다.

## 단위와 권한의 확인 근거

필요한 공식 페이지만 읽고 직접 요약했다. 확인: 2026-11-12.

- [DestinationInfo API Reference](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/entry): `remainTime`은 남은 **초**, `remainDistance`는 **미터**, `latitude`·`longitude`는 **도**다. 지리 좌표를 보존하고 표시용 좌표로 대체하지 않는다. 지리 좌표 자체를 시설 진입 근거로 간주하지 않는다.
- [RouteStateInfo API Reference](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-state-info/entry)는 `isRouting`을 활성 경로 여부로 설명하지만, 테스트 서비스 3.0.4에서는 실제 `ROUTE_GUIDING` 중에도 false를 반환했다. 앱은 [NaviMode의 안내 모드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-mode/entry)를 조회해 판단한다. `ROUTE_GUIDING`·`SIMULATION_GUIDING`을 구분하며, 상태 이벤트만으로 경로 취소를 확정하지 않는다. 미설정 경로를 ETA 0으로 만들지 않는다.
- [초기화](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release): 연결 완료 뒤에 리스너를 등록하고 조회한다. 중지할 때 리스너 제거 후 해제한다.
- [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions), [SDK 사용 신청](https://document.pleos.ai/docs/pleos-only/create-my-project): NaviHelper는 별도 사용 신청 없이 제공된다. 앱에는 조회용 `NAVI_ROUTE`와 사용자 확인 뒤 경유지 추가에 쓰는 `NAVI_ROUTE_SEARCH`만 선언한다. 설치별 실제 권한은 호출 전에 다시 확인한다. `NAVI_CUSTOM_MAP`, 위치·음성·차량 제어 권한은 추가하지 않는다.
- 기존 테스트 VM의 캐시에서 NaviHelper 3.0.5·navi-data 3.0.4의 공개 시그니처를 `javap`로 확인했다. 해당 에뮬레이터의 두 내비 권한은 normal로 정의되어 있다. 이것을 실차·다른 앱 서명의 권한 보장으로 확장하지 않는다.

## 읽기·갱신·실패

- `NavigationDestination`은 단위가 명시된 입력이다. ETA는 **콜백 관측 시각 + 남은 초 × 1,000**이다. 음수·시각 오버플로는 실패로 처리한다.
- 목적지·경로 변경, 재탐색, 경유지 변경 시 이전 ETA를 즉시 무효화한다. 경로 상태를 새로 확인한 다음 목적지·경유지를 조회한다. 동일 목적지의 ETA 갱신은 여정 ID를 유지한다.
- 구형 내비 서비스 3.0.4는 탐색 완료를 `onRouteEnded`로 보낸다. NaviHelper 3.0.5 AAR은 이 콜백을 `onRoutingCompleted`로 대체하도록 폐기 표시한다. 두 콜백 모두 활성 경로 재조회를 요청하며, 구형 콜백을 경로 취소로 해석하지 않는다. 2026-11-12 실제 로그와 AAR 주석으로 확인했다.
- SDK 콜백은 메인 looper로 모으며, 세션 세대 번호로 중지·재연결 이전의 지연 콜백을 무시한다.
- 요청 없이 온 목적지·안내 모드 변경도 반영한다. 같은 활성 모드가 반복 통지될 때 불필요한 조회를 추가하지 않는다. 네이티브 모의주행은 `JourneySnapshot.isSimulation`으로 유지하고 예보의 데모 표시로 전달한다.
- Activity가 보이는 동안만 연결·조회한다. 주기 조회는 30초, 읽기 타임아웃 10초, 최초 연결 타임아웃 15초, 값의 최신성은 60초다. 이 수치는 SDK 보장이 아닌 제품 정책이다. 백그라운드에서는 리스너와 SDK 연결을 해제한다.
- `EMPTY`, `UNSUPPORTED`, `PERMISSION_DENIED`, `FAILED`, `STALE`을 구분한다. 오래된 캐시는 `readyValue`로 전달되지 않으며 연결 화면에도 예전 ETA를 현재 값으로 표시하지 않는다. 권한 거부 때 자동 재연결을 반복하지 않는다.
- `journeyForecasts`는 확인된 돌봄 기록·새 여정·타이머 변경을 받아 같은 예보 엔진을 다시 계산한다. 입력 변경 때의 시각을 새로 읽어, 직전 타이머 뒤에 온 SDK 관측을 미래 데이터로 오인하지 않는다. 내비 실패 시 돌봄 이력 기반 예보는 유지한다.

## 경유지 확인

1. 접근이 확인된 실제 후보를 선택하면 현재 목적지·경유지 목록을 다시 읽는다. #6의 후보에는 실제 경로 공급자가 준 진입 좌표·현재 여정 ID·접근 확인 시각이 있어야 한다. 현재 Pleos 어댑터는 호환되는 공급자의 내비 POI ID도 요구하며, 없으면 확인·전송 전에 차단한다. 선택 직전과 확인 후 재조회에서 여정 일치와 60초 만료를 다시 검사한다. 공공시설 중심 좌표를 진입 좌표로 대입하지 않으며 데모 출처는 실제 SDK에 보내지 않는다.
2. 목적지와 사용 가능한 슬롯을 묶은 30초 유효 확인 ID를 제공한다. 이 단계까지 내비 변경은 없다.
3. 사용자가 확인하면 목적지와 경유지 목록을 다시 읽어 비교한다. 기존 1개는 유지하고 두 번째 슬롯에 추가한다. 이미 2개이면 덮어쓰지 않고 실패를 알린다.
4. `addWaypoint` 반환이나 탐색 완료 이벤트만으로 성공을 표시하지 않는다. 목적지·경유지 양쪽 조회가 끝나고 전송 전 목적지가 유지됐는지 확인한다. 에뮬레이터의 내비 서비스(3.0.4 / 기본 내비 3.3.2-rc5-hotfix.3)는 `onWayPointInfo`에 `poiId`·이름·남은 거리/시간을 채워 주면서도 위경도를 `0.0, 0.0`(`displayLatitude/Longitude = null`)으로 생략해 반환한다. 따라서 `poiId`가 있는 `(0, 0)` SDK 응답은 전송 전 목적지와 일치하는 재조회 뒤 제공된 `poiId` 일치를 확인해 성공(`CONFIRMED`)으로 확정하고, 비영 좌표를 반환하는 서비스에서는 좌표와 `poiId`가 모두 일치해야 성공으로 표시한다. 같은 목적지의 안내가 재탐색 중 잠시 `SAFE_GUIDING`으로 초기화됐다가 돌아온 경우도 이 재확인을 거친다.
5. 전송 뒤 타임아웃·연결 상실은 결과 미확인(`OUTCOME_UNKNOWN`)으로 남기고 자동 재전송을 막는다. 이후 읽기에서 확인되면 성공으로 바꾼다. 이미 등록된 같은 위치/`poiId`는 다시 추가하지 않는다.

`poiId`까지 비어 있는 미확인 `(0, 0)` 경유지 응답(`poiId == null`)은 빈 슬롯이나 확인된 실제 경유지로 취급하지 않는다. 실제 SDK 공급자에서는 경유지 정보 실패(`FAILED` / `REQUEST_FAILED`)로 처리해 **추가 요청을 차단하고 ETA는 유지**한다. 데모 공급자의 명시적인 합성 좌표와는 구분한다.

2026-11-13 후속 검증에서는 공식 POI ID(`36khp2hdj2cfcm5nqhe3`)를 사용한 `NavigationSmokeInstrumentation`(`mutateEmulatorRoute=true`, `officialPoiWaypoint=true`)이 빈 경로(`EMPTY`)에서 출발해 목적지 생성·ETA 수신·활성 경로 재연결·실제 경유지 등록 성공(`confirmedWaypoint=passed`)·자동 취소 15회 복원(`fixtureCleanup=passed`)과 후속 읽기 전용 스모크(`initialRoute=EMPTY`, `passive=passed`), `CryResponseSmokeInstrumentation` 회귀 검증까지 모두 통과했다. 상세 로그·원인 기록은 [후속 실측](navigation-repro.md), [시설·캐시·데모 검증](facilities.md)에 있다.

SDK 조회·변경 API에는 요청 ID나 조건부 갱신 기능이 없다. 앱의 재확인은 원자적인 차량 측 트랜잭션을 보장하지 않으며, 외부 앱의 동시 변경을 완전히 잠글 수 없다. 비영 좌표가 반환되는 환경에서는 공급자가 검증한 진입 좌표와 SDK의 지리 좌표를 정확히 비교하며, `(0, 0)` 좌표가 반환되는 서비스에서도 `poiId`가 없거나 불일치하면 성공으로 보지 않는다. SDK의 위경도 필드 자체가 시설 진입 가능성을 증명하지는 않는다.

`poiId`를 알 수 없는 공공시설은 실제 Pleos 요청을 보내지 않는다. 빈 ID의 지원은 공식 문서에 없고 현재 환경의 좌표 전용 요청에서 유효한 결과를 확인하지 못했다. 공공시설 ID를 내비 ID로 만들어 넣지 않는다. 전송 전 로컬 검사에서 거부한 경우는 FAILED이며 ETA·읽기 연결을 유지한다. 전송 뒤 불명확한 실패는 OUTCOME_UNKNOWN이다. 실제 시설 공급자·접근성 검증은 #6, 출발·울음 대응 화면의 시설 선택은 #7·#8 범위다.

## 최초 #5 검증 상태 (2026-11-12)

- 로컬 Kotlin 구문·XML·`git diff --check` 통과.
- 공용 **108개**, 서버 **1개**, 총 **109개** 테스트 통과(실패·오류·스킵 0). 이번 내비 연동 테스트는 30개이며, 0,0 경유지 차단과 ETA 보존도 포함한다. 단위·시간 오버플로, 지연 콜백, 목적지·모드 변경, 경로 취소, 권한 거부, 타임아웃·오래된 값, 두 개 경유지 보존, 확인 만료·중복 요청, 데모 분리와 ETA→예보 갱신을 검증했다.
- 사용자 승인 후 기존 GCP VM의 이 작업 전용 임시 폴더에서 실행했다(JDK 21, Android platform 37). 앱 debug APK·플랫폼 계측 APK 빌드와 설치를 통과했다. 로컬에는 JDK·Android SDK가 없어 로컬 빌드 결과로 표기하지 않는다.
- 실제 SDK 연결·경로 없음 조회, 합성 경로의 ETA 수신, 활성 경로 재연결을 확인했다. 원시 콜백은 Binder 스레드에서 왔으며 앱에서는 메인 스레드로 모아 처리했다. 내비 약관 동의 설정은 변경하지 않았다.
- `NavigationSmokeInstrumentation`의 기본 실행은 읽기만 한다. 합성 경로 변경은 `mutateEmulatorRoute=true`를 명시해야 한다. 에뮬레이터이며 원래 경로가 없거나, 이전 실행이 만든 동일한 공개 샘플의 이름·좌표가 확인된 경우만 진행한다. 다른 기존 경로는 보존한다. 내비 약관에 대신 동의하지 않는다.
- 실제 경유지 등록은 위 공급자 응답 문제로 미확인이다. 단위 테스트·데모의 성공과 구분한다. 이 이슈를 실등록 검증까지 완료한 것으로 닫지 않는다.
- 주야간·가로·세로 화면 캡처 및 검증 후 상태 복원은 별도 결과 파일에 기록한다. 실차·성능 한도·다른 시스템 이미지·네이티브 모의주행 실행·release 빌드는 이번 검증 범위가 아니다.

### 화면·복원 검증

**화면 규격 정정 (2026-11-12):** 아래 측정과 캡처는 1920×1080·320dpi로 수동 생성된 GCP AVD에서 수행했다. 이후 설치된 공식 Pleos 프로필의 2560×1440·160dpi와 다르다는 것을 확인해 환경을 수정했다. 버튼 접근·기록 보존의 기능 확인과 정상 규격의 시각 검증을 구분하며, 아래 캡처를 완성된 UI의 가독성 검증 근거로 보지 않는다. 수정 후 기록 화면의 캡처와 측정은 [화면 규격·재검증](care-records.md)에 기록했다. 내비 상태 카드 자체의 정상 규격 최종 시각 검증은 핵심 화면 통합 때 다시 수행한다.

네이티브 window bar로 창 크기를 바꾸고 각 상태를 직접 캡처했다. 좁은 창에서는 수동 스크롤로 상태와 버튼에 접근한다. 가로 여백을 24dp로 맞춰 좁은 창의 확인 버튼을 한 줄로 표시하고, 연결 화면 바깥 배경도 주야간 테마를 사용한다.

| 상태 | 앱 영역 | 내비 확인 버튼 | 확인 |
| --- | --- | --- | --- |
| 가로·주간 | 616×324dp | 472×72dp | 문구·조작·24dp 이상 버튼 간격 |
| 가로·야간 | 616×324dp | 472×72dp | 동일, 야간 배경 적용 |
| 세로·주간 | 308×324dp | 164×72dp | 확인 버튼 한 줄, 수동 스크롤 접근 |
| 세로·야간 | 308×324dp | 164×72dp | 동일, 야간 배경 적용 |

색·글자·버튼은 기존 `CareText`·`CareButton` 토큰을 재사용한다. 변경하지 않은 토큰 쌍의 기존 대비 계산은 활성·누름 9.41:1 이상, 비활성은 주간 5.00:1·야간 5.57:1 이상이다. UI 캡처·메타데이터는 Git에서 제외한 `.dev/verification/issue5/2026-11-12/verification/ui-final/`에 보관한다.

- 실제 AAOS 사용자 프로필에서 화면 검증 전후 돌봄 파일의 SHA-256 일치를 확인했다. 야간 설정은 `no`, 창은 원래 616×324dp로 복원했다.
- 테스트 내비는 종료 요청만으로 상태를 확정하지 않고 네이티브 앱을 재기동한 뒤 검색 화면·`SAFE_GUIDING`·`EMPTY`를 확인했다. 실제 경로·경유지 등록 성공을 뜻하지 않는다.
- APK의 병합 차량 권한은 `CAR_ENERGY`, `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH` 세 개임을 확인했다.
- 재현 가능한 소스 압축본의 고정 파일 시각이 증분 빌드에 영향을 주지 않도록 추출 시 시각을 갱신했다. 전체 재빌드로 검증하고, 설치된 APK의 SHA-256을 빌드 출력과 대조했다. 마지막 UI 보완 후에도 빌드·검증을 수행했다.

최종 소스 SHA-256: `52f49aea9a5689ae01a8b3f3e6b313dd33e9c7f8500a72049fed375a97d45fe8`.
debug APK SHA-256: `0ebafc2e756b93eac9f3f64130425dba1ccc02e885151ec82e2e2e5b1c8fbb75`.
당시 작업 폴더의 소스·빌드 입력 79개 파일이 검증 압축본과 일치했다. 커밋 전 소스이므로 위 지문으로 당시 빌드 입력을 구분한다. 요약·빌드 로그·테스트 XML은 `.dev/verification/issue5/2026-11-12/`에 보관한다. 최신 소스·검증은 [후속 기록](navigation-repro.md)에서 구분한다.

실행한 빌드·읽기 검증 명령:

```bash
./gradlew :shared:build :backend:build :frontend:app:assembleDebug :frontend:app:assembleDebugAndroidTest --console=plain -Pandroid.builder.sdkDownload=false
adb install -r frontend/app/build/outputs/apk/debug/app-debug.apk
adb install -r frontend/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.hmgchallenge.app.test/com.hmgchallenge.app.navigation.NavigationSmokeInstrumentation
```
