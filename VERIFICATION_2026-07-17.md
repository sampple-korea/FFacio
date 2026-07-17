# 검증 기록 — 2026-07-17

## 검증 대상

- 앱 버전: `0.7.1-runtime-demo-parity-ios`
- 저장 정책·레코드 스키마: 7
- 입력 기준: 첨부된 `FFacio-Runtime-main-3.zip`의 Runtime Demo
- 수정 대상: 첨부된 `FFacio-master-3.zip`

## 통과한 검사

### 1. 소스 fail-closed 감사

`python3 scripts/verify_source_static.py` 통과.

검사 항목:

- 전체 XML 파싱
- Kotlin·Java·Gradle 괄호, 문자열, 주석 구조
- Runtime 필수 호출과 Binder 모듈 포함 여부
- Fotoapparat AAR 존재와 고정 SHA-256
- CameraX·수동 NV21 변환 경로 부재
- 저장 정책·스키마 7과 이전 데이터 초기화 경로
- 가장 큰 얼굴 선택, 1200ms 등록, 1프레임 인증
- 고개 챌린지·5장·다각도·보조 샘플 로직 표식 부재
- 자체 ONNX/OpenCV 엔진, 모델 파일, 서명키, 알려진 비밀키 패턴 부재
- 등록 프레임·템플릿 소유권과 정리 경로

### 2. Kotlin 구문·핵심 코드 컴파일 검사

최종 `MainActivity.kt`와 `RuntimeDemoPolicy.kt`를 Kotlin 컴파일러의 파서에 입력해 `expecting`, `unexpected tokens`, 미종결 문자열·주석 같은 구문 진단이 0건인지 확인했습니다. Android/Compose 의존성이 없는 환경이므로 전체 파일은 미해결 참조에서 종료되며, 그 결과를 전체 앱 컴파일 성공으로 해석하지 않습니다.

핵심 정책·등록 안정화·프레임 소유권 코드는 실제 Runtime `FaceBox.java`, `FaceDetectionParam.java`와 함께 Kotlin/JVM 17로 별도 컴파일·실행했습니다.

```text
KOTLIN_SYNTAX_DIAGNOSTICS=0
RUNTIME_DEMO_POLICY_CHECKS_PASSED=39
```

### 3. Runtime Demo 카메라 동일성

- Demo와 앱의 `fotoapparat-2.7.0.aar` SHA-256 일치
- 해시: `a9ce65824a2ff6ee05450c1b28d11b0bb668e5345e0c60303b184f3cd8fbbdff`
- `frame.image`, width, height, rotation을 보존하는 NV21 경로 확인
- 전면·후면 0/90/180/270도 orientation 8개 일치
- `CenterCrop`과 전면 카메라 우선 설정 확인
- CameraX 의존성과 `ImageProxy`, `ProcessCameraProvider`, `PreviewView`, 수동 NV21 변환 함수 부재 확인

### 4. Runtime Demo 정책 실행 검사

실제 `RuntimeDemoPolicy.kt`와 최종 `MainActivity.kt`에서 추출한 등록 프레임 소유권·안정화 클래스를 Kotlin/JVM 17로 컴파일했습니다. 실제 Runtime 클라이언트의 `FaceBox.java`, `FaceDetectionParam.java`를 함께 사용했습니다.

실행 결과:

```text
RUNTIME_DEMO_POLICY_CHECKS_PASSED=39
```

포함 항목:

- 분석 간격 180ms, 등록 1200ms, 인증 1프레임
- orientation 8개
- 라이브니스·눈·선택적 가림·입·나이/성별 옵션
- Demo 임계값 경계
- 중앙 정사각형 ROI와 이동 방향 안내
- yaw·pitch·눈·입·라이브니스 실패 안내
- 인증에서 등록 전용 눈·입·자세 조건 제외
- 인증 최소 얼굴 면적 3%
- 가장 큰 얼굴 선택
- 정확한 해상도 미지원 시 종횡비 우선 fallback과 뒤집힌 동면적 스트림 배제
- 최고 품질 원본 프레임 소유권 분리
- 무효 프레임 안정화 초기화
- 캡처·최종 템플릿 메모리 정리

### 5. Runtime IPC 계약 대조

앱 `runtime-client`와 첨부 Runtime 프로젝트 `client`의 다음 파일을 바이트 단위로 대조합니다.

- AIDL 3개
- `FFacioFaceParcel.java`
- `FFacioOptionsParcel.java`
- `FaceBox.java`
- `FaceDetectionParam.java`

차이가 있으면 최종 패키징 검증이 실패하도록 별도 대조 스크립트에서 검사합니다.

### 6. 회귀 테스트 인벤토리

JUnit 테스트 소스에 `@Test` 89개가 있으며 정적 감사가 89개 미만으로 줄어들면 실패합니다. 새 테스트에는 Demo orientation·등록 임계값·인증 조건 분리·정사각형 ROI·최적 원본 프레임 소유권과 메모리 정리가 포함됩니다.

이 환경에서는 Gradle/JUnit 의존성을 모두 확보하지 못했으므로 89개 전체를 Gradle로 실행했다고 주장하지 않습니다. 대신 핵심 실제 정책 코드는 위 39개 독립 실행 검사를 통과했습니다.

## 수정 중 발견해 해결한 차이

- CameraX 수동 NV21 재조립과 Demo Fotoapparat NV21 입력 차이
- 전면 회전·미러링 처리 경로 차이
- Demo의 중앙 ROI는 세로로 긴 영역이 아니라 oriented frame 폭 2/3의 정사각형이라는 차이
- FFacio가 매 유효 프레임마다 템플릿을 추출하던 차이
- Demo는 안정화 구간의 최고 품질 원본 프레임을 최종 재검사한 뒤 한 번만 추출한다는 차이
- 일부 기존 사용자 비교 실패가 전체 등록을 실패시키던 문제
- 유사 얼굴을 강제 차단하던 문제
- 저장 실패·취소·화면 종료 시 프레임 또는 템플릿 소유권 정리 문제
- 손상된 사용자 한 명이 전체 저장소 로딩에 영향을 줄 수 있던 문제

## 환경상 제외한 검사

`android/gradlew`는 Gradle 8.13 배포본을 `services.gradle.org`에서 받아야 하지만 현재 실행 환경의 DNS/다운로드 경로로 배포본을 확보하지 못했습니다. 따라서 다음 항목은 완료됐다고 표시하지 않습니다.

- 전체 `testDebugUnitTest`
- `lintDebug`
- `assembleDebug`
- 실제 ARM 기기 카메라·Runtime 통합 실행

최종 ZIP에는 빌드 성공을 암시하는 APK나 조작된 로그를 포함하지 않습니다. 실제 배포 전에는 같은 인증서로 서명한 Runtime APK와 FFacio APK를 ARM Android 기기에 설치해 등록·인증·추적을 확인해야 합니다.
