/**
 * 내 알림 구독 화면 시연용 예시.
 * API 응답과 같은 모양이며, 업장(STORE)과 카테고리·지역(CATEGORY_REGION) 카드 둘 다 담는다.
 * shopName 은 화면에만 쓰고, 서버 구독 응답에는 없다.
 */
export const EXAMPLE_CATEGORIES = [
  { categoryId: 1, name: '네일' },
  { categoryId: 2, name: '헤어' },
  { categoryId: 3, name: '속눈썹' },
  { categoryId: 4, name: '왁싱' },
  { categoryId: 5, name: '바버' },
];

/** 시연용 매장 이름. 서버 구독 응답에 매장명이 없을 때 업장 카드 제목에 쓴다 */
export const EXAMPLE_SHOP_NAMES = {
  1: '언니네 네일',
  2: '강남 헤어살롱',
};

export const EXAMPLE_SUBSCRIPTIONS = [
  {
    subscriptionId: 9002,
    subscriptionType: 'STORE',
    shopId: 2,
    shopName: '강남 헤어살롱',
    categoryId: null,
    regionCode: null,
    startTime: '2026-10-29T14:00:00+09:00',
    endTime: '2026-10-31T20:00:00+09:00',
    status: 'ACTIVE',
  },
  {
    subscriptionId: 9001,
    subscriptionType: 'CATEGORY_REGION',
    shopId: null,
    shopName: null,
    categoryId: 1,
    regionCode: '11680',
    startTime: '2026-10-28T10:00:00+09:00',
    endTime: '2026-10-30T22:00:00+09:00',
    status: 'ACTIVE',
  },
];

/** 업장 구독 카드 제목. 예시·알려진 매장명이 있으면 그걸 쓰고, 없으면 매장 번호를 쓴다 */
export const shopTitleOf = (item) =>
  item.shopName ?? EXAMPLE_SHOP_NAMES[item.shopId] ?? `매장 ${item.shopId}`;
