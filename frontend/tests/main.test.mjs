import test from 'node:test';
import { createStore as createState } from 'zustand/vanilla';
import assert from 'node:assert/strict';
import { buildMainParams } from '../src/constants/mainPresets.js';
import { createItemApi } from '../src/api/itemApi.js';
import { createMainStore } from '../src/store/createMainStore.js';


const validItem = {
  id: 1,
  title: '예시 항목',
  startsAt: '2026-10-23T18:00:00+09:00',
};
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
  pending[1]({ content: [{ ...validItem, id: 2 }] });
  await second;
  pending[0]({ content: [{ ...validItem, id: 1 }] });
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
  pending[1].resolve({ content: [{ ...validItem, id: 2 }] });
  await second;
  pending[0].reject(new Error('이전 요청 실패'));
  await first;
  assert.equal(store.getState().sections.TODAY.status, 'succeeded');
  assert.equal(store.getState().sections.TODAY.data.content[0].id, 2);
  assert.equal(store.getState().sections.TODAY.error, null);
});
test('잘못된 목록 원소가 하나라도 있으면 성공 대신 실패 상태로 처리한다', async (t) => {
  const cases = [
    ['null 원소', null],
    ['문자열 원소', 'invalid'],
    ['ID 누락', { ...validItem, id: undefined }],
    ['음수 ID', { ...validItem, id: -1 }],
    ['소수 ID', { ...validItem, id: 1.5 }],
    ['문자열 ID', { ...validItem, id: '1' }],
    ['안전한 정수 범위 초과 ID', { ...validItem, id: Number.MAX_SAFE_INTEGER + 1 }],
    ['제목 누락', { ...validItem, title: undefined }],
    ['문자열 아닌 제목', { ...validItem, title: 123 }],
    ['시작 시각 누락', { ...validItem, startsAt: undefined }],
    ['null 시작 시각', { ...validItem, startsAt: null }],
    ['숫자 시작 시각', { ...validItem, startsAt: 123 }],
    ['빈 시작 시각', { ...validItem, startsAt: '' }],
    ['잘못된 시작 시각', { ...validItem, startsAt: 'invalid-date' }],
    ['잘못된 마감 시각', { ...validItem, applicationClosesAt: 'invalid-date' }],
    ['숫자 마감 시각', { ...validItem, applicationClosesAt: 123 }],
    ['0 마감 시각', { ...validItem, applicationClosesAt: 0 }],
    ['false 마감 시각', { ...validItem, applicationClosesAt: false }],
    ['빈 마감 시각', { ...validItem, applicationClosesAt: '' }],
  ];
  for (const [name, invalidItem] of cases) {
    await t.test(name, async () => {
      const store = createMainStore(createState, {
        search: async () => ({ content: [validItem, invalidItem] }),
      });
      await store.getState().fetchSection('TODAY');
      const section = store.getState().sections.TODAY;
      assert.equal(section.status, 'failed');
      assert.equal(section.data, null);
      assert.equal(section.error.message, '목록 응답 형식이 올바르지 않습니다.');
    });
  }
});

test('유효한 필수 필드와 생략·null·유효한 선택 날짜는 허용한다', async () => {
  const content = [
    validItem,
    { ...validItem, id: 2, applicationClosesAt: null },
    { ...validItem, id: 3, applicationClosesAt: '2026-10-23T17:00:00+09:00' },
  ];
  const store = createMainStore(createState, { search: async () => ({ content }) });
  await store.getState().fetchSection('TODAY');
  assert.equal(store.getState().sections.TODAY.status, 'succeeded');
  assert.deepEqual(store.getState().sections.TODAY.data.content, content);
});
test('시작·마감 시각의 오프셋 누락과 달력·시각·오프셋 범위 오류는 거부한다', async () => {
  const values = [
    '2026-10-23',
    '2026-10-23T18:00',
    '2026-10-23T18:00:00',
    '2026-10-23T18:00:00.123',
    '2026-02-29T18:00:00+09:00',
    '2024-02-30T18:00:00+09:00',
    '1900-02-29T18:00:00Z',
    '2026-04-31T18:00:00+09:00',
    '2026-00-23T18:00:00+09:00',
    '2026-13-23T18:00:00+09:00',
    '2026-10-00T18:00:00+09:00',
    '2026-10-32T18:00:00+09:00',
    '2026-10-23T24:00:00+09:00',
    '2026-10-23T18:60:00+09:00',
    '2026-10-23T18:00:60+09:00',
    '2026-10-23T18:00:00+24:00',
    '2026-10-23T18:00:00-24:00',
    '2026-10-23T18:00:00+09:60',
    '2026-10-23T18:00:00+0900',
    '2026-10-23T18:00:00+09:00junk',
  ];
  for (const field of ['startsAt', 'applicationClosesAt']) {
    for (const value of values) {
      const store = createMainStore(createState, {
        search: async () => ({ content: [{ ...validItem, [field]: value }] }),
      });
      await store.getState().fetchSection('TODAY');
      const section = store.getState().sections.TODAY;
      assert.equal(section.status, 'failed', field + ': ' + value);
      assert.equal(section.data, null);
    }
  }
});

test('UTC·양수·음수 오프셋과 실제 윤년·월말·시각 경계는 허용한다', async () => {
  const values = [
    '2026-10-23T09:00:00Z',
    '2026-10-23T18:00:00+09:00',
    '2026-10-23T05:00:00-04:00',
    '2026-10-23T14:30:00+05:30',
    '2026-10-23T18:00+09:00',
    '2026-10-23T18:00:00.123456789+09:00',
    '2024-02-29T00:00:00Z',
    '2000-02-29T23:59:59+14:00',
    '2026-04-30T23:59:59-03:30',
  ];
  for (const value of values) {
    const item = { ...validItem, startsAt: value, applicationClosesAt: value };
    const store = createMainStore(createState, { search: async () => ({ content: [item] }) });
    await store.getState().fetchSection('TODAY');
    assert.equal(store.getState().sections.TODAY.status, 'succeeded', value);
    assert.deepEqual(store.getState().sections.TODAY.data.content, [item]);
  }
});