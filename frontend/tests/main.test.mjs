import test from 'node:test';
import { createStore as createState } from 'zustand/vanilla';
import assert from 'node:assert/strict';
import { buildMainParams } from '../src/constants/mainPresets.js';
import { createItemApi } from '../src/api/itemApi.js';
import { createMainStore } from '../src/store/createMainStore.js';

test('한국 자정 전후에 오늘 프리셋의 날짜가 바뀐다', () => {
  const before = buildMainParams('TODAY', new Date('2026-10-22T14:59:59Z'));
  const after = buildMainParams('TODAY', new Date('2026-10-22T15:00:00Z'));
  assert.equal(before.startDate, '2026-10-22');
  assert.equal(after.startDate, '2026-10-23');
  assert.equal(after.endDate, after.startDate);
  assert.equal(after.sort, 'START_ASC');
});

test('마감 임박은 24시간, 6개, 첫 페이지 조건으로 조회한다', () => {
  assert.deepEqual(buildMainParams('CLOSING_SOON'), {
    availableOnly: true, page: 0, size: 6, closingWithinHours: 24, sort: 'CLOSING_ASC',
  });
  assert.throws(() => buildMainParams('UNKNOWN'), RangeError);
});

test('API는 프리셋 조건을 공통 클라이언트에 전달한다', async () => {
  const params = buildMainParams('CLOSING_SOON');
  const api = createItemApi({ get: async (path, config) => {
    assert.equal(path, '/v1/items');
    assert.deepEqual(config, { params });
    return { content: [] };
  } });
  assert.deepEqual(await api.search(params), { content: [] });
});

test('한 영역이 실패해도 다른 영역의 빈 결과는 성공으로 유지하며 재시도한다', async () => {
  let failToday = true;
  const store = createMainStore(createState, { search: async (params) => {
    if (params.startDate && failToday) throw { response: { data: { error: { code: 'SEARCH_001', message: '조회 실패' } } } };
    return { content: [] };
  } });
  await store.getState().fetchAll();
  assert.equal(store.getState().sections.TODAY.status, 'failed');
  assert.equal(store.getState().sections.TODAY.error.code, 'SEARCH_001');
  assert.equal(store.getState().sections.CLOSING_SOON.status, 'succeeded');
  failToday = false;
  await store.getState().fetchSection('TODAY');
  assert.equal(store.getState().sections.TODAY.status, 'succeeded');
  assert.equal(store.getState().sections.TODAY.error, null);
});

test('이전 요청이 늦게 완료되어도 최신 응답을 덮어쓰지 않는다', async () => {
  const pending = [];
  const store = createMainStore(createState, { search: () => new Promise((resolve) => pending.push(resolve)) });
  const first = store.getState().fetchSection('TODAY');
  const second = store.getState().fetchSection('TODAY');
  pending[1]({ content: [{ id: 2 }] });
  await second;
  pending[0]({ content: [{ id: 1 }] });
  await first;
  assert.equal(store.getState().sections.TODAY.data.content[0].id, 2);
});

test('잘못된 API 응답을 빈 목록으로 표시하지 않는다', async () => {
  const store = createMainStore(createState, { search: async () => ({ unexpected: [] }) });
  await store.getState().fetchSection('TODAY');
  assert.equal(store.getState().sections.TODAY.status, 'failed');
});

test('공통 axiosInstance의 정규화된 오류를 보존한다', async () => {
  const store = createMainStore(createState, { search: async () => {
    throw { code: 'COMMON_400', message: '잘못된 요청입니다.', status: 400 };
  } });
  await store.getState().fetchSection('TODAY');
  assert.deepEqual(store.getState().sections.TODAY.error, {
    code: 'COMMON_400', message: '잘못된 요청입니다.',
  });
});

test('오래된 요청 실패가 최신 성공 결과를 변경하지 않는다', async () => {
  const pending = [];
  const store = createMainStore(createState, { search: () => new Promise((resolve, reject) => pending.push({ resolve, reject })) });
  const first = store.getState().fetchSection('TODAY');
  const second = store.getState().fetchSection('TODAY');
  pending[1].resolve({ content: [{ id: 2 }] });
  await second;
  pending[0].reject(new Error('이전 요청 실패'));
  await first;
  assert.equal(store.getState().sections.TODAY.status, 'succeeded');
  assert.equal(store.getState().sections.TODAY.data.content[0].id, 2);
  assert.equal(store.getState().sections.TODAY.error, null);
});