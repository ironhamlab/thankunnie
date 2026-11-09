import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  buildCategoryRegionRequest,
  buildStoreRequest,
  duplicateCandidate,
  findDuplicateSubscription,
  hopeTimeError,
  isPendingFavorite,
  isShopSubscribed,
  selectableTreatments,
  shopLabel,
  storeSubscriptionsOf,
  toUpdateBody,
  treatmentLabel,
  treatmentNameOf,
} from './notificationRules.js';

const store = (id, shopId, startTime, endTime, treatmentId = 100) => ({
  subscriptionId: id,
  subscriptionType: 'STORE',
  shopId,
  treatmentId,
  categoryId: null,
  regionCode: null,
  startTime,
  endTime,
});

const category = (id, categoryId, regionCode, startTime, endTime) => ({
  subscriptionId: id,
  subscriptionType: 'CATEGORY_REGION',
  shopId: null,
  categoryId,
  regionCode,
  startTime,
  endTime,
});

test('같은 매장의 업장 알림만 고르고 최근 등록 순을 유지한다', () => {
  const subscriptions = [
    store(3, 10, '2026-11-01T10:00:00+09:00', '2026-11-02T10:00:00+09:00'),
    store(2, 11, '2026-11-01T10:00:00+09:00', '2026-11-02T10:00:00+09:00'),
    category(1, 1, '11680', '2026-11-01T10:00:00+09:00', '2026-11-02T10:00:00+09:00'),
    store(4, '10', '2026-11-03T10:00:00+09:00', '2026-11-04T10:00:00+09:00'),
  ];

  const found = storeSubscriptionsOf(subscriptions, 10);
  assert.deepEqual(
    found.map((item) => item.subscriptionId),
    [3, 4],
  );
});

test('매장명이 있으면 그 이름을 쓰고 없으면 매장 번호를 쓴다', () => {
  assert.equal(shopLabel(2, '강남 헤어살롱'), '강남 헤어살롱');
  assert.equal(shopLabel(2, '  '), '매장 2');
  assert.equal(shopLabel(2, null), '매장 2');
});

test('업장 알림을 하나만 지워도 같은 매장 알림이 남으면 하트는 유지된다', () => {
  const subscriptions = [
    store(2, 10, '2026-11-03T10:00:00+09:00', '2026-11-04T10:00:00+09:00'),
    store(1, 10, '2026-11-01T10:00:00+09:00', '2026-11-02T10:00:00+09:00'),
  ];
  const afterOne = subscriptions.filter((item) => item.subscriptionId !== subscriptions[0].subscriptionId);
  assert.equal(isShopSubscribed(afterOne, 10), true);
  assert.equal(isShopSubscribed([], 10), false);
});

test('희망 시간이 둘 다 없으면 업장 찜이고 시간 오류가 없다', () => {
  const favorite = store(1, 10, null, null);
  assert.equal(isPendingFavorite(favorite), true);
  assert.equal(hopeTimeError(null, null), null);
  assert.equal(
    findDuplicateSubscription([favorite], buildStoreRequest(10, 100, null))?.subscriptionId,
    1,
  );
});

test('희망 종료가 시작과 같거나 이미 지났으면 003이다', () => {
  const past = '2020-01-01T10:00:00+09:00';
  const later = '2020-01-01T12:00:00+09:00';
  assert.equal(hopeTimeError(past, past), 'NOTIFICATION_003');
  assert.equal(hopeTimeError(later, past), 'NOTIFICATION_003');
  assert.equal(hopeTimeError(past, later), 'NOTIFICATION_003');
});

test('희망 종료가 아직 남았으면 시간 오류가 없다', () => {
  const start = new Date(Date.now() + 60 * 60 * 1000).toISOString();
  const end = new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString();
  assert.equal(hopeTimeError(start, end), null);
});

test('같은 매장·같은 시술·같은 희망 시간이면 004이고 시술이나 시간이 다르면 허용한다', () => {
  const subscriptions = [
    store(1, 10, '2026-11-01T10:00:00+09:00', '2026-11-02T18:00:00+09:00', 100),
  ];
  const same = {
    subscriptionType: 'STORE',
    shopId: '10',
    treatmentId: '100',
    startTime: '2026-11-01T10:00:00+09:00',
    endTime: '2026-11-02T18:00:00+09:00',
  };
  const otherTime = { ...same, endTime: '2026-11-03T18:00:00+09:00' };
  const otherTreatment = { ...same, treatmentId: 101 };

  assert.equal(findDuplicateSubscription(subscriptions, same)?.subscriptionId, 1);
  assert.equal(findDuplicateSubscription(subscriptions, otherTime), undefined);
  assert.equal(findDuplicateSubscription(subscriptions, otherTreatment), undefined);
  assert.equal(findDuplicateSubscription(subscriptions, same, 1), undefined);
});

test('카테고리·지역은 시술과 지역과 시간이 모두 같아야 중복이다', () => {
  const subscriptions = [
    category(8, 1, '11680', '2026-11-01T10:00:00+09:00', '2026-11-02T18:00:00+09:00'),
  ];
  const same = {
    subscriptionType: 'CATEGORY_REGION',
    categoryId: '1',
    regionCode: '11680',
    startTime: '2026-11-01T10:00:00+09:00',
    endTime: '2026-11-02T18:00:00+09:00',
  };
  const otherRegion = { ...same, regionCode: '11650' };

  assert.equal(findDuplicateSubscription(subscriptions, same)?.subscriptionId, 8);
  assert.equal(findDuplicateSubscription(subscriptions, otherRegion), undefined);
});

test('업장 알림 수정은 매장 ID를 유지하고 유형은 보내지 않는다', () => {
  const current = store(1, 10, '2026-11-01T10:00:00+09:00', '2026-11-02T18:00:00+09:00');
  const body = toUpdateBody(current, {
    shopId: 99,
    treatmentId: 200,
    categoryId: 3,
    regionCode: '11680',
    startTime: '2026-11-05T10:00:00+09:00',
    endTime: '2026-11-06T18:00:00+09:00',
  });

  assert.deepEqual(body, {
    shopId: 10,
    treatmentId: 200,
    categoryId: null,
    regionCode: null,
    startTime: '2026-11-05T10:00:00+09:00',
    endTime: '2026-11-06T18:00:00+09:00',
  });
  assert.equal('subscriptionType' in body, false);
  assert.equal(duplicateCandidate(current, body).subscriptionType, 'STORE');
});

test('시간 없는 업장 찜은 시술 없이 매장만 보낸다', () => {
  assert.equal(buildStoreRequest(3, null, null).treatmentId, null);
});

test('같은 매장의 시술 없는 찜은 중복이고 시술이 있으면 다른 알림이다', () => {
  const favorite = store(1, 10, null, null, null);
  const sameFavorite = buildStoreRequest(10, null, null);
  const withTreatment = buildStoreRequest(10, 100, null);
  assert.equal(findDuplicateSubscription([favorite], sameFavorite)?.subscriptionId, 1);
  assert.equal(findDuplicateSubscription([favorite], withTreatment), undefined);
});

test('업장 등록은 고른 시술과 희망 시간을 보내고 지역은 보내지 않는다', () => {
  assert.deepEqual(
    buildStoreRequest('3', '100', {
      startTime: '2026-11-01T14:00:00+09:00',
      endTime: '2026-11-03T20:00:00+09:00',
    }),
    {
      subscriptionType: 'STORE',
      shopId: 3,
      treatmentId: 100,
      categoryId: null,
      regionCode: null,
      startTime: '2026-11-01T14:00:00+09:00',
      endTime: '2026-11-03T20:00:00+09:00',
    },
  );
});

test('알림에 고를 수 있는 시술은 판매 중인 일반 시술만이다', () => {
  const treatments = [
    { treatmentId: 1, name: '젤네일', treatmentType: 'NORMAL', status: 'ACTIVE' },
    { treatmentId: 2, name: '모델', treatmentType: 'MODEL', status: 'ACTIVE' },
    { treatmentId: 3, name: '중지', treatmentType: 'NORMAL', status: 'INACTIVE' },
  ];
  assert.deepEqual(
    selectableTreatments(treatments).map((item) => item.treatmentId),
    [1],
  );
  assert.equal(treatmentNameOf(treatments, 1), '젤네일');
  assert.equal(treatmentLabel(9, '  '), '시술 9');
});

test('카테고리·지역 등록은 업장 ID 없이 +09:00 시각을 보낸다', () => {
  assert.deepEqual(
    buildCategoryRegionRequest({
      categoryId: '1',
      regionCode: '11680',
      startDate: '2026-11-01',
      startTime: '14:00',
      endDate: '2026-11-03',
      endTime: '20:00',
    }),
    {
      subscriptionType: 'CATEGORY_REGION',
      shopId: null,
      categoryId: 1,
      regionCode: '11680',
      startTime: '2026-11-01T14:00:00+09:00',
      endTime: '2026-11-03T20:00:00+09:00',
    },
  );
});

test('카테고리 알림 수정은 매장 ID를 비우고 시술·지역을 반영한다', () => {
  const current = category(8, 1, '11680', '2026-11-01T10:00:00+09:00', '2026-11-02T18:00:00+09:00');
  const body = toUpdateBody(current, {
    shopId: 10,
    categoryId: 2,
    regionCode: '11650',
    startTime: '2026-11-05T10:00:00+09:00',
    endTime: '2026-11-06T18:00:00+09:00',
  });

  assert.equal(body.shopId, null);
  assert.equal(body.categoryId, 2);
  assert.equal(body.regionCode, '11650');
});
