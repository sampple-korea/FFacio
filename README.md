# FFacio Android 0.7.1 — Runtime Demo 완전 정렬·iOS 스타일 UI

FFacio는 카메라 기반 출입 인증, 사용자·Head Admin 관리와 HTTPS 문 릴레이를 담당하는 Android 앱입니다. 얼굴 검출, 속성 분석, NV21 변환, 템플릿 추출과 비교는 별도 설치되는 **FFacio Runtime**(`com.kbyai.faceattribute`)을 Binder/AIDL로 호출합니다.

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
3. Demo 등록 순서대로 좌표, 68점 랜드마크, 크기, 정사각형 중앙 가이드, 품질, 밝기, yaw·pitch·roll, 양쪽 눈, 선택적 가림, 입, 라이브니스를 검사합니다.
4. 모든 조건을 연속 1200ms 만족하는 동안 가장 품질이 좋은 **원본 NV21 프레임**을 보관합니다.
5. 최종 등록 직전에 그 프레임을 다시 변환·검출·판정한 뒤 템플릿을 한 번만 추출합니다.
6. 기존 사용자와의 최고 유사도를 경고로 표시하되 Demo처럼 별도 등록을 허용합니다.
7. 사용자당 Runtime 템플릿 하나만 암호화 저장합니다.

고개 돌리기 챌린지, 5장 등록, 자세별 유지, 등록 샘플 간 유사도·응집도 검사, 보조 샘플 지지 판정은 코드 경로와 저장 형식에서 제거했습니다.

## 인증 흐름

인증은 Runtime Demo `CameraActivityKt`의 기본 판정과 같이 다음 항목만 선행 조건으로 사용합니다.

- 라이브니스가 켜진 경우 0.70 이상
- 얼굴 품질 0.50 이상
- 얼굴 면적이 분석 이미지의 3% 이상
- 최고 유사도 0.80 이상
- 1위와 2위 후보 점수 차이 0.03 이상

등록용 눈·입·자세 속성은 Runtime 요청에는 포함하지만 인증을 막는 조건으로 사용하지 않습니다. 별도의 능동 동작 챌린지나 여러 프레임 후보 누적도 없습니다.

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
| yaw / pitch / roll | 각각 절댓값 10도 이하 |
| 양쪽 눈 감김 | 각각 0.80 이하 |
| 얼굴 가림 | 기본 꺼짐, 켤 경우 0.50 이하 |
| 입 벌림 | 0.50 이하 |
| 등록 얼굴 크기 | 80~1200px |
| 등록 안정화 | 1200ms |
| 분석 간격 | 180ms |
| 인증 결과 유지 | 3500ms |
| 인증 안정화 | **1프레임** |

## UI

- 카메라를 둥근 대형 카드로 구성하고 Face ID 스타일 추적 링을 검출된 가장 큰 얼굴 위치와 크기에 맞춰 부드럽게 이동시킵니다.
- 등록 진행률은 링과 하단 유리 질감 상태 카드에 함께 표시합니다.
- 승인·거절·검색 상태는 iOS 계열 파랑·초록·빨강 상태색과 캡슐 배지로 구분합니다.
- 운영 화면과 관리자 화면의 상태 카드, 버튼, 사용자 목록을 큰 모서리·넉넉한 여백 중심으로 정리했습니다.

## 데이터와 호환성

저장 정책과 사용자 레코드 스키마는 7입니다. 이 버전을 처음 실행하면 이전 얼굴 등록 데이터와 이전 얼굴 판정 설정을 폐기하고 Demo 기본값으로 초기화합니다. 릴레이 URL·토큰 등 얼굴 외 설정은 유지됩니다. 모든 사용자는 새 카메라·등록 흐름으로 다시 등록해야 합니다.

FFacio Runtime 서비스는 `signature` 권한을 사용하므로 **FFacio APK와 Runtime APK를 같은 인증서로 서명해야 합니다.**

## 구조

```text
FFacio Compose UI / 사용자·관리자·릴레이 정책
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

현재 검증 결과와 환경상 제외 항목은 [VERIFICATION_2026-07-17.md](VERIFICATION_2026-07-17.md)에 기록했습니다. 구현 세부 내용은 [docs/android.md](docs/android.md)와 [FFACIO_RUNTIME_REFACTOR_REPORT.md](FFACIO_RUNTIME_REFACTOR_REPORT.md)를 참고하세요.
