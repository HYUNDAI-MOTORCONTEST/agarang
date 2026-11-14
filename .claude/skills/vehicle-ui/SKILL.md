---
name: vehicle-ui
description: Pleos Connect 차량 앱의 Jetpack Compose UI(화면, 컴포넌트, 테마, 레이아웃)를 새로 만들거나 고치거나 리뷰할 때 쓴다. frontend/app의 @Composable, 색·글자 크기·터치 영역, 가로·세로(portrait/landscape) 레이아웃, 주간/야간 테마, 운전 중 제한(CarUxRestrictions)과 관련된 작업이면 먼저 이 스킬을 따른다. 백엔드·Gradle 설정·순수 데이터 로직만 바꾸는 작업에는 쓰지 않는다.
---

# 차량 UI 작업 절차 (Pleos Connect, Compose)

## 1. 먼저 읽기
1. `docs/guides/pleos-ui.md` — 특히 10장 "Compose 구현 실무 규칙" 표.
2. 컴포넌트(팝업, 토스트, 스위치, 필드, 세그먼트 메뉴 등)·모션·그림자가 걸리면 `docs/guides/pleos-components.md`에서 해당 컴포넌트 절과 6장 우선순위 표.
3. 운전 중 동작이 걸리면 `docs/guides/pleos-dev.md` 2장(주행 규제)·3장(주의 분산).
4. Back 처리·로터리(CCP)·키보드(IME)·소리가 걸리면 `docs/guides/pleos-platform.md`(0장 핵심 규칙표).
5. 공용 컴포넌트 `frontend/app/src/main/java/com/hmgchallenge/app/ui/components/`와 디자인 토큰 `ui/theme/PleosTokens.kt`(`PleosTheme.dimens/shapes`, `PleosMotion`, `PleosElevation`)·적용 도우미 `ui/theme/PleosEffects.kt`(`pleosShadow`, `pleosTween`). 같은 역할의 컴포넌트가 있으면 새로 만들지 않고 쓴다.
6. 수정할 화면 코드(`frontend/app/src/main/java/com/hmgchallenge/app/`)와 `AndroidManifest.xml`.

## 2. 요약에 없을 때만 원문 조회
- Pleos SDK·개발 가이드: Context7 `mcp__context7__query-docs`, `libraryId="/websites/document_pleos_ai_en"`(영어, 부분 색인).
- AAOS 주의 분산·CarUxRestrictions: `libraryId="/websites/source_android_automotive"`.
- Compose API: `mcp__context7__resolve-library-id`로 ID를 찾은 뒤 `query-docs`.
- Pleos 디자인 가이드 세부(한국어)는 필요한 한 페이지만 WebFetch로 읽는다. 원문을 레포에 복사하지 않는다.
- 조회 결과로 결정한 수치는 `docs/guides/` 요약에 출처 URL과 함께 반영한다.

## 3. 우선순위
Pleos UI 디자인 가이드 → Pleos 기획·개발 가이드·SDK 레퍼런스 → Google Design for Driving / Android for Cars → AAOS 주의 분산 가이드라인 → 일반 Android/Compose.
- Pleos 앱은 Car App Library 템플릿 앱이 아니다. 구글 템플릿 제약은 적용하지 않는다.
- Pleos에 수치가 없어 다른 근거를 쓰면 코드 주석에 근거를 짧게 적는다(예: `// 터치 64dp: Pleos 수치 없음, Google car-app-quality UX-1`).

## 4. 구현 체크리스트
- [ ] **대비**: 텍스트·아이콘과 배경의 대비 4.5:1 이상. 주간·야간 테마 둘 다 계산한다.
- [ ] **글자 크기**: 본문·버튼 24sp 이상, 핵심 수치·경고 32sp 이상(40sp 권장). 12sp 같은 휴대폰 크기 금지.
- [ ] **터치 영역**: 최소 64dp × 64dp(주요 버튼 72dp+), 터치 대상 간격·화면 가장자리 24dp 이상. `Modifier.sizeIn(minWidth = 64.dp, minHeight = 64.dp)` 등으로 보장.
- [ ] **아이콘**: 48dp 기준, 텍스트 라벨 병기.
- [ ] **가로·세로**: `screenOrientation` 고정 금지. 너비로 portrait(1단, 핵심 기능)·landscape(2단, 부가 정보) 배치를 나눈다. window 크기가 바뀌어도 잘리거나 겹치지 않게 한다.
- [ ] **주간/야간**: `VehicleTheme`(ui/theme/Theme.kt)이 시스템 야간 모드로 Pleos 토큰 두 벌(`PleosColors.Light/Dark`)을 고른다. 색은 `MaterialTheme.colorScheme`이나 `LocalPleosColors`에서만 가져오고 하드코딩하지 않는다.
- [ ] **글자**: 글꼴은 Asta Sans, 크기는 `LocalPleosTypography`/`MaterialTheme.typography` 토큰만 쓴다. `fontSize = 30.sp` 같은 하드코딩 금지.
- [ ] **운전 중 제한**: 동영상·애니메이션·자동 스크롤·움직이는 이미지 없음. `LocalUxRestrictions.current`(driving/UxRestrictions.kt)로 상태를 읽어, 제한 중이면 상세 정보를 숨기고 키보드 입력, 설정·로그인, 긴 텍스트, 영상을 막고 "주차 후 이용" 같은 짧은 안내를 보인다.
- [ ] **텍스트 길이**: 한 메시지 3줄 이하, `maxLines` + `TextOverflow.Ellipsis`. 문자열은 `strings.xml`.
- [ ] **응답성**: 터치 즉시 pressed·loading 표시(0.25초 기준). Vehicle SDK·네트워크 호출은 메인 스레드를 막지 않는다.
- [ ] **조작 단순화**: 자주 쓰는 기능은 첫 화면에, 작업은 3번 터치 이내. 드롭다운·다단 메뉴 지양. 키보드보다 프리셋·최근 항목·음성.
- [ ] **모션**: 애니메이션은 Pleos easing·duration 토큰(`pleos-components.md` 4장)을 따른다. 장식·전환 애니메이션은 운전 중 끄고, 누름 피드백(95% 축소·색 변화)은 유지한다.
- [ ] **Back**: 최상위 화면 Back은 `moveTaskToBack(true)`(`finish()` 금지, `pleos-platform.md`).
- [ ] **시스템 UI와 겹침**: Status bar·GNB·Window bar 같은 시스템 영역을 앱이 그리거나 덮지 않는다.
- [ ] **실패 상태**: Vehicle SDK `onFailed`, 권한 없음, VIN "invalid"(에뮬레이터)에서도 화면이 깨지지 않고 상태를 보여 준다.
- [ ] **권한**: 새 차량 도메인을 쓰면 실제로 쓰는 `pleos.car.permission.*`만 main 매니페스트에 추가(`pleos-dev.md` 5장, 공모전 허용 범위는 `docs/contest/rules.md` 1장). 개발 확인용 권한은 debug 매니페스트에 둔다.

## 5. 검증 (실제로 실행)
1. 빌드: `./gradlew :frontend:app:assembleDebug`
2. 에뮬레이터(Cold boot) 실행: `scripts/pleos-emu.sh --wait` (이미 실행 중이면 그대로 둔다)
3. 설치·실행: `scripts/run-app.sh`
4. **portrait와 landscape 둘 다** 캡처한다. 에뮬레이터 화면에서 앱 window 버튼을 누르거나 window bar를 끌어 window 1개(portrait)·2개(landscape)를 오간다.
   ```bash
   scripts/screenshot.sh "$TMPDIR/vehicle-ui-portrait.png"
   scripts/screenshot.sh "$TMPDIR/vehicle-ui-landscape.png"
   ```
   캡처 파일은 git에 넣지 않는다. 캡처를 열어 잘림·겹침·글자 크기·대비를 4장 체크리스트로 확인한다.
5. 주간/야간: 시작 전 `adb shell cmd uimode night`로 현재 값을 적어 두고, `adb shell cmd uimode night no`/`yes`로 바꿔 각각 캡처한 뒤 원래 값으로 되돌린다. Pleos 에뮬레이터 v2.1.0에서 동작 확인함(앱 재시작 없이 바로 바뀜).
6. 운전 중 제한을 건드렸다면 `scripts/pleos-console.sh`로 Virtual Console을 열고 기어 D·속도를 줘 주행 상태로 바꾼 뒤 캡처한다. 상태 확인: `adb shell dumpsys car_service --services CarUxRestrictionsManagerService | grep 'Display id'`. 끝나면 P·속도 0으로 되돌린다.
7. 결과 보고: 실행한 명령, 캡처 경로, 체크리스트에서 못 지킨 항목과 이유.
