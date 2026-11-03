# 관리자 매장 가입 신청 API (UNNIE-186)

> 기준: `backend/src/main/java/com/ucamp/app/domain/auth/controller/AdminShopApplicationController.java`
> 모두 관리자(`ADMIN`)만 호출할 수 있다. 응답은 공통 `ApiResponse` 형식이다. 오류 코드는 `error-codes.md`의 ADMIN 참고.

## 매장 상태 (`SHOP.STATUS`)

| 값 | 뜻 | 탐색 노출 |
|---|---|---|
| `PENDING` | 승인 대기 | 안 함 |
| `REJECTED` | 거절. 사유는 `SHOP.REJECT_REASON` | 안 함 |
| `ACTIVE` | 승인됨 | 함 |
| `INACTIVE` | 노출 중지 | 안 함 |

- 매장 가입을 `PENDING` 으로 받고, 대기·거절 매장의 로그인을 막아 사유를 보여주는 일은 매장 가입 작업에서 맡는다. 이 API 는 상태를 바꾸기만 한다.
- 승인된 매장의 상태는 `ACTIVE` 이고 거절 사유는 지워진다.

## GET /api/v1/admin/shop-applications

쿼리: `status`(기본 `PENDING`), `keyword`(선택, 50자까지), `page`(기본 0), `size`(기본 20, 1~100). 신청 순으로 반환한다.

`keyword` 는 매장명·아이디·연락처에서 일부 일치로 찾는다. 대소문자를 구분하지 않고, 연락처는 하이픈 없이 입력해도 찾는다.

`businessNumber` 는 숫자 10자리다. 번호 없이 가입한 기존 매장은 `null` 이다.

```json
{
  "content": [
    {
      "shopId": 1033,
      "loginId": "demo186_p2",
      "name": "스시 땡잡은 오마카세",
      "categoryId": 1,
      "categoryName": "네일",
      "address": "서울특별시 강남구 선릉로 300",
      "addressDetail": null,
      "phone": "02-555-1862",
      "businessNumber": "0000001862",
      "description": "런치 오마카세 코스를 판매합니다.",
      "status": "PENDING",
      "rejectReason": null,
      "appliedAt": "2026-11-02T11:30:00+09:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

## PATCH /api/v1/admin/shop-applications/{shopId}/approve

승인 대기 신청을 승인한다. 응답은 처리된 신청 한 건이다.

## PATCH /api/v1/admin/shop-applications/{shopId}/reject

```json
{ "reason": "서비스 대상 업종이 아니에요." }
```

승인 대기 신청을 거절한다. `reason` 은 필수이며 500자까지다. 응답은 처리된 신청 한 건이다.

## PATCH /api/v1/admin/shop-applications/{shopId}/cancel-approval

잘못 승인한 매장을 거절 상태로 바꾼다. 본문은 거절과 같다(`reason` 필수). 점유 중·확정·취소 처리 중인 예약 중 아직 끝나지 않은 예약이 있으면 `ADMIN_004` 로 막는다.

## PATCH /api/v1/admin/shop-applications/{shopId}/cancel-rejection

잘못 거절한 신청을 승인 대기로 되돌리고 거절 사유를 지운다. 본문은 없다.
