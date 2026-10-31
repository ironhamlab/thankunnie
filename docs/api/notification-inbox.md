# 받은 알림 API

문자 발송에 성공한 알림을 종 목록에서 본다. 로그인한 고객 본인 알림만 조회·읽음 처리할 수 있다. 종 뱃지의 안 읽은 수는 스트림으로 받는다.

`category` 는 `RESERVATION`(예약 확정·취소), `INTEREST`(빈자리·임박·초임박·묶음)다. 생략하면 전체다.

## GET /api/v1/notifications/inbox — 받은 알림 목록

최신 50건만 반환한다. 그보다 오래된 알림은 목록에 없다. `readAt` 이 `null` 이면 아직 읽지 않은 알림이다.

```json
{
  "success": true,
  "data": [
    {
      "inboxId": 10,
      "category": "INTEREST",
      "title": "임박 알림",
      "body": "임박! 예쁜네일-2026년 10월 31일 18:00 땡잡을 기회가 생겼어요!",
      "linkPath": "/shops/1",
      "readAt": null,
      "createdAt": "2026-10-31T15:00:00+09:00"
    }
  ],
  "error": null,
  "timestamp": "2026-10-31T15:10:00+09:00"
}
```

관심만 보려면 `GET /api/v1/notifications/inbox?category=INTEREST` 를 호출한다.

| 상황 | 응답 |
|---|---|
| 비로그인 | 401 `COMMON_401` |
| 고객이 아님 | 403 `COMMON_403` |
| `category` 가 `RESERVATION`·`INTEREST` 가 아님 | 400 `COMMON_400` |
| 알림이 없음 | 200, `data` 는 빈 배열 |

## GET /api/v1/notifications/inbox/stream — 알림 스트림

응답은 `ApiResponse` 가 아니다. `Content-Type` 은 `text/event-stream` 이다. 고객만 연결할 수 있다. 토큰은 다른 API와 같이 `Authorization: Bearer` 로 보낸다.

로그인한 고객 화면은 브라우저 탭이 보일 때 이 주소에 연결해 둔다. 서버는 시간을 재서 이 연결을 끊지 않는다. 탭을 숨기면 화면이 연결을 닫고, 다시 보이면 다시 연결한다.

연결되면 지금 안 읽은 수를 `data` 로 한 번 보낸다. 예시는 안 읽은 알림이 2개일 때다.

```text
data:2
```

그 뒤에 안 읽은 수가 바뀌면 같은 형식으로 새 개수를 다시 보낸다. 바뀌는 때는 두 가지다.

- 문자 발송에 성공해 알림이 저장된 뒤
- 모두 읽음이 끝난 뒤

그 회원이 연결해 둔 탭에만 보낸다. 연결이 없으면 보내지 않는다. 서버는 사용자별로 연결을 메모리에 모아 둔다. 같은 회원이 탭을 여러 개 열면 연결도 여러 개다. 연결이 끊기면 그 연결만 목록에서 뺀다.

| 상황 | 응답 |
|---|---|
| 비로그인 | 401 `COMMON_401` |
| 고객이 아님 | 403 `COMMON_403` |

## PATCH /api/v1/notifications/inbox/read-all — 모두 읽음

안 읽은 알림의 `readAt` 을 지금 시각으로 채운다. 이미 읽은 알림의 시각은 바꾸지 않는다. 요청 본문은 없고, 응답 `data` 는 처리 후 안 읽은 수다.

```json
{
  "success": true,
  "data": 0,
  "error": null,
  "timestamp": "2026-10-31T15:10:00+09:00"
}
```

| 상황 | 응답 |
|---|---|
| 비로그인 | 401 `COMMON_401` |
| 고객이 아님 | 403 `COMMON_403` |

받은 알림 전용 에러 코드는 없다. 위 표의 공통 코드를 쓴다.
