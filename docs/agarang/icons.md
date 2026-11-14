# 아가랑 UI 아이콘

2026-11-13에 만든 자체 아이콘 14종의 Outlined/Filled 시안 28개를 앱의 `res/drawable/ic_agarang_*.xml`에 연결했다. 원본 PNG·프롬프트·생성 기록은 `output/imagegen/agarang-icons-2026-11-13-v1/`에 보관한다.

## 표시 방식

- 공통 `ui/components/CareGlyph.kt`에서 벡터를 읽고 기존 Pleos 주간·야간 콘텐츠 색으로 tint한다. 기본은 Outlined, 선택된 탭·출발 카드·완료 체크는 Filled다. 누름·비활성 색은 기존 버튼 토큰을 따른다.
- 48dp 캔버스와 텍스트 라벨을 유지한다. 아이콘은 장식 요소이며 접근성 이름·선택 상태·터치 영역은 기존 상위 컨트롤이 제공한다. 주행 중 애니메이션을 추가하지 않았다.
- 프로필 설정에는 `baby`, 울음 대응 진입에는 `soothe`를 배정한다. `care`는 돌봄 선택·기록 탭에 쓴다. 수유·기저귀·수면·경로·경유지·준비물·출발·홈·되돌리기·체크·꺾쇠도 같은 세트로 교체했다.
- 체크박스의 컨테이너·체크 의미는 유지하고 내부 체크 모양만 새 Filled 리소스로 표시한다. `start`·`chevron`·`undo`는 RTL에서 방향을 반전한다.

## 원본에서 벡터로 변환

원본의 검정 윤곽을 VTracer 0.6.12의 binary/spline 모드로 추출했다. 흰 배경과 내부 흰 디테일은 투명한 빈 공간이며, 경로의 even-odd fill로 구멍을 보존한다. 기존 아이콘을 수동으로 다시 그리거나 새 디자인을 생성하지 않았다.

각 아이콘의 Outlined/Filled가 공유하는 경계를 기준으로 비율을 유지해 48dp 안의 40dp 영역에 중앙 정렬했다. 4dp 여백은 원본 제작 의도에 따른 팀의 정렬 기준이다. 같은 아이콘의 두 상태에 동일한 변환을 적용하므로 선택할 때 위치·크기가 흔들리지 않는다. 모서리·눈·입 등 내부 디테일은 원본 해상도 1024px에서 추출했다.

28개 XML의 합계는 296,967바이트다. 약 18.6MB인 원본 아이콘 PNG 28개를 앱에 번들하지 않는다. 원본 마스크와 정규화한 벡터를 384px에서 비교한 IoU 최솟값은 0.978740이며, 투명 모서리와 잘림 여부도 확인했다. 도구는 변환 때만 사용하며 앱의 런타임 의존성은 추가하지 않는다.

변환 파라미터·원본과 XML의 SHA-256·아이콘별 비교 결과·렌더는 Git에서 제외되는 `.dev/verification/icon-integration/2026-11-13/`에 보관한다.

기준: [Pleos 아이콘 가이드](https://document.pleos.ai/docs/connect/guide/docs-design/foundations/contents-creation/iconography), [Compose 리소스](https://developer.android.com/develop/ui/compose/resources), [그래픽의 접근성](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).

## 앱 검증

- `./gradlew :frontend:app:assembleDebug --console=plain -Pandroid.builder.sdkDownload=false`를 기존 GCP 검증 VM의 새 임시 폴더에서 실행해 성공했다(1분 35초). 로컬에는 JDK·Android SDK가 없어 로컬 빌드는 실행하지 못했다. 키·개인 설정을 제외한 입력 147개 파일을 사용자 승인 후 전송했고, 현재 로컬 입력도 같은 SHA-256 목록과 일치한다.
- Pleos AVD를 Cold boot하고 debug APK를 설치했다. 실제 앱 창 1682×1210dp(가로)·841×1210dp(세로) 각각의 주간/야간 화면과 세로 수동 스크롤 화면을 캡처해 직접 확인했다. 물리 해상도 2560×1440·160dpi를 유지하고 Pleos 창 막대를 드래그해 크기를 바꿨다.
- 첫 사용의 아기 아이콘, 울음 대응·수유·기저귀·수면·경로와 조작 아이콘의 표시를 확인했다. 격리된 데모에서 수유용품 체크(1/5)와 ‘돌봄 후 출발’ 계획을 저장해 Filled 전환·선택 상태 및 창 크기/테마 변경 후 유지도 확인했다. 앱에서 새 충돌이 발생하지 않았다.
- 실제 토큰으로 주야간·기본·누름·선택·비활성·체크의 32개 색 조합을 합성해 계산했다. 아이콘의 불투명한 전경과 배경 대비 최솟값은 4.998:1이다. 48dp 아이콘, 기존 라벨과 터치 영역을 유지한다.
- 데모를 끝내고 실제 첫 사용 화면, 원래 주간 설정과 가로 창 크기를 복원했다. 실제 사용자 10의 `care-record.json`·`departure.json`은 시작 전후 모두 없음을 확인했으며 데모 기록은 실제 저장소에 쓰지 않았다.
- 빌드 입력 묶음 SHA-256: `ec5ed8adb0d14d27ad2a2a72c619cdf22c45147e22f04d8b1a294eda7ac027e1`.
- 빌드 APK와 설치된 APK의 SHA-256: `3fdd07b4facf50a55b5969ac391fe9a89dd5687ba1ba14d88610788b09857b71`. APK 안의 새 아이콘 벡터 28개도 확인했다.

APK·빌드 로그·원본 PNG/XML 화면·설정 복원 결과는 `.dev/verification/icon-integration/2026-11-13/`에 보관한다. 검증 때문에 시작한 GCP VM은 자료 회수 후 중지했으며 `TERMINATED`를 확인했다. 커밋·푸시는 하지 않았다.
