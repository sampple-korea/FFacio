> **0.9.0 안내:** 현재 문 제어는 SmartThings가 아니라 별도 ITSOKEY Runtime Binder로 이전되었습니다. 이 문서의 SmartThings 내용은 이전 버전 역사 기록입니다. 현재 구현은 `../ITSOKEY_INTEGRATION.md`를 참고하세요.

# SmartThings 도어락 연동

FFacio는 임의의 릴레이 URL을 호출하지 않습니다. 관리 화면에서 SmartThings Device ID와 access token을 저장하면, 얼굴 인증 성공 뒤 SmartThings REST API의 기기 명령 엔드포인트로 `main` 컴포넌트의 `lock` capability에 `unlock` 명령을 보냅니다.

문 열림은 다음 조건을 전부 통과할 때만 실행됩니다.

- FFacio Runtime 라이브니스가 켜져 있고 현재 프레임이 `runtime_live` 상태이며 점수가 기준 이상
- 품질·얼굴 크기·심한 고개 회전 기준 통과
- 모든 호환 등록 사용자와의 템플릿 비교 완료
- 최고 유사도와 2위 점수 차이 기준 통과
- SmartThings Device ID와 token이 설정되고 문 열림 스위치가 켜짐
- 단일 실행 잠금과 재호출 대기시간 통과

관리 화면의 연결 확인은 lock capability 상태만 조회하며 unlock 명령을 보내지 않습니다. 따라서 기기 조회·상태 읽기 접근은 확인하지만, 실제 unlock 명령 권한은 인증 뒤 명령 요청 응답에서 최종 확인됩니다. 명령 API가 `ACCEPTED`를 반환해도 이는 요청이 대기열에 들어갔다는 뜻이므로 앱은 실제 물리 잠금 해제 완료로 단정하지 않습니다.

access token은 Android Keystore AES-GCM으로 암호화해 보관하고, 저장 항목 이름을 추가 인증 데이터(AAD)로 묶습니다. 로그·승인 기록·SmartThings 요청 본문에는 사용자 이름이나 token을 넣지 않습니다. Device ID는 URL·경로 문자를 허용하지 않고, token은 길이·공백·제어문자를 검사합니다. 설정 저장이 중간에 실패하면 Device ID·암호문·활성화 플래그를 함께 제거해 반쯤 저장된 설정을 남기지 않습니다. 라이브니스를 끄면 문 열림도 자동으로 비활성화됩니다. 이 상태에서는 Head Admin 얼굴 승인도 비활성화하고 Android 화면 잠금으로 관리자 작업을 보호합니다.

## Token 운영 주의

직접 입력하는 Personal Access Token은 단기 운영·시험용입니다. 필요한 기기 읽기·제어 범위만 부여하고, 만료 또는 401 응답이 나오면 새 token으로 갱신해야 합니다. 장기 상시 운용은 OAuth 기반 Service Integration으로 전환하는 것이 적합합니다.
