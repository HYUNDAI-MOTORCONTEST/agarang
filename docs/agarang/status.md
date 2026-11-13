# 개발 현황 (2026-11-14)

남은 일과 작업 방법은 [작업 인계](handoff.md)에 정리했다.

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
| #12 통합 검증 | 일부 진행 | 공용 195개+서버 12개(207개) 단위·번들 테스트, 라이브 백엔드+4종 에뮬레이터 계측, 실제 UI(가로/세로·주야간·영속복원·오프라인·주행제한·Back·CPU 0.0%/RAM 90.63~105.82MB) 통합 검증 완료. FormFactorConsole(Host Display Bridge)·실차 환경만 미검증. [상세](verification-issue12.md) |
| #13 제출 준비 | 일부 진행 | release APK(`apk/아가랑_아가랑_release.apk`, 서버 없이 동작, v2·v3 서명)·README 1~4장·에뮬레이터 설치 검증·시연용 실화면 녹화 완료. 접수양식 5·7번 정합성 수정, 영상 편집, main 반영(PR #17), 이메일 제출이 남음. [인계](handoff.md) |
| #9~#11 케어·오디오·회고 | 미구현 | P1 후속 기능 |
| #14 음성 확장 | 미구현 | P2, 승인 후 확장 |

## 검증과 UI 상태

- 후속 UI/UX 정리: 아이콘·저장된 선택 표시·홈 준비물 직접 체크와 진행 상태, 목록 편집 모드, 울음 대응의 정차 선택 우선 배치·근거 펼치기를 반영했다. 주행 중 비활성 기록 묶음을 숨기고 상단 복귀를 유지한다. 207개 회귀 테스트·기기 계측 4종·두 창 주야간·실제 주행 제한 전환과 입력/키보드 종료·정차 선택·복귀·데이터 보존을 확인했다. 이 후속 변경은 로컬 검토 상태다. [UI/UX 변경·검증](ux-refinement.md)
- #16 디자인 토큰·UI/UX 개선: `PleosTokens`(`PleosDimens`·`PleosShapes`·`PleosMotion`·`PleosElevation`·`PleosTheme`)·`PleosRoles`·`PleosAccentTone`을 도입하고 `:frontend:app` 13개 화면·컴포넌트에서 `ui/theme/` 외부의 `.dp`·`RoundedCornerShape` 하드코딩을 제거했다. 카드 내부 보조 버튼 대비·선택 상태 리딩 바·셸 임베딩 시 중복 `"이전"` 제거·`TOOLS` 중복 `"더 보기"` 숨김·`NEW_OUTING` 헤더·정보 그룹핑·`"취소"` 2줄 래핑 방지를 반영했으며, 에뮬레이터에서 13종 주간/야간·가로/세로 스크린샷 및 `돌봄 기록` 재캡처를 확인한 뒤 최종 `PleosTokens.kt` 수정본 기준으로 `:shared:test` 195개·`:backend:test` 12개(`--rerun-tasks`)·`:frontend:app:assembleDebug`/`assembleDebugAndroidTest`·4종 계측(`NavigationSmokeInstrumentation`, `DepartureSmokeInstrumentation`, `CryResponseSmokeInstrumentation`, `ScenarioVerificationInstrumentation`)까지 모두 통과했다.
- 최신 #12 통합 검증 빌드: `:shared:test` 195개(13파일) + `:backend:test` 12개(3파일) = **207개 테스트 캐시 제외(`--rerun-tasks`) 전수 통과·스킵/실패/오류 0**, `:frontend:app:assembleDebug` 성공. 라이브 백엔드(`/api/health`, 공공시설 468건, 접근성 2개 회랑)와 `-Phmg.testRunner` 4종 실기기 계측(`ScenarioVerificationInstrumentation`, `DepartureSmokeInstrumentation`, `CryResponseSmokeInstrumentation`, `NavigationSmokeInstrumentation`+패시브 복원), 실제 `MainActivity` UI(`841×1210dp`·`1682×1210dp`, 주야간, `force-stop` 영속 복원, 백엔드 중단 오프라인 폴백, Virtual Console `DO: true UxR: 80` 주행 제한/단일 선택, 루트 `Back`, `PSS 89.07~105.82 MB`/`CPU 0.0%`)를 검증했고, 헤드리스 환경상 **FormFactorConsole(Host Display Bridge)·실차**만 미검증 한계로 남겼다. [통합 검증 기록](verification-issue12.md)
- 최신 #6 완료 GCP 검증 빌드: `:shared:build`, `:backend:build`, `:frontend:app:assembleDebug`, `:frontend:app:assembleDebugAndroidTest` 통과(**공용 191개 + 서버 10개 = 201개 테스트 통과·스킵/실패/오류 0**). 라이브 백엔드 `/api/facilities/route-access`(기본 `NAVI_SDK` 2개 회랑·`mode=demo`·오류 모드 HTTP 400)와 `NavigationSmokeInstrumentation`(`routeAccessPlannerPipeline=passed`, `confirmedWaypoint=passed`, 15회 취소 복원, 후속 `initialRoute=EMPTY` 읽기 전용 스모크), `CryResponseSmokeInstrumentation`(`routeAccessProviderIntegration=passed`, `personalDataPreserved=true`)까지 모두 통과했다. [시설 검증 기록](facilities.md)
- 이전 #5 완료 GCP 검증 빌드: `:shared:build`, `:backend:build`, `:frontend:app:assembleDebug`, `:frontend:app:assembleDebugAndroidTest` 통과(**공용 189개 테스트 완료·실패 0**, `:backend:test` 통과). `NavigationSmokeInstrumentation`(`mutateEmulatorRoute=true`, `officialPoiWaypoint=true`)이 `initialRoute=EMPTY`에서 출발해 공식 `poiId` 경유지 실제 등록 성공(`confirmedWaypoint=passed`)과 15회 취소 복원(`fixtureCleanup=passed`), 이어진 읽기 전용 스모크(`initialRoute=EMPTY`, `passive=passed`), 실제 Android 울음 대응 회귀 검증(`CryResponseSmokeInstrumentation`, `personalDataPreserved=true`)까지 모두 통과했다. [후속 검증 기록](navigation-repro.md)
- #8 GCP 검증은 공용 179개 + 서버 9개 = 188개 테스트와 앱 설치·기기 컨트롤러/저장 검증을 통과했다. 당시 빌드 입력 109개 파일과 설치 APK의 지문을 대조했다. [울음 대응 검증 기록](response.md)
- 수동/임박 진입·반복 억제·두 정차 선택·기록 후 복귀·기록 없이 완료·첫날/시설 없음/내비 실패를 재현했다. 화면 20개로 두 창 크기·주야간·주행 제한과 주차 후 상세 복원을 확인했다. 저장 확인 실패에는 대응을 유지했다. 데모와 임시 검증이 기존 개인 돌봄·준비물을 변경하지 않았다.
- #7 단계에서는 171개 테스트와 출발 선택 3가지·기록/취소·첫날·90분 지연·외출 범위·준비물 추가/재실행 복원, 주행 시작 때 입력·키보드 종료를 확인했다. [출발 예보 검증 기록](departure.md)
- #6 1차 단계에서는 공용 137개 + 서버 9개 = 146개 테스트, 공개 시설 468건 API와 Kotlin 재수집 결과 일치, 화면·캐시를 검증했고, 2차 단계(2026-11-13)에서 경로 접근성 공급자 파이프라인 연결과 201개 테스트·에뮬레이터 전 구간 계측까지 완료했다. [시설 검증 기록](facilities.md)
- 이전 #6 1차 실행의 내비 읽기 스모크는 `read=FAILED`였으나, 2026-11-13 후속 실행에서 기본 내비 복구·`(0, 0)` 응답 원인 확인·공식 POI 경유지 실제 등록 성공(#5) 및 `RouteAccessCatalog` 기반 후보→경유지 등록 전 구간(#6)을 모두 검증했다. [내비 검증 기록](navigation-repro.md)
- 로컬에는 JDK·Android SDK가 없어 위 결과는 GCP 실행 결과다. 실행 스크립트 변경은 Bash 구문 검사와 실제 HOME/LAUNCHER 동작을 확인했다.
- 11/11~11/12 초기 캡처의 GCP AVD 화면 규격이 잘못돼 있었다. 공식 기기 프로필의 2560×1440·160dpi로 맞추고 Cold boot한 뒤 앱과 차량 시스템 UI를 다시 확인했다. 이전 규격의 시각 검증 주장은 정정했다. [화면 규격·재검증](care-records.md)
- 현재 앱의 첫 화면은 출발 예보·출발 선택·준비물·바로 기록과 큰 울음 대응 진입으로 구성했다. 글자 위계·대비·터치 크기와 실제 두 창 크기의 주간/야간을 확인했고, 디자인 토큰·시각 결함 개선(#16)과 최종 회귀 테스트까지 반영했다.
- 2026-11-14: 실행 가능한 제출용 release APK를 만들고 Cold boot 에뮬레이터에 새로 설치해 확인했다(서버 없이 시설 468곳, 주행 제한 전환, 야간·좁은 창, PSS 약 69MB). 시연 영상 편집본은 아직 없다.

현재 #12(에뮬레이터·단위/번들·UI 통합 검증, FormFactorConsole·실차만 미검증)와 #16(디자인 토큰·UI/UX 시각 개선) 반영을 마쳤으며, 제출 준비(#13)로 진행할 수 있는 상태다. 부분 완료(#12)를 합산하지 않은 #1 로드맵 기준 완료율은 P0 78%·전체 54%다.
