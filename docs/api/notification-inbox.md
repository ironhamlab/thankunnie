# 받은 알림 API

종 목록에서 본인 알림을 본다. 고객은 회원 알림, 사장님은 매장 예약 알림만 조회·읽음 처리할 수 있다. 종 뱃지의 안 읽은 수는 스트림으로 받는다.

고객 알림은 `USER_ID`, 사장님 알림은 `SHOP_ID`로 찾는다. 스트림 연결도 역할과 ID를 같이 키로 쓴다. 회원 ID와 매장 ID 숫자가 같아도 서로 보내지 않는다.

`category` 는 `RESERVATION`(예약 확정·취소), `INTEREST`(빈자리·임박·초임박·묶음)다. 생략하면 그 사람의 전체다. 사장님에게 관심 알림은 없다.

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
      "linkPath": "/shops/1?treatmentId=10&startsAt=2026-10-31T18:00:00",
      "readAt": null,
      "createdAt": "2026-10-31T15:00:00+09:00"
    }
  ],
  "error": null,
  "timestamp": "2026-10-31T15:10:00+09:00"
}
```

업장 알림 한 건의 `linkPath`는 `/shops/{shopId}?treatmentId={시술ID}&startsAt={시작시각}`이다. `startsAt`은 `2026-10-31T18:00:00`처럼 오프셋 없이 둔다. 그 회원에게 그 시각에 예약되는 시술이 여러 개면 ID가 가장 작은 시술을 넣는다. 맞는 시술을 못 찾으면 `/shops/{shopId}`만 둔다. 카테고리·지역 묶음 알림은 `/?view=list&category={categoryId}&region={regionCode}&date={yyyy-MM-dd}`를 유지한다. 이미 저장된 알림의 `linkPath`는 바꾸지 않는다.

관심만 보려면 `GET /api/v1/notifications/inbox?category=INTEREST` 를 호출한다. 사장님이 이렇게 호출하면 200이고 `data` 는 빈 배열이다.

사장님 예약 알림의 `linkPath`는 `/owner/bookings?reservationId={예약ID}`다. 예약 현황은 이 번호의 상세를 연다. 번호가 없는 `/owner/bookings`는 목록의 첫 예약을 연다.

| 상황 | 응답 |
|---|---|
| 비로그인 | 401 `COMMON_401` |
| 고객도 사장님도 아님 | 403 `COMMON_403` |
| `category` 가 `RESERVATION`·`INTEREST` 가 아님 | 400 `COMMON_400` |
| 알림이 없음 | 200, `data` 는 빈 배열 |
| 사장님이 `INTEREST` 를 조회 | 200, `data` 는 빈 배열 |

## GET /api/v1/notifications/inbox/stream — 알림 스트림

응답은 `ApiResponse` 가 아니다. `Content-Type` 은 `text/event-stream` 이다. 고객과 사장님만 연결할 수 있다. 토큰은 다른 API와 같이 `Authorization: Bearer` 로 보낸다.

로그인한 고객·사장님 화면은 브라우저 탭이 보일 때 이 주소에 연결해 둔다. 서버는 시간을 재서 이 연결을 끊지 않는다. 배포 Nginx는 300초 동안 데이터가 없으면 연결을 끊으므로, 서버는 30초마다 주석 `heartbeat` 를 보낸다. 이 줄은 안 읽은 수가 아니고 화면은 무시한다. 탭을 숨기면 화면이 연결을 닫고, 다시 보이면 다시 연결한다.

연결되면 지금 안 읽은 수를 `data` 로 한 번 보낸다. 예시는 안 읽은 알림이 2개일 때다.

```text
data:2
```

그 뒤에 안 읽은 수가 바뀌면 같은 형식으로 새 개수를 다시 보낸다. 바뀌는 때는 다음이다.

- 고객: 문자 발송에 성공해 알림이 저장된 뒤, 한 건 읽음·모두 읽음이 끝난 뒤
- 사장님: 예약 확정·취소 알림이 저장된 뒤, 한 건 읽음·모두 읽음이 끝난 뒤

그 사람만 연결해 둔 탭에 보낸다. 연결이 없으면 보내지 않는다. 서버는 역할과 ID로 연결을 메모리에 모아 둔다. 같은 사람이 탭을 여러 개 열면 연결도 여러 개다. 연결이 끊기면 그 연결만 목록에서 뺀다.

| 상황 | 응답 |
|---|---|
| 비로그인 | 401 `COMMON_401` |
| 고객도 사장님도 아님 | 403 `COMMON_403` |

## PATCH /api/v1/notifications/inbox/{inboxId}/read — 한 건 읽음

그 알림의 `readAt` 을 지금 시각으로 채운다. 이미 읽은 알림의 시각은 바꾸지 않는다. 고객은 본인 회원 알림만, 사장님은 본인 매장 알림만 읽음으로 바꾼다. 요청 본문은 없고, 응답 `data` 는 처리 후 안 읽은 수다.

```json
{
  "success": true,
  "data": 1,
  "error": null,
  "timestamp": "2026-10-31T15:10:00+09:00"
}
```

| 상황 | 응답 |
|---|---|
| 비로그인 | 401 `COMMON_401` |
| 고객도 사장님도 아님 | 403 `COMMON_403` |
| 없거나 다른 사람·다른 매장 알림 | 404 `NOTIFICATION_006` |

## PATCH /api/v1/notifications/inbox/read-all — 모두 읽음

안 읽은 알림의 `readAt` 을 지금 시각으로 채운다. 이미 읽은 알림의 시각은 바꾸지 않는다. 고객은 회원 알림만, 사장님은 매장 알림만 읽음으로 바꾼다. 요청 본문은 없고, 응답 `data` 는 처리 후 안 읽은 수다.

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
| 고객도 사장님도 아님 | 403 `COMMON_403` |

받은 알림 전용 에러 코드는 없다. 위 표의 공통 코드를 쓴다.

## 기존 DB 적용

`NOTIFICATION_INBOX`에 매장 수신 컬럼을 추가했다. 고객 알림은 기존처럼 `USER_ID`만 채우고 `SHOP_ID`는 비운다. 사장님 알림은 `SHOP_ID`만 채운다. 새 DB는 `docs/erd/01_schema_ddl.sql`로 만들면 된다.

이미 만들어진 Oracle에는 아래를 순서대로 실행한다. 컬럼·제약·인덱스가 이미 있으면 그 문장은 다시 실행하지 않는다.

```sql
ALTER TABLE NOTIFICATION_INBOX ADD SHOP_ID NUMBER(19);
ALTER TABLE NOTIFICATION_INBOX MODIFY (USER_ID NULL);
ALTER TABLE NOTIFICATION_INBOX ADD CONSTRAINT FK_NOTIFICATION_INBOX_SHOP
    FOREIGN KEY (SHOP_ID) REFERENCES SHOP (SHOP_ID);
ALTER TABLE NOTIFICATION_INBOX ADD CONSTRAINT CK_NOTIFICATION_INBOX_RECIPIENT CHECK (
    (USER_ID IS NOT NULL AND SHOP_ID IS NULL)
    OR (USER_ID IS NULL AND SHOP_ID IS NOT NULL)
);
CREATE INDEX IDX_NOTIFICATION_INBOX_SHOP ON NOTIFICATION_INBOX (SHOP_ID, CREATED_AT);
```

기존 고객 알림 행은 `USER_ID`가 있고 `SHOP_ID`가 없으므로 체크 제약을 통과한다. Oracle DDL은 자동 커밋되므로 실행 전 대상 스키마를 확인한다.
