import assert from 'node:assert/strict';
import { test } from 'node:test';
import { create } from 'zustand';
import { createExploreStore } from './createExploreStore.js';
import { vacancyMarkers, validateVacancyPage } from '../utils/vacancyResponse.js';

const item = {
  vacancyId: '3-202610281800',
  treatmentId: 3,
  shopName: '매장',
  treatmentName: '시술',
  salePrice: 0,
  latitude: 0,
  longitude: 0,
  startsAt: '2026-10-28T18:00:00+09:00',
  bookingClosesAt: '2026-10-28T17:40:00+09:00',
};
const page = (content = [item]) => ({ content, totalElements: content.length, hasNext: false });

test('좌표 0과 0원은 보존하고 잘못된 좌표는 지도에서 제외한다', () => {
  const markers = vacancyMarkers([
    item,
    { ...item, latitude: null },
    { ...item, longitude: 181 },
    { ...item, latitude: '37' },
  ]);
  assert.equal(markers.length, 1);
  assert.equal(markers[0].lat, 0);
  assert.equal(markers[0].lng, 0);
  assert.equal(markers[0].label, '0원');
  assert.equal(validateVacancyPage(page()).content[0].salePrice, 0);
  assert.throws(() => validateVacancyPage(page([{ ...item, salePrice: '1000' }])));
});

test('늦은 이전 응답이 최신 검색 결과를 덮지 않는다', async () => {
  const pending = [];
  const store = createExploreStore(create, {
    search: () => new Promise((resolve) => pending.push(resolve)),
  });
  const first = store.getState().load('search', { keyword: '이전' });
  const second = store.getState().load('search', { keyword: '최신' });
  pending[1](page([{ ...item, shopName: '최신' }]));
  await second;
  pending[0](page());
  await first;
  assert.equal(store.getState().sections.search.data.content[0].shopName, '최신');
});

test('한 영역 실패가 다른 영역을 지우지 않고 재시도가 성공한다', async () => {
  let failed = true;
  const store = createExploreStore(create, {
    search: async (params) => {
      if (params.fail && failed) throw new Error('조회 실패');
      return page([]);
    },
  });
  await Promise.all([
    store.getState().load('today', {}),
    store.getState().load('closing', { fail: true }),
  ]);
  assert.equal(store.getState().sections.today.status, 'succeeded');
  assert.equal(store.getState().sections.closing.status, 'failed');
  failed = false;
  await store.getState().load('closing', { fail: true });
  assert.equal(store.getState().sections.closing.status, 'succeeded');
});
