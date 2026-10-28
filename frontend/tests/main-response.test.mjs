import assert from 'node:assert/strict';
import test from 'node:test';
import { createStore } from 'zustand/vanilla';
import { createMainStore } from '../src/store/createMainStore.js';
import { buildMainParams } from '../src/constants/mainPresets.js';

const item = {
  id: 101,
  title: '젤 네일 원컬러',
  startsAt: '2026-10-28T18:00:00+09:00',
  applicationClosesAt: '2026-10-28T17:00:00+09:00',
  shopName: '예시 뷰티샵',
  price: 32500,
  latitude: 37.4982,
  longitude: 127.0276,
};
const load = async (content) => {
  const store = createMainStore(createStore, { search: async () => ({ content }) });
  await store.getState().fetchSection('TODAY');
  return store.getState().sections.TODAY;
};

test('8개 필드와 페이지 정보를 보존하며 프리셋 조건을 API에 전달한다', async () => {
  const now = new Date('2026-10-28T12:00:00+09:00');
  const response = { content: [item], page: 0, size: 6, totalElements: 1, hasNext: false };
  const store = createMainStore(createStore, {
    search: async (params) => {
      assert.deepEqual(params, buildMainParams('TODAY', now));
      return response;
    },
  });
  await store.getState().fetchSection('TODAY', now);
  assert.equal(store.getState().sections.TODAY.status, 'succeeded');
  assert.deepEqual(store.getState().sections.TODAY.data, response);
});

test('0원, 좌표 0·경계값, null 마감 및 미등록 좌표는 허용한다', async () => {
  const content = [
    { ...item, price: 0, latitude: 0, longitude: 0 },
    { ...item, id: 102, latitude: -90, longitude: -180 },
    { ...item, id: 103, latitude: 90, longitude: 180 },
    { ...item, id: 104, applicationClosesAt: null, latitude: null, longitude: null },
  ];
  const section = await load(content);
  assert.equal(section.status, 'succeeded');
  assert.deepEqual(section.data.content, content);
});

test('필드 누락·잘못된 가격·좌표·시각은 빈 결과 대신 실패로 처리한다', async (t) => {
  const cases = [
    ...Object.keys(item).map((field) => [field + ' 누락', { [field]: undefined }]),
    ['빈 매장명', { shopName: '  ' }],
    ['문자열 가격', { price: '32500' }],
    ['음수 가격', { price: -1 }],
    ['소수 가격', { price: 0.5 }],
    ['NaN 가격', { price: NaN }],
    ['무한 가격', { price: Infinity }],
    ['문자열 위도', { latitude: '37.4982' }],
    ['NaN 위도', { latitude: NaN }],
    ['위도 상한 초과', { latitude: 90.1 }],
    ['위도 하한 초과', { latitude: -90.1 }],
    ['경도 상한 초과', { longitude: 180.1 }],
    ['경도 하한 초과', { longitude: -180.1 }],
    ['무한 경도', { longitude: Infinity }],
    ['위도만 없음', { latitude: null }],
    ['경도만 없음', { longitude: null }],
    ['시간대 없음', { startsAt: '2026-10-28T18:00:00' }],
    ['달력 날짜 오류', { applicationClosesAt: '2026-02-29T18:00:00+09:00' }],
  ];
  for (const [label, patch] of cases) {
    await t.test(label, async () => {
      const section = await load([item, { ...item, id: 102, ...patch }]);
      assert.equal(section.status, 'failed');
      assert.equal(section.data, null);
      assert.equal(section.error.message, '목록 응답 형식이 올바르지 않습니다.');
    });
  }
});

test('한 영역의 실패가 다른 영역의 빈 결과를 숨기지 않고 재시도할 수 있다', async () => {
  let failToday = true;
  const store = createMainStore(createStore, {
    search: async (params) => {
      if (params.startDate && failToday) throw { code: 'HTTP_404', message: '탐색 API 준비 중' };
      return { content: [] };
    },
  });
  await store.getState().fetchAll();
  assert.equal(store.getState().sections.TODAY.error.code, 'HTTP_404');
  assert.equal(store.getState().sections.CLOSING_SOON.status, 'succeeded');
  failToday = false;
  await store.getState().fetchSection('TODAY');
  assert.equal(store.getState().sections.TODAY.status, 'succeeded');
  assert.equal(store.getState().sections.TODAY.error, null);
});

test('늦게 도착한 이전 응답이 최신 매장명·가격·좌표를 덮어쓰지 않는다', async () => {
  const pending = [];
  const store = createMainStore(createStore, {
    search: () => new Promise((resolve) => pending.push(resolve)),
  });
  const first = store.getState().fetchSection('TODAY');
  const second = store.getState().fetchSection('TODAY');
  const latest = { ...item, shopName: '최신 매장', price: 0, latitude: 0, longitude: 0 };
  pending[1]({ content: [latest] });
  await second;
  pending[0]({ content: [item] });
  await first;
  assert.deepEqual(store.getState().sections.TODAY.data.content, [latest]);
});

test('한국 자정 날짜와 마감 임박 24시간 조건을 유지한다', () => {
  assert.equal(buildMainParams('TODAY', new Date('2026-10-28T14:59:59Z')).startDate, '2026-10-28');
  assert.equal(buildMainParams('TODAY', new Date('2026-10-28T15:00:00Z')).startDate, '2026-10-29');
  assert.deepEqual(buildMainParams('CLOSING_SOON'), {
    availableOnly: true,
    page: 0,
    size: 6,
    closingWithinHours: 24,
    sort: 'CLOSING_ASC',
  });
});
