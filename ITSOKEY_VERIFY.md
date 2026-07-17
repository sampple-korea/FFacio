# ITSOKEY 연동 검증 기록

검증일: 2026-07-17 KST

## 통과

- FFacio Manifest XML 파싱
- `:itsokey-runtime-client` settings/dependency 연결 확인
- Runtime 쪽 AIDL과 FFacio 쪽 AIDL 동일 확인
- `ItsokeySettingsActivity.java` Android 16 스텁 정적 컴파일
- 클라이언트 Java Android 16 스텁 정적 컴파일
- `MainActivity.kt` 구문 수준 검사에서 파서 오류 없음
- 기존 SmartThings 직접 `HttpURLConnection` 문 제어 제거 확인
- ITSOKEY token 원문을 FFacio 설정에 저장하지 않음 확인
- 얼굴 인증 + 라이브니스 + 선택 기기 조건에서만 OPEN 호출 확인

## 실행하지 못한 항목

- Compose를 포함한 전체 Gradle 빌드
- APK 설치 및 동일 서명 Binder 연결
- 카카오 로그인과 ITSOKEY 서버 실계정 호출
- 실제 도어락 OPEN

이 ZIP은 소스 통합본입니다. 실제 기기에서 완전 작동을 확정한 APK가 아닙니다.
