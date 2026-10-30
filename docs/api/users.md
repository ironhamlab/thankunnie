# 회원 API (UNNIE-149)

고객·관리자 계정(`APP_USER`)의 정보를 다룬다. 사장님 계정은 `SHOP` 행에 있어 매장 API(`shops.md`)를 쓴다.

## GET /api/v1/users/me — 내 회원 정보

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "name": "고객일",
    "phone": "010-1111-1111",
    "createdAt": "2026-10-01T09:00:00+09:00"
  },
  "error": null,
  "timestamp": "2026-10-30T23:01:44+09:00"
}
```

| 상황 | 응답 |
|---|---|
| 비로그인 | 401 `COMMON_401` |
| 사장님 | 403 `COMMON_403` |
| 회원 행 없음 | 404 `COMMON_404` |

- 현재 사용자 ID·역할·매장 ID만 필요하면 `GET /api/v1/auth/me`를 쓴다.
