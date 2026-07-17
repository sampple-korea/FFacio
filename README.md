# FFacio Android 0.9.1 — FFacio Runtime + ITSOKEY Runtime

FFacio는 카메라 기반 출입 인증, 사용자·Head Admin 관리와 ITSOKEY 도어락 연동을 담당하는 Android 앱입니다.

- 얼굴 검출·속성 분석·템플릿 추출·비교: 별도 **FFacio Runtime** (`com.kbyai.faceattribute`)
- 카카오 로그인·ITSOKEY 세션·기기 조회·문 제어: 별도 **ITSOKEY Runtime** (`io.ffacio.itsokeyruntime`)

FFacio 본체에는 얼굴 모델과 ITSOKEY access/refresh token을 넣지 않습니다.

## 이번 수정의 핵심

- 기존 SmartThings REST 직접 연동을 ITSOKEY Runtime Binder 연동으로 교체
- FFacio 관리자 화면에서 ITSOKEY 공식 카카오 로그인 화면 실행
- 계정에 등록된 도어락 자동 조회·선택
- 선택한 기기 ID와 활성화 상태만 FFacio에 저장
- 토큰은 ITSOKEY Runtime 앱의 Android Keystore AES-GCM 저장소에만 보관
- 연결 확인은 문을 열지 않고 단일 기기 조회만 수행
- 시험 열기는 별도 ITSOKEY 설정 화면에서 사용자 확인 후에만 실행
- 얼굴 인증, 라이브니스, 전체 후보 비교, 설정 활성화 조건을 모두 통과한 경우에만 `OPEN` 호출

자세한 내용은 [ITSOKEY_INTEGRATION.md](ITSOKEY_INTEGRATION.md)를 참고하세요.

## 얼굴 등록 흐름

기존 고개 돌리기 챌린지와 다각도 샘플 수집은 사용하지 않습니다.

1. Runtime Demo와 같은 옵션으로 얼굴·속성을 검출합니다.
2. 여러 얼굴 중 사각형 면적이 가장 큰 얼굴 한 명만 선택합니다.
3. 좌표·68점 랜드마크·크기·중앙 가이드·품질·밝기·라이브니스와 선택적 가림을 검사합니다.
4. 조건을 연속 1200ms 만족하는 동안 가장 품질이 좋은 원본 NV21 프레임을 보관합니다.
5. 최종 등록 직전에 다시 검출·판정한 뒤 템플릿을 한 번만 추출합니다.
6. 기존 사용자 전원과 비교하고 최고 유사도 0.80 이상이면 중복 얼굴로 차단합니다.
7. 사용자당 Runtime 템플릿 하나만 암호화 저장합니다.

## 얼굴 인증 흐름

- 라이브니스 0.70 이상
- 얼굴 품질 0.50 이상
- 얼굴 면적이 분석 이미지의 3% 이상
- yaw 35도, pitch·roll 30도를 넘는 과도 회전 차단
- 모든 호환 사용자 템플릿 비교 완료
- 최고 유사도 0.80 이상
- 1위와 2위 후보 점수 차이 0.03 이상
- 사용자 요구에 따라 인증 안정화는 1프레임

라이브니스를 끄면 ITSOKEY 자동 문 열기와 Head Admin 얼굴 승인이 함께 비활성화됩니다.

## 도어락 실행 흐름

```text
FFacio 얼굴 인증 성공
  → Runtime 라이브니스 확인
  → ITSOKEY 자동 열기 활성화 확인
  → 선택한 deviceIdx 검증
  → signature 권한 Binder 호출
  → ITSOKEY Runtime의 openDeviceJson()
  → POST /api/widget/device/{deviceIdx}/control.do, type=OPEN
  → 응답 결과를 인증 결과와 별도로 표시
```

## 설치 순서와 서명

1. FFacio Face Runtime 설치
2. ITSOKEY Runtime 설치
3. FFacio 설치

FFacio Face Runtime의 기존 `signature` 권한 조건과 ITSOKEY Runtime의 새 `signature` 권한 조건 때문에 관련 APK를 **호환되는 동일 인증서 구성**으로 빌드해야 합니다. 특히 FFacio와 ITSOKEY Runtime은 반드시 같은 인증서로 서명하세요. 같은 PC에서 두 프로젝트를 debug 빌드하면 보통 동일 debug keystore가 사용됩니다.

## 빌드

요구 환경: JDK 17, Android SDK 36, Gradle과 Google Maven 의존성을 받을 수 있는 네트워크.

```bash
python3 scripts/verify_itsokey_integration.py
cd android
./gradlew clean testDebugUnitTest assembleDebug
```

APK 경로:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## 검증 범위

- 새 ITSOKEY Java/AIDL 코드는 Android 16 API 스텁으로 정적 컴파일했습니다.
- FFacio ITSOKEY 설정 Activity와 client 모듈도 같은 방식으로 컴파일했습니다.
- AIDL 3개 사본의 SHA-256이 일치합니다.
- 프로토콜 입력 검증 하네스 10개 검사를 통과했습니다.
- `MainActivity.kt`는 Kotlin 파서 단계에서 구문 오류가 없음을 확인했습니다.

현재 실행 환경에는 완성된 Android SDK/Gradle 배포본, 실제 카카오 계정과 HG-1300이 없어 전체 APK 빌드·설치·실문 OPEN은 실행하지 못했습니다. 자세한 기록은 [ITSOKEY_VERIFY.md](ITSOKEY_VERIFY.md)에 있습니다.

## 문서

- [ITSOKEY_INTEGRATION.md](ITSOKEY_INTEGRATION.md)
- [ITSOKEY_VERIFY.md](ITSOKEY_VERIFY.md)
- [FFACIO_RUNTIME_REFACTOR_REPORT.md](FFACIO_RUNTIME_REFACTOR_REPORT.md)
- [docs/android.md](docs/android.md)
- [docs/runtime-migration.md](docs/runtime-migration.md)

`docs/smartthings-door-lock.md`와 과거 release 기록은 이전 버전의 역사 자료이며 현재 0.9.1의 문 제어 구현에는 적용되지 않습니다.
