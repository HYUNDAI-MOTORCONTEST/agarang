# 공모전 기준 규칙 (개발·제출 내내 지킬 것)

> 현대자동차그룹 차량용 앱 개발 공모전의 공식 안내를 팀이 직접 요약한 문서다. **원문이 최종 기준**이며, 요약과 원문이 다르면 원문을 따르고 이 문서를 고친다.
> 확인일: 2026-11-03(사이트 전체 페이지·공지 3건·온라인 설명회 다시보기 [OT] 반영). 일정이 바뀌면 공모전 공지사항으로 안내된다고 되어 있다([SCH]). 공지가 새로 올라오면 이 문서도 갱신한다.
> 표기: `[약어]`는 맨 아래 "출처"의 URL이다. **문서에 없음/미확정**은 원문에서 찾지 못한 항목이다(추측으로 채우지 않는다).

## 1. 한눈에 보기

| 언제 (KST) | 무엇 | 출처 |
| --- | --- | --- |
| 2026.10.28(수) 14:00 | 온라인 설명회 (완료, 다시보기 [OT]) | [SCH], [OT] |
| **2026.11.15(일) 23:59** | **접수 마감** (이메일). 마감 후 제출은 인정되지 않음 | [SCH], [FORM], [N6] |
| 2026.11.23(월) | 1차 서류심사 결과 발표 (합격 20팀) | [SCH], [N6] |
| 2026.12.03(목)–12.04(금) | 2차 최종 PT (오프라인). **이때까지 실제 동작하는 앱 완성** | [SCH], [RUL] |

꼭 지킬 규칙

1. **마감 전 마지막 제출본만 심사**한다. 수정 제출 때도 전체 자료와 서명 동의서를 다시 첨부한다([SUB], [N6]).
2. **익명성**: 기획서 작품 소개·공유 링크·첨부자료에 실명, 연락처, 이메일, 소속, 계정 정보를 노출하지 않는다. 레포·커밋 작성자 정보도 같은 기준으로 본다([FORM]).
3. **APK**: 실행 가능한 release 빌드를 `apk/팀명_앱이름_release.apk`로 레포에 함께 올린다. APK만 따로 내지 않는다([SUB], [FAQ]).
4. **README 필수 4항목**(앱 이름·한 줄 소개 / 실행 방법 / 테스트 환경·설정 / APK 위치 또는 Release 링크)을 지우거나 빼지 않는다([SUB]).
5. **SDK·권한**: 공모전 개발 단계에서는 SDK 5종(Vehicle·NaviHelper·TTS·STT·FusedLocation)을 **권한 신청·승인 없이 매니페스트 선언만으로** 쓸 수 있다([OT] 31:46, 36:42). 승인되지 않은 권한은 App Market 배포 심사에서 걸리므로, 쓰는 도메인 권한만 선언한다([P-SDK], [P-PERM]).
6. **차량 UX**: 가로·세로 레이아웃을 모두 지원하고, 운전 중 제한(CarUxRestrictions)과 주의 분산 가이드를 따른다([P-SCREEN], [P-DR], [P-DD]).
7. **순수 창작·제3자 권리**: 표절·타 공모전 수상작 금지. 글꼴·이미지·라이브러리 라이선스를 지킨다([N6]).

### 설명회(10.28)에서 새로 확인한 것 ([OT], 괄호는 영상 시각)

| 항목 | 내용 |
| --- | --- |
| SDK 사용 | 공모전에서는 권한 신청·승인이 필요 없다. SDK 문서의 user permission을 매니페스트에 선언하면 에뮬레이터에서 바로 쓸 수 있다. 승인 여부는 App Market 배포 심사 때 본다(31:46). Vehicle·NaviHelper·STT·TTS·FusedLocation 모두 해당(36:42). "현재 공개되지 않은 SDK까지 제공할 예정"이라는 언급도 있었다(24:35) |
| 외부 API·네트워크 | 외부 API를 함께 쓸 수 있고 퍼블릭 네트워크 연결도 된다(35:43) |
| 생성형 AI | 기획서 작성·개발 전 과정에 쓸 수 있다. 단 전체 구조와 로직을 이해하고 2차 PT 기술 Q&A에서 직접 설명할 수 있어야 한다(20:28) |
| 시연 | 실차 시연은 없다. 앱 검증은 Pleos Playground 에뮬레이터로 한다(20:28) |
| 복수 출품 | 가능, 개수 제한 없음. 같은 작품의 수정 제출과는 구분하고 앱명으로 출품을 구분한다(19:09) |
| 부문 | 일반인이 한 명이라도 있으면 일반 부문. 대학생이면서 스타트업을 하는 경우는 대학생 부문(17:51, 21:24) |
| 메일 제목 | 설명회 장표는 앱명을 넣은 `[차량용 앱 개발 공모전] 앱명_팀명(개인은 성명)_대표자명`(수정 제출은 앞에 `[수정제출] `). 사이트·접수양식은 앱명이 없는 형식이다. 앱명을 넣은 장표 형식을 쓰면 두 안내를 모두 만족한다 |
| 아이디어 특별상 | 개발 공모전이라 아이디어 특별상도 개발 산출물 제출이 필요하다(06:14) |
| 숏폼 | 초상권·저작권은 응모자 책임, 차량 내외부 이미지 노출 최소화(16:38) |
| 운영 문의 | 외국인·미성년자 참가 조건은 추후 공지. 추가 질문은 접수 이메일로(19:09) |

## 2. 일정 전체 (KST, [SCH] 기준)

| 날짜 | 일정 | 메모 |
| --- | --- | --- |
| 2026.10.21(수) | 공고·접수 시작, 숏폼 챌린지 시작 | 숏폼은 접수 기간과 같이 진행([FAQ]) |
| 2026.10.28(수) 오후 | 온라인 설명회 (라이브 1회, 다시보기 예정) | 프로그램: 공모전 안내·개발환경 소개·질의응답. 아래 8장 질문 준비 |
| 2026.11.15(일) 23:59 | 접수 마감 | 23:59는 접수양식에만 적혀 있음([FORM]). 사이트 일정표는 날짜만 표기 |
| 2026.11.23(월) | 1차 서류심사 결과 발표 | 합격팀은 공지사항으로 안내([JDG]) |
| 2026.12.03(목)–12.04(금) | 2차 최종 PT (오프라인 발표) | 장소: 현대자동차 강남대로사옥 1층 UX스튜디오 서울(강남구 강남대로 350) |
| 2026.12.18(금) | 최종 선정 결과 발표 (10팀) | 순위는 2차 PT 결과물 기준으로 확정([JDG]) |
| 2026.12.19–2027.01 | 수상팀 전시 준비 기간 | 추가 개발 기간이 아님. 전시 영상·APK 구동 테스트용([JDG], [FAQ]) |
| 2027.02 | 현대자동차그룹 행사 전시·시상식 (최종 순위 발표) | 최종 10팀은 현장 시연·시상식 참석 의무([N6]) |

## 3. 기술 요건

| 항목 | 기준 | 출처 |
| --- | --- | --- |
| 플랫폼 | Pleos Connect (Android Automotive OS 기반 차량 인포테인먼트) | [DEV], [P-SDK] |
| 언어 | Java 또는 Kotlin. **팀 규칙은 Kotlin만**(`AGENTS.md`) | [DEV], [FAQ] |
| 최소 SDK | Android API 28 이상 (차량 보안 요건: 서명 키 로테이션·리니지) | [DEV], [P-SETUP] |
| 빌드 도구 | Gradle 8.0 이상 | [DEV], [P-SETUP] |
| targetSdk·compileSdk | 공모전 요건으로는 문서에 없음 | — |
| 앱 주제 | 제한 없음(예시는 참고용) | [DEV] |
| 시연 환경 | Pleos Connect Emulator(Android Studio 시스템 이미지) 또는 기기. 실차·개발 기기 제공 여부는 문서에 없음 | [P-SETUP], [N6] |

사용 가능한 SDK와 현재 상태 (공모전이 허용한 SDK는 아래 5종, [FAQ], [P-PROJ], [OT])

| SDK | 용도 | 공모전에서 | Playground 표기(App Market 배포 기준) |
| --- | --- | --- | --- |
| Vehicle SDK (Vehicle State) | 차량 상태 조회 | 사용 가능(매니페스트 선언) | 사용 가능 — 신청 없이 |
| NaviHelper SDK | 길 안내 연동 | 사용 가능(매니페스트 선언) | 사용 가능 — 신청 없이 |
| FusedLocation SDK | 위치 | 사용 가능(매니페스트 선언, [OT]) | 준비 중(Coming Soon) |
| TextToSpeech / SpeechToText SDK | 음성 출력·입력 | 사용 가능(매니페스트 선언, [OT]). 온디바이스 모드는 클라이언트 ID·시크릿 불필요(`docs/guides/pleos-sdk.md`) | 준비 중(Coming Soon) |
| Vehicle Control SDK (Vehicle SDK의 set 계열) | 차량 제어 | 설명회는 "공개되지 않은 SDK까지 제공 예정"이라고만 함. 쓰기 전에 에뮬레이터 동작 확인 | 준비 중(Coming Soon) |

- 상태는 바뀔 수 있다. "제공 상태와 사용 조건은 공식 문서에서 확인"하라고 되어 있다([DEV]).
- **차량 권한**: 공모전 개발·심사 단계에서는 SDK 문서의 user permission을 매니페스트에 선언해 쓴다([OT]). App Market에 배포할 때는 승인된 권한만 선언해야 심사를 통과한다([P-SDK], [P-PERM]). 그래서 쓰는 도메인 권한만 선언한다(현재 선언 목록은 `AGENTS.md`).
- **레이아웃**: 가로 전용 게임 같은 예외를 빼면 가로(landscape, window 2개)·세로(portrait, window 1개) 모두에 맞게 조절돼야 한다. `screenOrientation`을 고정하지 않는다([P-LAYOUT], [P-SCREEN]).
- **운전 중 제한·주의 분산**: CarUxRestrictions를 받아 동영상·애니메이션·자동 스크롤·텍스트 입력 등을 줄이거나 막고, 운전자 주의 분산 가이드를 따른다([P-DR], [P-DD]). 상세와 팀 규칙은 [../guides/pleos-dev.md](../guides/pleos-dev.md)(2·3장), UI 수치는 [../guides/pleos-ui.md](../guides/pleos-ui.md).
- **에뮬레이터**: Cold boot 필수(quick boot에서는 VHAL 사용 불가)([P-AVD]). 실행은 `scripts/pleos-emu.sh`.

## 4. 제출물

| 구분 | 제출물 | 비고 | 출처 |
| --- | --- | --- | --- |
| **필수** | 접수양식(= 기획서, DOCX) | 개인정보 동의서 포함. 대표자 포함 **팀원 전원이 각자 동의 여부·서명·날짜** 작성. CC만으로 동의 대체 불가 | [SUB], [FORM] |
| 선택·가산점 | 시연 영상 | 목업·프로토타입·화면 녹화 인정. 길이·형식·전달 방식은 문서에 없음 | [SUB], [N1] |
| 선택·가산점 | GitHub 저장소 (소스 코드 + APK) | 함께 내면 단계별 가산점 | [SUB], [JDG] |
| 선택·가산점 없음 | 숏폼 링크 | 접수양식 8번에 기재 | [SUB], [FORM] |

접수양식(기획서) 2장 항목: 1 한 줄 소개, 2 기획 배경, 3 주요 기능·차량 특화 요소, 4 사용 시나리오, **5 개발 계획(Pleos Connect 구현 범위와 SDK/API 사용 계획)**, 6 기대효과·발전 가능성(타깃·예상 MAU·Retention 등), 7 참고자료(시안·영상·GitHub, 가산점), 8 숏폼 링크. 분량 제한 없음([FORM]).

APK 규칙 ([SUB], [FAQ])
- 실행 가능한 **release** 빌드. 레포 안 `apk/` 폴더에 `팀명_앱이름_release.apk`로 둔다.
- GitHub 업로드 용량을 넘으면 GitHub Releases에 올리고 그 Release 페이지 주소를 낸다(용량 수치는 공모전 문서에 없음).
- APK만 따로 제출하지 않는다.

README.md 필수 4항목 ([SUB]) — 우리 README 1~4장이 이 순서다.
1. 앱 이름 및 한 줄 소개
2. 실행 방법
3. 테스트 환경 및 필요한 설정
4. APK 파일 위치 또는 GitHub Release 링크

공개·비공개 레포 ([SUB], [FAQ])
- 비공개(Private)도 가능. 이때는 **공지된 심사용 GitHub 계정을 Collaborator로 초대**하고 마감 전에 접근되는지 확인한다.
- 개인 계정 비공개 레포는 Collaborator에게 쓰기 권한까지 주므로 초대 전에 권한을 확인한다.
- 심사용 계정명은 **아직 공지되지 않음**(2026-10-25 기준).

제출 방법 ([SUB], [RUL], [FORM])
- 접수 이메일: `appdevchallenge@hyundai-partners.com` (공식 공개 주소. 문의도 같은 주소, [FAQ])
- 제목: `[차량용 앱 개발 공모전] 앱명_팀명(개인은 성명)_대표자명` — 설명회 장표 형식([OT]). 사이트·접수양식은 앱명이 빠진 `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명`이다
- 대표자가 **팀원 전원을 CC**한다. CC 주소는 다른 팀원에게도 보인다는 점을 미리 알린다([FORM]).
- 수정 제출: 마감 전, 처음 제출한 것과 같은 이메일로 다시 보낸다. 제목 `[수정제출] [차량용 앱 개발 공모전] 앱명_팀명(개인은 성명)_대표자명`, 전체 자료 + 서명 동의서 재첨부 + 팀원 전원 CC. 마감 전 마지막 제출본을 심사한다.
- 공모전 사이트 회원가입은 필요 없다([FORM]).

## 5. 심사 기준과 대비

1차 서류심사: 아래 4개 필수 항목 + 가산점. 세부 배점은 비공개([JDG], [N6]).

| 항목 | 원문 요지 | 우리 레포에서 대비할 것 |
| --- | --- | --- |
| 차량특화성 | 모바일 대비 차량만의 가치, 주행·정차 맥락과 차량 기능의 결합 | 주행/정차 상태별 화면 차이와 Vehicle SDK 연동을 README·영상에서 바로 보이게 한다 |
| 창의성 | 독창적 아이디어, 운전자·동승자 Pain Point와 해결 방식이 명확 | 기획서 2·4번의 문제·시나리오가 앱 첫 화면 흐름과 그대로 이어지게 한다 |
| 기술 활용성 | Pleos Playground SDK·API를 핵심 기능·경험에 적절히 활용 | 사용 가능한 SDK(Vehicle State·NaviHelper)를 핵심 기능에 쓰고, 어떤 API를 어디서 쓰는지 README 3장에 적는다 |
| 사업성 | 타깃 고객, 사용자 확보·지속 이용·확대 가능성(MAU, Retention 등) | 기획서 6번 수치의 근거를 정리하고, 앱 기능 범위가 그 타깃과 맞는지 확인한다 |
| [가산점] 개발 산출물 | 프로토타입 시연 영상, GitHub 저장소(소스+APK) | 마감 전까지 release APK·README·시연 영상을 갖춘 상태로 레포를 유지한다 |

- **2차 PT**: 합격 20팀은 PT 전까지 실제 동작하는 앱을 완성하고, PT에서 **에뮬레이터 구동 화면 또는 기기로 시연**해야 한다([RUL], [N6]). 1차 산출물은 PT 전까지 자유롭게 고도화할 수 있다. 2차 PT 심사 기준은 추후 공지([JDG]).
  - 대비: 에뮬레이터 Cold boot → release APK 설치 → 시연 시나리오가 네트워크·백엔드 없이도 끊기지 않는지 미리 리허설한다.
- **최종·전시**: 순위는 2차 PT 결과물 기준으로 확정. 최종 10팀은 전시 구동용 최종 APK를 필수 제출하고 2027.02 행사에서 현장 시연·시상식에 참석한다. 전시 준비 기간(2026.12.19–2027.01)은 추가 개발이 아니라 영상 제작·구동 안정성 테스트용이며, 추가 개발은 순위에 영향을 주지 않는다([JDG], [FAQ], [N6]).
- 상금: 총 5,100만원(대상 1,000만원·최우수 600만원·우수 400만원은 부문별, 아이디어 특별상 150만원 부문통합) + 숏폼 50만원 별도([JDG]).

## 6. 익명성·권리

- **익명성** ([FORM]): 실명·연락처·이메일·소속은 접수양식 01(참가신청서)에만 적는다. 02(작품 소개)와 **공유 링크·첨부자료**에 개인정보·계정정보·개발자 실명이 드러나지 않게 한다. GitHub 링크도 공유 링크이므로 아래를 지킨다.
  - 레포 파일·README·스크린샷·APK 리소스·코드 주석에 실명·연락처·이메일·소속·개인 계정명을 넣지 않는다.
  - **커밋 작성자·커미터 정보(`%an`, `%ae`, `%cn`, `%ce`)와 커밋 메시지**도 노출 대상이다. 공개 레포는 이력 전체가 보인다. 커밋 전에 git `user.name`·`user.email`을 익명 값(예: GitHub noreply 주소)으로 맞춘다.
  - 레포 URL의 소유자(계정·조직) 이름도 링크에 그대로 드러난다(판단 기준은 8장 질문).
- **순수 창작**: 제3자 작품을 표절했거나 이미 발표된 타 공모전 수상작은 심사에서 빠진다. 허위 기재는 선정 취소 사유([N6]).
- **제3자 권리**: 초상권·저작권·디자인권 등을 침해하지 않는다. 분쟁 책임은 응모자에게 있고, 수상 후에도 수상 취소·상금 환수가 될 수 있다([N6]). 번들 글꼴·에셋의 라이선스 전문은 `licenses/`에 둔다.
- **지식재산권** ([N6]): 출품작 IP는 응모자 소유. 단 수상작은 주최측이 통상실시권을 가질 수 있고(대가·범위·기간은 협의), 결과 발표일로부터 4개월간 IP 양도 등에 대한 우선협상권을 가질 수 있다.
- **임직원**: 현대자동차그룹 임직원은 참가는 가능하지만 최종 수상(시상·상금) 대상에서 제외된다([N6]).
- 개인정보 보관: 2027-02-28까지 보관 후 파기, 수상자는 결과 발표일로부터 2년([SUB], [FORM]).

## 7. 제출 전 체크리스트

- [ ] `./gradlew :frontend:app:assembleRelease`로 release APK를 만들고, Pleos 에뮬레이터를 **Cold boot**(`scripts/pleos-emu.sh --wait`)한 깨끗한 상태에서 설치·실행해 핵심 시나리오가 동작한다
- [ ] 가로·세로 전환, 주행 상태(Virtual Console 기어 D·속도)에서 운전 중 제한이 동작한다
- [ ] APK가 `apk/팀명_앱이름_release.apk`(실제 팀명·앱이름) 이름으로 있다. 용량 초과면 Releases 페이지 링크를 README 4장에 적었다
- [ ] README 1~4장(앱 이름·한 줄 소개 / 실행 방법 / 테스트 환경·설정 / APK 위치)이 채워져 있다(“확정 예정” 없음)
- [ ] 익명성 스캔: `git log --format='%an %ae %cn %ce' | sort -u`에 실명·개인 이메일이 없다
- [ ] 익명성 스캔: `git log --format='%s %b'`, README·문서·스크린샷·`res/`에 실명·연락처·소속·개인 계정명이 없다
- [ ] 시크릿 없음: `git grep -nIiE 'api[_-]?key|secret|password|BEGIN .*PRIVATE KEY'` 결과를 확인했고, `*.jks`·`keystore.properties`·`.env*`가 이력에 없다
- [ ] 선언한 `pleos.car.permission.*`·SDK 권한이 실제 쓰는 것뿐이다(공모전은 선언만으로 사용 가능. App Market 배포용 빌드를 따로 만들 때는 승인된 권한만 남긴다)
- [ ] 기획서·README의 수치는 원출처를 확인했다(출생아 수는 국가데이터처 확정치, Pleos Connect 탑재 계획은 현대차그룹 발표). 가정한 값은 "가정"이라고 적었다(허위 기재는 선정 취소 사유, [N6])
- [ ] 팀원 전원이 `docs/architecture.md`의 3~5장을 말로 설명할 수 있다(생성형 AI를 써도 PT 기술 Q&A는 직접 설명, [OT])
- [ ] 레포 공개 범위를 정했다: 공개면 기획 세부가 마감 전에 노출된다. 비공개면 심사용 계정을 초대했다
- [ ] GitHub 레포의 Activity·커밋 이력에 개인 계정명·이메일이 없다(익명 팀 계정으로 푸시)
- [ ] 기획서 5번(개발 계획)의 SDK/API 사용 계획과 실제 구현·README가 일치한다(준비 중 SDK를 "사용"으로 적지 않았다)
- [ ] 시연 영상을 만들었고, 영상에도 개인정보·계정 정보가 보이지 않는다
- [ ] 비공개 레포라면 심사용 계정을 Collaborator로 초대했고 접근을 확인했다
- [ ] 이메일 제목이 `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명`(수정이면 앞에 `[수정제출] `)이고, 팀원 전원이 CC에 있고, 전원 서명한 접수양식이 첨부됐다
- [ ] 2026.11.15 23:59 전에 보냈다(여유 있게 하루 전 목표)

## 8. 문서에 없음/미확정 항목과 설명회(10.28) 질문

문서에 없음/미확정 (2026-10-25 기준)

| 항목 | 상태 |
| --- | --- |
| release APK 서명 키 규정(자체 키 허용, debug 키 서명 허용 여부) | 공모전 문서에 없음 |
| 패키지명(applicationId) 규칙 | 문서에 없음 |
| 심사용 GitHub 계정명 | 공지 예정, 미공지 |
| 사용 가능한 오픈소스·외부 API(지도·AI·클라우드 등) 제한 | 문서에 없음 |
| Jetpack Compose 사용 가능 여부 | 금지·허용 모두 언급 없음 |
| targetSdk·compileSdk 요건 | 문서에 없음 |
| 시연 영상 길이·형식·전달 방식(첨부/링크) | 문서에 없음 |
| "저장소 주소와 APK 파일 경로를 제출 페이지에 입력"의 "제출 페이지" | 제출은 이메일로만 안내돼 있어 어디에 적는지 불명확([SUB]) |
| 1차 심사 시 레포를 어느 시점 기준으로 보는지(마감 후 push 반영 여부) | 문서에 없음 |
| 2차 PT 심사 기준, 전시 준비 기간 준수사항 | 추후 공지([JDG]) |

설명회에서 확인할 질문 (2026-11-03: 설명회 답이 나온 것은 → 뒤에 적었다. 나머지는 접수 이메일로 문의)

1. 언어 요건(Java 또는 Kotlin)은 차량 앱에만 해당하는가? 백엔드 서버의 언어·프레임워크 제한 범위는?
2. 앱이 외부 서버(자체 백엔드)나 외부 API에 의존해도 되는가? 심사 시 서버는 팀이 계속 띄워 둬야 하는가? → 외부 API·퍼블릭 네트워크 사용 가능([OT]). 서버 상시 가동 여부는 답 없음
3. 에뮬레이터에서 VIN이 "invalid"로 나온다. 심사·시연 시 VIN 같은 차량 식별 정보가 없는 상태를 전제로 해도 되는가?
4. Apple Silicon Mac에서 에뮬레이터 GPU 요건(문서의 권장·최소 사양은 NVIDIA/AMD 기준)은 어떻게 보면 되는가? PT 현장 시연 장비는 팀이 준비하는가? → 실차 시연 없음, 에뮬레이터로 검증([OT]). 장비 준비 주체는 답 없음
5. release APK 서명은 팀 자체 키로 하면 되는가? 패키지명 규칙이 있는가?
6. 심사용 GitHub 계정은 언제 공지되는가? 공개 레포의 소유자(계정·조직) 이름은 익명성 위반으로 보는가?
7. 레포 주소·APK 경로는 이메일 본문과 접수양식 7번 중 어디에 적어야 하는가?
8. 준비 중인 SDK(FusedLocation·TTS·STT·Vehicle Control)는 마감 전에 열릴 예정인가? 기획서에 "향후 사용"으로 적어도 되는가? → 공모전에서는 매니페스트 선언만으로 사용 가능([OT])
9. 차량 권한의 "승인 범위"는 공모전 단계에서 무엇을 기준으로 보는가(Vehicle State 조회 권한은 별도 승인 없이 써도 되는가)? → 공모전에서는 승인 불필요, App Market 배포 심사 때 확인([OT])
10. 1차 마감 이후 레포에 push한 내용이 1차 가산점 평가에 반영되는가?

## 출처

[OT]: https://youtu.be/na6tSJ44pWc (통합 온라인 설명회 다시보기, 2026-10-28, 42:40)
[OVW]: https://appdevchallenge.auto-hmg.io/guide/overview
[SUB]: https://appdevchallenge.auto-hmg.io/guide/submission
[RUL]: https://appdevchallenge.auto-hmg.io/guide/rules
[JDG]: https://appdevchallenge.auto-hmg.io/guide/judging-awards
[SCH]: https://appdevchallenge.auto-hmg.io/guide/schedule
[DEV]: https://appdevchallenge.auto-hmg.io/guide/development
[FAQ]: https://appdevchallenge.auto-hmg.io/faq
[N1]: https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000001
[N6]: https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000006
[FORM]: https://appdevchallenge.auto-hmg.io/downloads/contest-application-consent.docx
[P-SETUP]: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
[P-AVD]: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator
[P-PROJ]: https://document.pleos.ai/docs/pleos-only/create-my-project
[P-SDK]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/
[P-PERM]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission
[P-LAYOUT]: https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout
[P-SCREEN]: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model
[P-DR]: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
[P-DD]: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline

| 약어 | 문서 |
| --- | --- |
| SUB / RUL / JDG / SCH / DEV / FAQ | 공모전 참가 가이드: 지원·제출 방법 / 참가 규정 / 심사·시상 / 일정·설명회 / 개발 환경 / FAQ |
| N1 / N6 / FORM | 공지: 참가자 모집 공고 / 참가 및 출품 유의사항, 접수양식 DOCX |
| OT / OVW | 온라인 설명회 다시보기(YouTube) / 공모전 개요(고객 니즈 예시) |
| P-* | Pleos Playground 개발 문서(에뮬레이터 설치·설정, 프로젝트·SDK 신청, SDK·권한, 레이아웃, 화면 모델, 주행 규제, 주의 분산) |
