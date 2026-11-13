# 작업 인계 (2026-11-14)

1차 제출 마감은 **2026-11-15(일) 23:59**다. 이 문서는 지금까지 한 일, 남은 일, 문서·브랜치를 다루는 방법을 한곳에 모은 것이다.
규칙의 기준은 [`docs/contest/rules.md`](../contest/rules.md), 기능별 구현·검증 기록은 [`status.md`](status.md)에서 시작한다.
개인 연결 정보·실명·드라이브 링크는 익명성 규정 때문에 이 레포에 적지 않는다. 그런 내용은 팀 드라이브의 인계 문서에 있다.

## 1. 지금 상태

| 항목 | 상태 |
| --- | --- |
| P0 기능(#2~#8) | 구현·검증 완료. 기록 → 예보 → 출발 코칭 → 울음 대응·정차 선택 |
| 통합 검증(#12) | 에뮬레이터 범위 완료. FormFactorConsole·실차만 미검증 |
| 디자인 토큰·UI/UX(#16) | 완료 |
| release APK | `apk/아가랑_아가랑_release.apk`(약 49MB, v2·v3 서명). 서버 없이 동작 |
| README 1~4장 | 채움. "확정 예정" 없음 |
| main 반영 | **PR #17 리뷰 대기.** main에는 아직 초기 구성만 있다 |
| P1(#9~#11)·P2(#14) | 미구현. 1차 결과(11-23) 이후 2차 PT(12-03~04)까지의 범위 |

## 2. 2026-11-13~14에 한 일

1. 조작·상태 중심 UI/UX 정리와 자체 아이콘 추가([`ux-refinement.md`](ux-refinement.md), [`icons.md`](icons.md)).
2. **release가 서버 없이 동작하게 변경.** 백엔드의 `backend/src/main/resources/facilities/*.json`(공개 시설 468곳, 경로 접근성)을 빌드할 때 앱 assets로 넣고, release는 `BuildConfig.BUNDLED_FACILITY_DATA`로 assets에서 읽는다. debug에서 같은 동작을 보려면 `-Phmg.bundledData=true`.
3. **release 서명.** `HMG_RELEASE_STORE_FILE`·`HMG_RELEASE_STORE_PASSWORD`·`HMG_RELEASE_KEY_ALIAS`·`HMG_RELEASE_KEY_PASSWORD` 환경변수가 모두 있을 때만 서명한다. AGP 기본값은 minSdk 28에서 v2만 넣으므로 v3를 명시적으로 켰다.
4. 앱 이름을 **아가랑**으로 확정하고 런처 이름·README·AGENTS.md에 반영했다. 한 줄 소개는 접수양식 문구와 같다.
5. 제출 APK를 Cold boot한 에뮬레이터에 새로 설치해 확인했다(아래 4장).
6. 기능 커밋만 골라 main용 PR #17을 만들었다.
7. 시연 영상용 실화면 3개를 제출 APK로 녹화했다(팀 드라이브).

## 3. 남은 일 (마감 전)

- [ ] **PR #17 리뷰 → 머지.** 심사 링크는 main 기준으로 낸다. 머지하면 #3~#8, #16이 함께 닫힌다.
- [ ] **접수양식 5번(개발 계획)·7번을 실제 구현에 맞게 고치기.** 1차본에는 구현하지 않은 내용(LLM 설명 문장, 공조·트립 조회, 길안내 TBT, 휠 미디어 버튼, 케어 모드·회고)이 "구현"처럼 적혀 있고, 준비 중인 SDK(STT·TTS·FusedLocation)가 "사용 가능"으로 적혀 있다. 수정안은 팀 드라이브에 있다. 허위 기재는 선정 취소 사유다([`rules.md`](../contest/rules.md) 체크리스트).
- [ ] 시연 영상 편집. 대본의 장면 3(주행 중 케어 모드)은 미구현 기능이라 "주행을 시작하면 꼭 필요한 정보만 남긴다"로 바꾼다.
- [ ] 접수양식 서명(팀원 전원), 팀명 표기 통일(접수양식의 팀명과 APK 파일명의 팀명 부분).
- [ ] 제출 전 체크리스트([`rules.md`](../contest/rules.md) 7장)를 하나씩 확인하고 이메일 제출.
- [ ] 심사용 GitHub 계정이 공지됐는지 확인(레포가 비공개가 되면 Collaborator 초대 필요).

## 4. 제출 APK 검증 결과와 한계

Pleos Connect 에뮬레이터 v2.1.0(2560×1440·160dpi, Cold boot, 헤드리스)에 `apk/아가랑_아가랑_release.apk`를 새로 설치했다.

- 설치·실행, 크래시 없음, v3 서명 인식, 콜드 스타트 약 6.7초.
- 서버 없이 "한국도로공사 · 공공데이터포털 468곳" 표시.
- Virtual Console에서 D·주행 시 `DO: true / UxR: 80` → 주행용 요약 화면, P 복귀 시 홈 복원.
- 야간 모드, 좁은 창(841dp), 최상위 Back(`moveTaskToBack`), PSS 약 69MB·CPU 0.0%.

알아 둘 것:

- **Cold boot 직후에는 기본 내비 앱을 한 번 열어야 한다.** 그 전에는 내비 서비스가 경로 상태 조회에 응답하지 않아 "내비 정보를 받지 못했어요"가 뜬다(README 3장 6번).
- 헤드리스 에뮬레이터는 길게 쓰면 그래픽 오류로 꺼질 수 있다. 다시 Cold boot한다.
- Virtual Console(`:8088`)은 브라우저 없이 HTTP로도 조작할 수 있다. `POST /browser/session?client=<이름>`을 1초마다 보내는 동안 `PUT /vehicle/gear?gear=Drive|Park`, `PUT /vehicle/controls?speed=&accelerator=&brake=&steering=`가 동작한다.
- FormFactorConsole·실차는 검증하지 못했다. 창 크기는 `am task resize`로 대신 확인했다.

## 5. 브랜치와 PR

- 기능 작업은 개인 작업 브랜치에서 하고, **main에는 PR로만** 넣는다. main에 직접 push하지 않는다.
- 개인 작업 브랜치에는 개인용 개발 도구 커밋이 섞여 있을 수 있다. `scripts/prepare-main-pr.sh <pr-브랜치> <커밋...>`이 `origin/main` 위에 기능 커밋만 옮긴 PR 브랜치를 만든다(개인 브랜치에만 있는 도구다).
- PR #17 브랜치(`pr/agarang-p0-submission`)에 추가 수정이 필요하면 그 브랜치에 직접 커밋하거나, 기능 커밋을 cherry-pick한다.
- **커밋 전에 git `user.name`·`user.email`이 익명 값인지 확인한다**(`git var GIT_AUTHOR_IDENT`). 전역 설정이 개인 계정이면 레포 로컬 설정으로 덮어쓴다. 커밋 메시지·PR 본문·이슈에도 실명·연락처를 쓰지 않는다.

## 6. release APK를 다시 만들 때

1. 코드 변경을 커밋한다.
2. 서명 환경변수 4개를 설정하고 `./gradlew :frontend:app:assembleRelease`.
3. `apksigner verify -v --min-sdk-version 24 <apk>`로 v2·v3 서명을 확인한다.
4. `apk/아가랑_아가랑_release.apk`를 교체하고, 에뮬레이터에 새로 설치해 4장의 항목을 다시 본다.

서명 키는 레포에 없다(`*.jks`는 gitignore). 지금 APK를 만든 키는 그 빌드 환경에만 있다. 다른 키로 서명해도 심사자가 새로 설치하는 데는 문제가 없지만, 기존 설치본 위에 덮어 설치는 되지 않는다.

## 7. 문서를 어디에 두는가

| 내용 | 위치 |
| --- | --- |
| 공모전 규칙·제출 체크리스트 | `docs/contest/rules.md` |
| 기능별 설계·검증 기록, 현황 | `docs/agarang/` (`status.md`가 색인) |
| Pleos·차량 UI 가이드 요약 | `docs/guides/` |
| 에이전트 지침 | `AGENTS.md`(단일 원본), `CLAUDE.md`, `.claude/skills/` |
| 기획서·접수양식·영상·디자인 산출물, 인계용 개인 정보 | 팀 드라이브(레포에 링크·ID를 적지 않는다) |

기능을 바꾸면 해당 `docs/agarang/*.md`와 `status.md`를 같은 커밋에서 고친다. 검증하지 않은 것은 "미검증"으로 적는다.
