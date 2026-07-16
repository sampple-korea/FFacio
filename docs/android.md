# FFacio Android Runtime 구현

## 책임 분리

FFacio는 화면, 카메라 수명주기, 사용자 등록·인증 정책, Head Admin, 암호화 저장소와 문 릴레이를 담당합니다. 얼굴 엔진과 네이티브 모델은 FFacio Runtime이 담당합니다. 앱은 Runtime 템플릿을 해석하거나 자체 벡터로 변환하지 않습니다.

`android/runtime-client`에는 AIDL, Parcelable, `FFacioRuntimeClient`, 호환 API `FaceSDK`가 들어 있습니다. Runtime IPC 계약이 바뀌면 이 모듈도 같은 버전으로 갱신해야 합니다.

## 카메라와 Runtime 분석

CameraX `YUV_420_888` 프레임을 row stride, pixel stride와 crop을 반영한 NV21로 변환한 뒤 Runtime Demo와 같은 orientation 매핑으로 `yuv2Bitmap()`을 호출합니다. 분석 호출 순서는 다음과 같습니다.

1. `FaceSDK.yuv2Bitmap`
2. `FaceSDK.faceDetection`
3. 검출된 얼굴 중 사각형 면적이 가장 큰 얼굴 하나 선택
4. Runtime Demo 기준의 품질·라이브니스 또는 등록 품질 검사
5. `FaceSDK.templateExtraction`
6. 등록 중복 또는 1:N 인증에서 `FaceSDK.similarityCalculation`

분석 간격은 180ms입니다. 이전 분석이나 등록·인증 비교가 끝나기 전에 새 작업을 쌓지 않습니다. 템플릿 비교는 I/O 작업에서 한 번에 하나만 실행하며, 8초 제한과 판정 토큰으로 화면·모드가 바뀐 뒤 도착한 결과를 버립니다. 동기식 Binder가 10초 이상 반환하지 않으면 Runtime 연결을 다시 초기화합니다.

## Runtime 요청 옵션

| 옵션 | 인증 | 등록 |
|---|---:|---:|
| `check_liveness` | 관리자 설정 | 관리자 설정 |
| `check_liveness_level` | 0 또는 1 | 0 또는 1 |
| `check_eye_closeness` | 켜짐 | 켜짐 |
| `check_face_occlusion` | 관리자 설정 | 관리자 설정 |
| `check_mouth_opened` | 켜짐 | 켜짐 |
| `estimate_age_gender` | 꺼짐 | 꺼짐 |

이는 Runtime Demo의 인증·등록 요청 의미에 맞춘 것입니다. 얼굴 가림 검사를 끄면 표시만 숨기는 것이 아니라 Runtime 요청과 앱 통과 조건에서 모두 제외합니다.

## 기본 판정값

| 항목 | 기본값 |
|---|---:|
| 라이브니스 | 0.70 이상 |
| 식별 점수 | 0.80 이상 |
| 1위·2위 점수 차이 | 0.03 이상 |
| 얼굴 품질 | 0.50 이상 |
| 밝기 | 0~255 |
| yaw / pitch / roll | 절댓값 10도 이하 |
| 왼쪽·오른쪽 눈 감김 | 각각 0.80 이하 |
| 가림 | 사용 시 0.50 이하 |
| 입 벌림 | 0.50 이하 |
| 등록 얼굴 크기 | 80~1200px |
| 랜드마크 | 68쌍, 배열 길이 136 |
| 등록 안정화 | 1200ms |
| 인증 안정화 | 1프레임 |

인증에서는 얼굴 사각형 면적이 이미지의 3%보다 작으면 가까이 오도록 안내합니다. 이 값은 Runtime Demo 인증 구현과 같습니다.

## 등록

등록은 단일 정면 Runtime 템플릿 방식입니다. 다음 조건을 연속으로 만족하는 동안 1200ms 안정화 시간을 측정하고, 그 구간에서 얼굴 품질이 가장 높은 템플릿 하나를 저장합니다.

- 가장 큰 얼굴의 좌표와 68점 랜드마크가 유효함
- 얼굴 중심이 Demo 등록 가이드 영역 안에 있음
- 크기 80~1200px
- 품질·밝기·yaw/pitch/roll 기준 통과
- 양쪽 눈이 열려 있음
- 설정한 경우 얼굴 가림 기준 통과
- 입 벌림 기준 통과
- 설정한 경우 Runtime 라이브니스 기준 통과

고개 돌리기, 다각도 샘플, 자세별 유지, 내부 샘플 간 유사도 검사는 없습니다. 저장 직전에는 새 대표 템플릿과 기존 사용자 대표 템플릿을 비교해 가장 유사한 기존 사용자를 경고로 표시하되, Runtime Demo처럼 별도 등록은 허용합니다.

## 인증

각 사용자당 대표 Runtime 템플릿 하나만 비교합니다. 최고 점수가 0.80 이상이고 2위와의 차이가 0.03 이상이면 승인 후보입니다. Runtime 라이브니스가 켜져 있으면 0.70 이상이어야 하며, 품질 0.50과 최소 얼굴 면적 조건도 먼저 통과해야 합니다.

사용자 요구에 따라 안정화 프레임은 1입니다. 별도의 동작 챌린지, 보조 샘플 지지 수, 반복 후보 누적은 사용하지 않습니다.

## 저장 형식과 이전 데이터

현재 레코드는 스키마 5로 `schema_version`, `name`, `engine_id`, `template_size`, `template_b64`, `head_admin`을 암호화 저장소에 기록합니다.

이 버전을 처음 실행하면 저장 정책 버전이 5가 아닌 모든 과거 사용자 레코드와 암호화 사용자 템플릿을 삭제합니다. 기존 자체 엔진 데이터, 과거 Runtime 대표 템플릿, `samples_b64` 보조 샘플을 모두 복구하거나 변환하지 않습니다. 릴레이 URL·토큰은 유지되지만 얼굴 인식 설정은 Runtime Demo 기본값(라이브니스 켬·레벨 0·가림 검사 끔)으로 초기화되며, 모든 사용자를 새 방식으로 다시 등록해야 합니다.

## Runtime 진단과 보안

관리자 화면은 Runtime 패키지 버전, Binder 상태, 초기화 결과, 마지막 끊김 사유, 자동 재연결 횟수, 최근 YUV 변환·검출·템플릿 추출 시간을 표시합니다. 진단 텍스트에는 얼굴 이미지나 템플릿이 포함되지 않습니다.

Runtime 서비스는 `io.ffacio.sdk.permission.BIND_RUNTIME` signature 권한을 사용하므로 FFacio와 Runtime APK를 같은 인증서로 서명해야 합니다. 앱은 평문 HTTP를 차단하고, 릴레이는 파싱 가능한 HTTPS URL과 토큰이 모두 있을 때만 활성화합니다.

## 검증

```bash
python3 scripts/verify_source_static.py
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

단위 테스트는 가장 큰 얼굴 선택, 단일 템플릿 안정화, 잘못된 프레임에서 안정화 초기화, 1프레임 인증, 대표 템플릿 단독 비교, 점수·2위 모호성, Runtime 비교 실패 계수를 검사합니다. 실제 카메라·라이브니스·네이티브 결과는 ARM Android 기기에서 `scripts/verify_android_device.ps1`로 확인합니다.
