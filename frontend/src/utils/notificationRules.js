import { toKstDateTime } from './scheduleDate.js';

const sameInstant = (left, right) => {
  if (left == null && right == null) return true;
  if (left == null || right == null) return false;
  return new Date(left).getTime() === new Date(right).getTime();
};

/** 업장 구독인데 희망 시간이 없으면 알림 전 찜이다. 시간을 넣으면 알림 대상이 된다. */
export function isPendingFavorite(item) {
  return item?.subscriptionType === 'STORE' && item.startTime == null && item.endTime == null;
}

/** 이 매장의 업장(STORE) 구독. 목록은 최근 등록이 앞이다. */
export function storeSubscriptionsOf(subscriptions, shopId) {
  return subscriptions.filter(
    (item) => item.subscriptionType === 'STORE' && Number(item.shopId) === Number(shopId),
  );
}

/** 매장 조회에서 이름을 받았으면 그 이름, 없으면 매장 번호. */
export function shopLabel(shopId, shopName) {
  if (typeof shopName === 'string' && shopName.trim()) return shopName.trim();
  return `매장 ${shopId}`;
}

/** 업장 구독이 하나라도 남아 있으면 매장 상세 하트는 켜 둔다. */
export function isShopSubscribed(subscriptions, shopId) {
  return storeSubscriptionsOf(subscriptions, shopId).length > 0;
}

export function sameSubscriptionId(left, right) {
  return Number(left) === Number(right);
}

/**
 * 희망 종료가 시작보다 같거나 이르거나, 종료가 지금과 같거나 지났으면 003.
 * 서버 NotificationService 와 같은 기준이다.
 */
export function hopeTimeError(startTime, endTime) {
  if (startTime == null && endTime == null) return null;
  const start = new Date(startTime).getTime();
  const end = new Date(endTime).getTime();
  const invalid = Number.isNaN(start) || Number.isNaN(end) || end <= start || end <= Date.now();
  if (!invalid) return null;
  return 'NOTIFICATION_003';
}

/**
 * 같은 대상이고 희망 시작·종료가 둘 다 같으면 004.
 * 수정할 때는 excludeId 로 자기 구독을 뺀다.
 */
export function findDuplicateSubscription(subscriptions, candidate, excludeId) {
  return subscriptions.find((item) => {
    if (excludeId != null && item.subscriptionId === excludeId) return false;
    if (item.subscriptionType !== candidate.subscriptionType) return false;
    if (!sameInstant(item.startTime, candidate.startTime)) return false;
    if (!sameInstant(item.endTime, candidate.endTime)) return false;
    if (candidate.subscriptionType === 'STORE') {
      return Number(item.shopId) === Number(candidate.shopId);
    }
    return (
      Number(item.categoryId) === Number(candidate.categoryId) &&
      item.regionCode === candidate.regionCode
    );
  });
}

/**
 * 수정 본문. 구독 유형은 보내지 않는다.
 * 업장 구독은 매장 ID를 유지하고 희망 시간만 바꾼다.
 * 카테고리·지역 구독은 시술·지역·시간을 바꾼다.
 */
export function toUpdateBody(current, request) {
  if (current?.subscriptionType === 'STORE') {
    return {
      shopId: current.shopId,
      categoryId: null,
      regionCode: null,
      startTime: request.startTime,
      endTime: request.endTime,
    };
  }
  if (current?.subscriptionType === 'CATEGORY_REGION') {
    return {
      shopId: null,
      categoryId: request.categoryId,
      regionCode: request.regionCode,
      startTime: request.startTime,
      endTime: request.endTime,
    };
  }
  return {
    shopId: request.shopId ?? null,
    categoryId: request.categoryId ?? null,
    regionCode: request.regionCode ?? null,
    startTime: request.startTime,
    endTime: request.endTime,
  };
}

/** 업장 구독 등록 본문. 시술·지역은 보내지 않는다. 희망 시각이 없으면 알림 전 찜이다. */
export function buildStoreRequest(shopId, period) {
  return {
    subscriptionType: 'STORE',
    shopId: Number(shopId),
    categoryId: null,
    regionCode: null,
    startTime: period?.startTime ?? null,
    endTime: period?.endTime ?? null,
  };
}

/** 카테고리·지역 구독 등록 본문. 업장 ID는 보내지 않고, 희망 시각은 +09:00 이다. */
export function buildCategoryRegionRequest(form) {
  return {
    subscriptionType: 'CATEGORY_REGION',
    shopId: null,
    categoryId: Number(form.categoryId),
    regionCode: form.regionCode,
    startTime: toKstDateTime(form.startDate, form.startTime),
    endTime: toKstDateTime(form.endDate, form.endTime),
  };
}

/** 중복 비교에 쓸 유형. 수정 요청에는 유형이 없으므로 기존 구독의 유형을 쓴다. */
export function duplicateCandidate(current, body) {
  return {
    subscriptionType: current?.subscriptionType,
    shopId: body.shopId,
    categoryId: body.categoryId,
    regionCode: body.regionCode,
    startTime: body.startTime,
    endTime: body.endTime,
  };
}
