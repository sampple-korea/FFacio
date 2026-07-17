> **0.9.0 안내:** 현재 문 제어는 SmartThings가 아니라 별도 ITSOKEY Runtime Binder로 이전되었습니다. 이 문서의 SmartThings 내용은 이전 버전 역사 기록입니다. 현재 구현은 `../ITSOKEY_INTEGRATION.md`를 참고하세요.

# FFacio Android Runtime 구현

## 책임 분리

FFacio는 Compose UI, 카메라 수명주기, 사용자 등록·인증 정책, Head Admin, 암호화 저장소와 SmartThings 도어락을 담당합니다. 얼굴 엔진과 네이티브 모델은 별도 FFacio Runtime이 담당합니다. FFacio는 Runtime 템플릿을 해석하거나 자체 벡터로 변환하지 않습니다.

`android/runtime-client`에는 AIDL, Parcelable, `FFacioRuntimeClient`, 호환 API `FaceSDK`가 들어 있습니다. Runtime IPC 계약이 바뀌면 이 모듈도 같은 계약으로 갱신해야 합니다.

## 카메라 입력

FFacio는 Runtime Demo와 동일한 `fotoapparat-2.7.0.aar`를 사용합니다. Demo AAR과 FFacio AAR의 SHA-256은 다음과 같습니다.

```text
a9ce65824a2ff6ee05450c1b28d11b0bb668e5345e0c60303b184f3cd8fbbdff
```

분석 경로는 다음과 같습니다.

1. Fotoapparat가 전달한 tightly packed NV21 `frame.image`를 복사해 작업 스레드로 넘깁니다.
2. `frame.size.width`, `frame.size.height`, `frame.rotation`을 그대로 보존합니다.
3. Runtime Demo와 같은 EXIF orientation 매핑으로 `FaceSDK.yuv2Bitmap()`을 호출합니다.
4. `FaceSDK.faceDetection()`을 Demo 옵션으로 호출합니다.
5. 여러 얼굴 중 면적이 가장 큰 얼굴 하나만 선택합니다.
6. 등록 또는 인증 정책을 통과한 경우에만 후속 템플릿 작업을 합니다.

이전 CameraX `YUV_420_888` plane·row stride·pixel stride·crop을 수동으로 NV21로 재조립하던 경로는 제거했습니다. 전면 카메라에서 잘못된 크로마 배열이나 회전·미러링 불일치가 눈·자세·랜드마크 값에 영향을 주지 않도록 Demo 입력 흐름 자체를 이식한 것입니다.

등록 카메라는 Demo `CaptureActivityKt`와 같은 1280×720 요청을 우선합니다. 인증은 Demo 균형 프리셋과 같은 640×480을 우선합니다. 정확한 해상도가 없으면 픽셀 수가 가장 가까운 해상도를 선택합니다. 분석 간격은 180ms이며 한 번에 한 분석만 실행합니다.

## orientation 매핑

| 센서 회전 | 전면 | 후면 |
|---:|---:|---:|
| 0° | 2 | 1 |
| 90° | 7 | 6 |
| 180° | 4 | 3 |
| 270° | 5 | 8 |

전면 orientation에 미러링이 포함되므로 분석 좌표와 Fotoapparat 전면 미리보기는 같은 시각 방향을 사용합니다. 얼굴 추적 링은 oriented bitmap 좌표를 `CenterCrop` 규칙으로 화면에 매핑합니다.

## Runtime 요청 옵션

```text
check_liveness       = 관리자 설정, 기본 true
check_liveness_level = 0 또는 1, 기본 0
check_eye_closeness  = true
check_face_occlusion = 관리자 설정, 기본 false
check_mouth_opened   = true
estimate_age_gender  = false
```

등록과 인증 모두 Demo처럼 눈·입 속성을 요청합니다. 다만 인증 통과 조건에서 눈·입은 사용하지 않고, 자세는 명백히 심한 회전만 차단합니다.

## 등록 정책

등록 조건과 순서는 Demo `RegistrationPipeline.inspect()` 및 `CaptureView.getROIRect1()`에 맞춥니다.

1. 유효한 얼굴 좌표
2. 68쌍, 배열 길이 136의 랜드마크
3. 얼굴 크기 80px 이상
4. 얼굴 크기 1200px 이하
5. oriented frame 폭의 2/3인 중앙 정사각형 ROI 안에 얼굴 중심 위치
6. 품질 0.50 이상
7. 밝기 0~255
8. yaw 35도, pitch·roll 30도를 넘는 명백한 과도 회전 차단
9. 가림 검사를 켠 경우 0.50 이하
10. 라이브니스를 켠 경우 0.70 이상

눈 감김과 입 벌림 값은 Runtime에 요청하지만 오검출로 등록을 막지 않도록 진단용으로만 사용합니다.

모든 조건이 연속 1200ms 유지되는 동안 품질이 가장 좋은 원본 NV21 프레임의 독립 복사본을 보관합니다. 완료 시 Demo와 같이 그 프레임을 다시 `yuv2Bitmap → faceDetection → 등록 조건 검사 → templateExtraction` 순서로 처리합니다. 최종 재검사에서 조건이 달라졌으면 저장하지 않고 안내를 다시 표시합니다.

최종 템플릿은 기존 호환 사용자 전원과 비교합니다. 각 비교는 일시적 Binder 오류를 한 번 재시도하고, 그래도 한 건이라도 실패하면 등록을 중단합니다. 비교가 모두 끝난 뒤 최고 유사도가 0.80 이상이면 중복 얼굴로 판단해 저장하지 않습니다.

고개 돌리기 챌린지, 5장 다각도 등록, 자세별 유지, 샘플 간 유사도·응집도 검사, 대표 샘플 선정, 보조 샘플 지지 판정은 없습니다.

## 인증 정책

Demo `CameraActivityKt`의 인증 선행 조건을 따릅니다.

- 라이브니스가 켜진 경우 0.70 이상
- 얼굴 품질 0.50 이상
- 얼굴 면적이 oriented bitmap 전체 면적의 3% 이상
- yaw 35도, pitch·roll 30도를 넘는 심한 자세 차단

그 뒤 사용자당 대표 Runtime 템플릿 하나와 비교합니다. 모든 호환 사용자와의 비교가 성공해야 다음 단계로 넘어갑니다. 최고 점수가 0.80 이상이고 2위와의 차이가 0.03 이상이면 승인합니다. 사용자 요구에 따라 안정화는 1프레임입니다. 눈 감김과 입 벌림은 인증 차단 조건이 아닙니다. 자세는 명백히 심하게 돌아간 경우만 차단합니다.

## 프레임·템플릿 수명주기

- Fotoapparat 프레임은 분석 큐에 넘길 때 복사하고 작업 완료 후 0으로 덮습니다.
- 등록 안정화 추적기는 품질이 더 좋은 프레임만 독립 소유하며 교체·취소·실패 때 이전 배열을 0으로 덮습니다.
- 최종 등록 작업은 저장소 비교용 사용자 템플릿도 독립 복사해 사용합니다.
- 템플릿 저장 성공 전까지 새 템플릿의 소유권을 명시적으로 관리하며 실패·오래된 결과·화면 종료 때 정리합니다.
- Runtime 임시 캐시 파일은 삭제 전 매 프레임 `fsync`하지 않고 앱 전용 캐시에서 즉시 제거합니다.

## 저장 형식과 이전 데이터

현재 저장 정책과 레코드 스키마는 8입니다. 레코드는 `schema_version`, `name`, `engine_id`, `template_size`, `template_b64`, `head_admin`을 암호화 저장소에 기록합니다.

정책 버전 8이 아닌 과거 얼굴 사용자 데이터와 판정 설정은 첫 실행 시 삭제하고 다음 Demo 기본값으로 초기화합니다.

```text
라이브니스 true
라이브니스 레벨 0
가림 검사 false
```

기존 임의 릴레이 설정은 폐기되고 SmartThings 설정은 새 형식으로 입력해야 합니다.

## UI

카메라는 둥근 대형 카드, Face ID 스타일 링, 실제 검출 얼굴 경계 사각형으로 표시합니다. 링과 사각형은 가장 큰 얼굴을 `CenterCrop` 좌표로 실시간 추적합니다. 사용자 안내는 카메라 아래 상태 패널 한 곳에만 표시하며, 카메라가 켜진 동안 내부에는 추적 그래픽과 관리자 인증 취소 조작만 둡니다. 내부 Runtime 수치나 후보 이름은 운영 화면에 노출하지 않습니다.

## Runtime 진단과 보안

관리자 화면은 Runtime 패키지 버전, Binder 상태, 초기화 결과, 마지막 끊김 사유, 재연결 횟수와 최근 YUV 변환·검출·템플릿 시간을 표시합니다. 진단 텍스트에는 얼굴 이미지나 템플릿 원문을 포함하지 않습니다.

Runtime 서비스는 `io.ffacio.sdk.permission.BIND_RUNTIME` signature 권한을 사용하므로 FFacio와 Runtime APK를 같은 인증서로 서명해야 합니다. 앱은 임의 URL을 호출하지 않고 SmartThings API host의 기기 상태·명령 경로만 사용합니다. Runtime의 유사도·라이브니스·품질·가림 결과는 0.0~1.0 범위를 벗어나면 오류로 보고 차단합니다. 문 열림은 Runtime 라이브니스, 전체 사용자 비교 완료, 유사도·후보 격차와 단일 실행 잠금을 모두 통과해야 합니다. Runtime 라이브니스가 꺼져 있으면 Head Admin 얼굴 인증 대신 Android 화면 잠금으로 관리자 작업을 승인합니다.

## 검증 명령

```bash
python3 scripts/verify_source_static.py
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

실제 카메라·라이브니스·Runtime 네이티브 결과는 같은 서명으로 설치한 ARM Android 기기에서 확인해야 합니다.
