# FFacio + ITSOKEY Runtime 연동

이 프로젝트는 기존 FFacio 얼굴인식 흐름을 유지하면서 문 제어 부분을 별도 `ITSOKEY Runtime` Binder 앱으로 분리한 수정본입니다.

## 변경점

- `android/itsokey-runtime-client` AIDL 클라이언트 모듈 추가
- 관리자 설정에 `ITSOKEY 로그인·도어락 선택` 화면 추가
- Runtime 설치·로그인·기기 접근 검사
- 등록 기기 자동 목록과 선택 저장
- 시험 열기는 별도 확인 대화상자를 거쳐서만 실행
- 얼굴 인증과 Runtime 라이브니스가 모두 통과한 경우에만 `OPEN` 호출
- FFacio에는 token/refresh token을 저장하지 않음
- 기존 SmartThings 직접 HTTP 문 제어 코드는 제거
- Runtime 미설치·로그인 만료·기기 미선택 시 문 제어를 안전하게 차단

## 필요한 별도 앱

함께 제공되는 `ITSOKEY-Runtime-1.0.0-source.zip`을 빌드해 먼저 설치해야 합니다.

**두 APK는 동일 인증서로 서명해야 합니다.** Runtime Binder 권한이 `signature` 보호 수준이기 때문입니다. 같은 PC의 debug keystore 또는 동일 release keystore를 사용하세요.

## 빌드

```bash
cd android
./gradlew clean test assembleDebug
```

## 설정 순서

1. ITSOKEY Runtime 설치
2. 이 FFacio 설치
3. FFacio 관리자 설정 열기
4. ITSOKEY 연결 화면에서 카카오 로그인
5. 사용할 도어락 선택
6. Runtime 라이브니스 활성화 확인
7. `인증 성공 시 ITSOKEY 도어락 해제` 활성화
8. 연결 확인은 문을 열지 않고 기기 접근만 검사
9. 실제 시험 열기는 ITSOKEY 설정 화면에서 명시적 확인 후 진행

## 안전 동작

- 기기 ID 형식 검증
- Binder 호출 제한시간 12초
- 인증 실패·라이브니스 비활성화 시 OPEN 미전송
- 문 제어 응답 실패를 인증 성공과 별도로 표시
- FFacio 데이터 초기화 시 ITSOKEY 선택과 활성화 상태 제거
- 토큰은 Runtime 앱의 Android Keystore 저장소에만 존재

## 검증 한계

새 ITSOKEY Java/AIDL 통합 코드는 Android 16 API 스텁으로 정적 컴파일했고, FFacio Kotlin 파일은 구문 오류 여부를 별도로 검사했습니다. 다만 현재 실행 환경에는 Android SDK/완성 Gradle 배포본·실제 계정·HG-1300이 없어 APK 전체 빌드와 실물 문 열기는 실행하지 못했습니다.
