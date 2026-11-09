import { toKstDateTime } from './scheduleDate.js';

const sameId = (left, right) => {
  if (left == null && right == null) return true;
  if (left == null || right == null) return false;
  return Number(left) === Number(right);
};

const sameInstant = (left, right) => {
  if (left == null && right == null) return true;
  if (left == null || right == null) return false;
  return new Date(left).getTime() === new Date(right).getTime();
};

/** 업장 알림인데 희망 시간이 없으면 알림 전 찜이다. 시간을 넣으면 알림 대상이 된다. */
export function isPendingFavorite(item) {
  return item?.subscriptionType === 'STORE' && item.startTime == null && item.endTime == null;
}

/** 이 매장의 업장(STORE) 알림. 목록은 최근 등록이 앞이다. */
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

/** 업장 알림이 하나라도 남아 있으면 매장 상세 하트는 켜 둔다. */
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

/** 판매 중인 일반 시술만 알림에 고를 수 있다. 초보 시술 모집과 판매 중이 아닌 시술은 뺀다. */
export function selectableTreatments(treatments) {
  return (treatments ?? []).filter((item) => {
    if (item?.treatmentType !== 'NORMAL') return false;
    if (item.status && item.status !== 'ACTIVE') return false;
    return true;
  });
}

/** 시술명을 받았으면 그 이름, 없으면 시술 번호. */
export function treatmentLabel(treatmentId, treatmentName) {
  if (typeof treatmentName === 'string' && treatmentName.trim()) return treatmentName.trim();
  if (treatmentId == null) return '시술';
  return `시술 ${treatmentId}`;
}

/** 매장 시술 목록에서 알림에 들어 있는 시술 이름을 찾는다. */
export function treatmentNameOf(treatments, treatmentId) {
  const found = (treatments ?? []).find((item) => Number(item.treatmentId) === Number(treatmentId));
  return treatmentLabel(treatmentId, found?.name);
}

/**
 * 같은 대상이고 희망 시작·종료가 둘 다 같으면 004.
 * 업장 알림은 매장과 시술이 같아야 중복이다. 시술이 다르면 다른 알림이다.
 * 수정할 때는 excludeId 로 자기 알림을 뺀다.
 */
export function findDuplicateSubscription(subscriptions, candidate, excludeId) {
  return subscriptions.find((item) => {
    if (excludeId != null && item.subscriptionId === excludeId) return false;
    if (item.subscriptionType !== candidate.subscriptionType) return false;
    if (!sameInstant(item.startTime, candidate.startTime)) return false;
    if (!sameInstant(item.endTime, candidate.endTime)) return false;
    if (candidate.subscriptionType === 'STORE') {
      return Number(item.shopId) === Number(candidate.shopId) && sameId(item.treatmentId, candidate.treatmentId);
    }
    return (
      Number(item.categoryId) === Number(candidate.categoryId) &&
      item.regionCode === candidate.regionCode
    );
  });
}

/**
 * 수정 본문. 알림 유형은 보내지 않는다.
 * 업장 알림은 매장 ID를 유지하고 시술과 희망 시간을 바꾼다.
 * 카테고리·지역 알림은 시술·지역·시간을 바꾼다.
 */
export function toUpdateBody(current, request) {
  if (current?.subscriptionType === 'STORE') {
    return {
      shopId: current.shopId,
      treatmentId: Number(request.treatmentId),
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

/** 업장 알림 등록 본문. 고객이 고른 시술을 넣고, 지역은 보내지 않는다. 희망 시각이 없으면 알림 전 찜이다. */
export function buildStoreRequest(shopId, treatmentId, period) {
  return {
    subscriptionType: 'STORE',
    shopId: Number(shopId),
    treatmentId: treatmentId == null || treatmentId === '' ? null : Number(treatmentId),
    categoryId: null,
    regionCode: null,
    startTime: period?.startTime ?? null,
    endTime: period?.endTime ?? null,
  };
}

/** 카테고리·지역 알림 등록 본문. 업장 ID는 보내지 않고, 희망 시각은 +09:00 이다. */
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

/** 중복 비교에 쓸 유형. 수정 요청에는 유형이 없으므로 기존 알림의 유형을 쓴다. */
export function duplicateCandidate(current, body) {
  return {
    subscriptionType: current?.subscriptionType,
    shopId: body.shopId,
    treatmentId: body.treatmentId,
    categoryId: body.categoryId,
    regionCode: body.regionCode,
    startTime: body.startTime,
    endTime: body.endTime,
  };
}
