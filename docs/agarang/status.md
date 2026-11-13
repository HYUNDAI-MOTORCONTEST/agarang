# 개발 현황 (2026-11-13)

기준: 이슈 #1의 로드맵과 현재 소스·실행 검증 기록. GitHub 이슈의 체크 표시·종료 상태와 별개로 실제 구현과 완료 조건을 대조했다.

## 진행률

- **핵심 MVP(P0): 78% — 필수 9개 이슈 중 7개 완료.**
- **전체 로드맵: 54% — 작업 이슈 #2~#14, 13개 중 7개 완료.**

각 이슈를 같은 비중으로 센 완료율이다. 작업 시간이나 제품 완성도를 가중 추정한 수치가 아니다. 부분 완료(#12)는 완료율에 더하지 않았다. #5는 에뮬레이터 경유지 `(0, 0)` 응답 원인 확인과 공식 POI ID 실제 등록 성공·자동 취소 복원 검증까지 마쳤고, #6은 공개 시설·API·캐시·정차 계획에 더해 `RouteAccessCatalog`·`RouteAccessRepository`·`/api/facilities/route-access` 공급자 파이프라인 연결과 에뮬레이터 전 구간 검증까지 완료했다. 출발 코칭과 울음 대응의 데모 동선·실패 상태도 구현·검증했다.

| 이슈 | 상태 | 근거·남은 범위 |
| --- | --- | --- |
| #2 MVP·SDK·데이터 계약 | 완료 | 공용 계약, 출처·실패·데모 경계 |
| #3 프로필·돌봄 기록 | 완료 | 기록·수정·취소·로컬 저장·복원·실패 처리, 주행 제한 검증. 제품 시각 디자인 마감과는 구분 |
| #4 예보 엔진 | 완료 | 설정·이력·일반 참고 구분, 시간 범위·근거·정보 부족·여정 판단과 테스트. 핵심 화면은 후속 범위 |
| #5 내비 연동 | 완료 | ETA·재연결·전송 전 검사·목적지/경유지 재조회 검증 완료. 에뮬레이터 `NavigationUseCase`의 `(0, 0)` 좌표 생략 원인 규명 후 `poiId` 기반 확정(미확인 `(0, 0)`은 차단 유지) 및 공식 POI 실제 등록 성공·15회 복원 통과. [후속 실측](navigation-repro.md) |
| #6 시설·정차 계획 | 완료 | 공개 시설 468건·조회 API·앱 캐시·두 정차 계획·마지막 휴게소·데모/실패 화면에 더해 `GET /api/facilities/route-access`·`RouteAccessRepository`·`MainViewModel`/`DepartureController` 공급자 연결 완료. 검증 회랑은 실제 후보·`navigationPoiId`·경유지 등록까지 동작하고 미등록 경로는 `UNSUPPORTED`(`ACCESS_UNVERIFIED`) 유지. 공용 191개+서버 10개 테스트 및 에뮬레이터 파이프라인 계측 통과. [상세](facilities.md) |
| #7 출발 예보·코칭·체크리스트 | 완료 | 첫 화면·근거·첫날 제한, 출발 선택 3가지, 준비물 계산·저장·복원, 지연 재계산과 주행 전환 검증. 실제 SDK와 분리된 데모 정차 동선 포함. [상세](departure.md) |
| #8 울음 대응 | 완료 | 수동/임박 확인·기록 근거·두 정차 선택·확정 기록 후 복귀·반복 억제. 첫날/시설 없음/내비 실패·가로세로/주야간/주행 전환과 실제 저장 실패 검증. [상세](response.md) |
| #12 통합 검증 | 일부 진행 | 개별 기능·SDK·창·오류와 F2·F4 데모 동선 검증은 있음. 실제 내비를 포함한 전체 시나리오 통합 검증은 남음 |
| #13 제출 준비 | 미완료 | 소개·기획서·release APK·시연 자료 최종화 |
| #9~#11 케어·오디오·회고 | 미구현 | P1 후속 기능 |
| #14 음성 확장 | 미구현 | P2, 승인 후 확장 |

## 검증과 UI 상태

- 최신 #6 완료 GCP 검증 빌드: `:shared:build`, `:backend:build`, `:frontend:app:assembleDebug`, `:frontend:app:assembleDebugAndroidTest` 통과(**공용 191개 + 서버 10개 = 201개 테스트 통과·스킵/실패/오류 0**). 라이브 백엔드 `/api/facilities/route-access`(기본 `NAVI_SDK` 2개 회랑·`mode=demo`·오류 모드 HTTP 400)와 `NavigationSmokeInstrumentation`(`routeAccessPlannerPipeline=passed`, `confirmedWaypoint=passed`, 15회 취소 복원, 후속 `initialRoute=EMPTY` 읽기 전용 스모크), `CryResponseSmokeInstrumentation`(`routeAccessProviderIntegration=passed`, `personalDataPreserved=true`)까지 모두 통과했다. [시설 검증 기록](facilities.md)
- 이전 #5 완료 GCP 검증 빌드: `:shared:build`, `:backend:build`, `:frontend:app:assembleDebug`, `:frontend:app:assembleDebugAndroidTest` 통과(**공용 189개 테스트 완료·실패 0**, `:backend:test` 통과). `NavigationSmokeInstrumentation`(`mutateEmulatorRoute=true`, `officialPoiWaypoint=true`)이 `initialRoute=EMPTY`에서 출발해 공식 `poiId` 경유지 실제 등록 성공(`confirmedWaypoint=passed`)과 15회 취소 복원(`fixtureCleanup=passed`), 이어진 읽기 전용 스모크(`initialRoute=EMPTY`, `passive=passed`), 실제 Android 울음 대응 회귀 검증(`CryResponseSmokeInstrumentation`, `personalDataPreserved=true`)까지 모두 통과했다. [후속 검증 기록](navigation-repro.md)
- #8 GCP 검증은 공용 179개 + 서버 9개 = 188개 테스트와 앱 설치·기기 컨트롤러/저장 검증을 통과했다. 당시 빌드 입력 109개 파일과 설치 APK의 지문을 대조했다. [울음 대응 검증 기록](response.md)
- 수동/임박 진입·반복 억제·두 정차 선택·기록 후 복귀·기록 없이 완료·첫날/시설 없음/내비 실패를 재현했다. 화면 20개로 두 창 크기·주야간·주행 제한과 주차 후 상세 복원을 확인했다. 저장 확인 실패에는 대응을 유지했다. 데모와 임시 검증이 기존 개인 돌봄·준비물을 변경하지 않았다.
- #7 단계에서는 171개 테스트와 출발 선택 3가지·기록/취소·첫날·90분 지연·외출 범위·준비물 추가/재실행 복원, 주행 시작 때 입력·키보드 종료를 확인했다. [출발 예보 검증 기록](departure.md)
- #6 1차 단계에서는 공용 137개 + 서버 9개 = 146개 테스트, 공개 시설 468건 API와 Kotlin 재수집 결과 일치, 화면·캐시를 검증했고, 2차 단계(2026-11-13)에서 경로 접근성 공급자 파이프라인 연결과 201개 테스트·에뮬레이터 전 구간 계측까지 완료했다. [시설 검증 기록](facilities.md)
- 이전 #6 1차 실행의 내비 읽기 스모크는 `read=FAILED`였으나, 2026-11-13 후속 실행에서 기본 내비 복구·`(0, 0)` 응답 원인 확인·공식 POI 경유지 실제 등록 성공(#5) 및 `RouteAccessCatalog` 기반 후보→경유지 등록 전 구간(#6)을 모두 검증했다. [내비 검증 기록](navigation-repro.md)
- 로컬에는 JDK·Android SDK가 없어 위 결과는 GCP 실행 결과다. 실행 스크립트 변경은 Bash 구문 검사와 실제 HOME/LAUNCHER 동작을 확인했다.
- 11/11~11/12 초기 캡처의 GCP AVD 화면 규격이 잘못돼 있었다. 공식 기기 프로필의 2560×1440·160dpi로 맞추고 Cold boot한 뒤 앱과 차량 시스템 UI를 다시 확인했다. 이전 규격의 시각 검증 주장은 정정했다. [화면 규격·재검증](care-records.md)
- 현재 앱의 첫 화면은 출발 예보·출발 선택·준비물·바로 기록과 큰 울음 대응 진입으로 구성했다. 글자 위계·대비·터치 크기와 실제 두 창 크기의 주간/야간을 확인했다. 전체 제품의 시각 마감·통합 검증은 남아 있다.
- 실행 가능한 제출용 release APK와 전체 시연 동선은 아직 완성되지 않았다.

현재 핵심 시나리오 통합 검증·제출 준비(#12·#13)를 진행할 수 있는 상태다. 부분 완료(#12)를 합산하지 않은 이슈 완료율은 P0 78%·전체 54%다. 검증된 작업 결과는 작업 트리에 보관했고, 커밋·원격 푸시는 요청 시에만 수행한다.
