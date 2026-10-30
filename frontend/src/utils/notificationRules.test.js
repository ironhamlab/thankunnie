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
  shopLabel,
  storeSubscriptionsOf,
  toUpdateBody,
} from './notificationRules.js';

const store = (id, shopId, startTime, endTime) => ({
  subscriptionId: id,
  subscriptionType: 'STORE',
  shopId,
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

test('같은 매장의 업장 구독만 고르고 최근 등록 순을 유지한다', () => {
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

test('업장 구독을 하나만 지워도 같은 매장 구독이 남으면 하트는 유지된다', () => {
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
    findDuplicateSubscription([favorite], buildStoreRequest(10, null))?.subscriptionId,
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

test('같은 매장·같은 희망 시간이면 004이고 시간이 다르면 허용한다', () => {
  const subscriptions = [
    store(1, 10, '2026-11-01T10:00:00+09:00', '2026-11-02T18:00:00+09:00'),
  ];
  const same = {
    subscriptionType: 'STORE',
    shopId: '10',
    startTime: '2026-11-01T10:00:00+09:00',
    endTime: '2026-11-02T18:00:00+09:00',
  };
  const otherTime = { ...same, endTime: '2026-11-03T18:00:00+09:00' };

  assert.equal(findDuplicateSubscription(subscriptions, same)?.subscriptionId, 1);
  assert.equal(findDuplicateSubscription(subscriptions, otherTime), undefined);
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

test('업장 구독 수정은 매장 ID를 유지하고 유형은 보내지 않는다', () => {
  const current = store(1, 10, '2026-11-01T10:00:00+09:00', '2026-11-02T18:00:00+09:00');
  const body = toUpdateBody(current, {
    shopId: 99,
    categoryId: 3,
    regionCode: '11680',
    startTime: '2026-11-05T10:00:00+09:00',
    endTime: '2026-11-06T18:00:00+09:00',
  });

  assert.deepEqual(body, {
    shopId: 10,
    categoryId: null,
    regionCode: null,
    startTime: '2026-11-05T10:00:00+09:00',
    endTime: '2026-11-06T18:00:00+09:00',
  });
  assert.equal('subscriptionType' in body, false);
  assert.equal(duplicateCandidate(current, body).subscriptionType, 'STORE');
});

test('업장 등록은 시술·지역 없이 고른 희망 시간을 보낸다', () => {
  assert.deepEqual(
    buildStoreRequest('3', {
      startTime: '2026-11-01T14:00:00+09:00',
      endTime: '2026-11-03T20:00:00+09:00',
    }),
    {
      subscriptionType: 'STORE',
      shopId: 3,
      categoryId: null,
      regionCode: null,
      startTime: '2026-11-01T14:00:00+09:00',
      endTime: '2026-11-03T20:00:00+09:00',
    },
  );
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

test('카테고리 구독 수정은 매장 ID를 비우고 시술·지역을 반영한다', () => {
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
