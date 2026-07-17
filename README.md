# FFacio Android 0.8.2 — 보안 강화·SmartThings·실시간 추적

FFacio는 카메라 기반 출입 인증, 사용자·Head Admin 관리와 SmartThings 도어락 연동을 담당하는 Android 앱입니다. 얼굴 검출, 속성 분석, NV21 변환, 템플릿 추출과 비교는 별도 설치되는 **FFacio Runtime**(`com.kbyai.faceattribute`)을 Binder/AIDL로 호출합니다.

## 이번 수정의 핵심

이 버전은 FFacio의 얼굴 처리 흐름을 Runtime Demo와 입력 단계부터 다시 맞췄습니다.

- CameraX `YUV_420_888`을 직접 NV21로 재조립하던 경로를 제거했습니다.
- Runtime Demo와 동일한 `fotoapparat-2.7.0.aar`를 포함하고, Fotoapparat가 전달하는 NV21 원본 프레임·회전값을 그대로 사용합니다.
- 전면 카메라의 EXIF 회전·미러링 코드는 Runtime Demo `CameraCoordinateMapper.nativeOrientation()`과 동일합니다.
- 등록은 Demo의 1280×720 캡처 요청, 인증은 기본 640×480 균형 설정을 사용합니다. 기기가 정확한 해상도를 지원하지 않으면 화면 방향과 종횡비를 우선하고 면적 차이를 보조 기준으로 가장 가까운 해상도를 선택합니다.
- 여러 얼굴이 보일 때에는 사용자 요구에 따라 사각형 면적이 가장 큰 얼굴 한 명만 등록·인증·추적합니다.
- 인증 안정화는 사용자 요구에 따라 1프레임입니다. 그 외 등록·인증 기준과 Runtime 옵션은 Demo 기본값을 따릅니다.

## 등록 흐름

등록은 기존 FFacio의 고개 돌리기 챌린지나 다각도 샘플 수집을 사용하지 않습니다.

1. Demo와 같은 옵션으로 얼굴·속성을 한 번에 검출합니다.
2. 가장 큰 얼굴 하나를 선택합니다.
3. 좌표, 68점 랜드마크, 크기, 중앙 가이드, 품질, 밝기, 라이브니스와 선택적 가림을 검사합니다. 눈·입 속성은 진단용으로만 요청하며, 고개는 명백히 심하게 돌아가거나 숙여진 경우에만 차단합니다.
4. 모든 조건을 연속 1200ms 만족하는 동안 가장 품질이 좋은 **원본 NV21 프레임**을 보관합니다.
5. 최종 등록 직전에 그 프레임을 다시 변환·검출·판정한 뒤 템플릿을 한 번만 추출합니다.
6. 기존 사용자 전원과 Runtime 템플릿을 비교합니다. 한 건이라도 비교가 끝나지 않으면 저장을 중단하고, 최고 유사도가 0.80 이상이면 중복 얼굴로 차단합니다.
7. 사용자당 Runtime 템플릿 하나만 암호화 저장합니다.

고개 돌리기 챌린지, 5장 등록, 자세별 유지, 등록 샘플 간 유사도·응집도 검사, 보조 샘플 지지 판정은 코드 경로와 저장 형식에서 제거했습니다.

## 인증 흐름

인증은 Runtime Demo의 카메라·템플릿 흐름을 유지하면서 출입 단말 보안에 필요한 최소 차단 조건을 추가합니다.

- 라이브니스가 켜진 경우 0.70 이상
- 얼굴 품질 0.50 이상
- 얼굴 면적이 분석 이미지의 3% 이상
- yaw 35도, pitch·roll 30도를 넘는 명백한 과도 회전 차단
- 모든 호환 사용자 템플릿 비교 완료
- 최고 유사도 0.80 이상
- 1위와 2위 후보 점수 차이 0.03 이상

눈·입 속성은 Runtime 요청에는 포함하지만 인증을 막지 않습니다. 자세는 잦은 오검출을 피하기 위해 심한 회전만 막습니다. 별도의 능동 동작 챌린지나 여러 프레임 후보 누적도 없습니다.

## Runtime Demo 기본값

| 항목 | 값 |
|---|---:|
| 전면 카메라 | 기본 사용 |
| 라이브니스 | 켜짐 |
| 라이브니스 레벨 | 0 |
| 라이브니스 임계값 | 0.70 |
| 식별 임계값 | 0.80 |
| 1위·2위 불확실 구간 | 0.03 |
| 품질 임계값 | 0.50 |
| 밝기 | 0~255 |
| 심한 자세 차단 | yaw 35도, pitch·roll 30도 초과 |
| 양쪽 눈 감김 | 진단용, 차단하지 않음 |
| 얼굴 가림 | 기본 꺼짐, 켤 경우 0.50 이하 |
| 입 벌림 | 진단용, 차단하지 않음 |
| 등록 얼굴 크기 | 80~1200px |
| 등록 안정화 | 1200ms |
| 분석 간격 | 180ms |
| 인증 결과 유지 | 3500ms |
| 인증 안정화 | **1프레임** |

## UI

- 카메라를 둥근 대형 카드로 구성하고 Face ID 스타일 링과 실제 얼굴 경계 사각형을 가장 큰 얼굴 위치에 맞춰 실시간 이동시킵니다.
- 안내 문구는 카메라 아래 상태 패널 한 곳에만 표시합니다. 카메라가 켜져 있을 때 내부에는 얼굴 추적 그래픽과 관리자 인증 취소 조작 외의 상태 문구를 표시하지 않습니다.
- 승인·거절·검색 상태는 iOS 계열 파랑·초록·빨강 상태색과 캡슐 배지로 구분합니다.
- 운영 화면과 관리자 화면의 상태 카드, 버튼, 사용자 목록을 큰 모서리·넉넉한 여백 중심으로 정리했습니다.

## 보안 보강

- 동일 얼굴은 기존 사용자 전원과 비교해 유사도 0.80 이상이면 등록하지 않습니다. 비교 실패·템플릿 크기 불일치·범위 밖 점수가 한 건이라도 있으면 중복 검사가 불완전한 것으로 보고 저장을 중단합니다.
- 사용자 이름은 공백을 정규화하고 제어문자·과도한 길이·대소문자 무시 중복을 등록 시작과 최종 저장 양쪽에서 차단합니다.
- Runtime 유사도·라이브니스·품질·가림 점수는 0.0~1.0 범위만 허용합니다. `NaN`, 무한대, 음수, 1 초과 값은 엔진 오류로 보고 fail-closed 처리합니다.
- SmartThings 문 열림은 Runtime 라이브니스, 현재 프레임의 실제 얼굴 판정, 전체 사용자 비교 완료, 유사도·후보 격차, 활성화 상태를 모두 통과해야 합니다. 라이브니스를 끄면 문 열림과 Head Admin 얼굴 승인은 자동으로 비활성화됩니다.
- SmartThings API host는 `https://api.smartthings.com/v1`로 고정하고 redirect를 따르지 않습니다. Device ID와 token 입력 길이·문자 범위를 검사하고 응답은 64KiB로 제한합니다.
- token과 얼굴 템플릿은 Android Keystore AES-GCM으로 암호화하며, 저장 키 이름을 추가 인증 데이터(AAD)로 묶어 암호문을 다른 항목으로 바꿔치기할 수 없게 했습니다.
- SmartThings 활성화 저장이 중간에 실패하면 Device ID·암호화 token·활성화 플래그를 함께 롤백합니다.

## 데이터와 호환성

저장 정책과 사용자 레코드 스키마는 8입니다. 이 버전을 처음 실행하면 이전 얼굴 등록 데이터와 이전 얼굴 판정 설정을 폐기하고 Demo 기본값으로 초기화합니다. 기존 임의 릴레이 설정은 폐기되며 SmartThings Device ID와 access token을 새로 설정해야 합니다. 모든 사용자는 새 카메라·등록 흐름으로 다시 등록해야 합니다.

FFacio Runtime 서비스는 `signature` 권한을 사용하므로 **FFacio APK와 Runtime APK를 같은 인증서로 서명해야 합니다.**

## 구조

```text
FFacio Compose UI / 사용자·관리자·SmartThings 정책
        ↓
Fotoapparat NV21 카메라 파이프라인
        ↓
runtime-client (FaceSDK 호환 API + AIDL Binder client)
        ↓
FFacio Runtime APK / FFacioRuntimeService
```

FFacio APK에는 자체 OpenCV·ONNX·ArcFace·YuNet·SFace·MiniFASNet 엔진이나 얼굴 모델 파일이 포함되지 않습니다.

## 빌드와 검증

```bash
python3 scripts/verify_source_static.py
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

현재 검증 결과와 환경상 제외 항목은 [VERIFICATION_2026-07-17.md](VERIFICATION_2026-07-17.md)에 기록했습니다. SmartThings 형식은 [docs/smartthings-door-lock.md](docs/smartthings-door-lock.md)를 참고하세요. 구현 세부 내용은 [docs/android.md](docs/android.md)와 [FFACIO_RUNTIME_REFACTOR_REPORT.md](FFACIO_RUNTIME_REFACTOR_REPORT.md)를 참고하세요.
