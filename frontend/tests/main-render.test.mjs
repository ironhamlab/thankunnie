import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import { createStore } from 'zustand/vanilla';
import { createMainStore } from '../src/store/createMainStore.js';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { createServer } from 'vite';

let server;
let MainItemCard;
let MainPage;
let MainLayout;

before(async () => {
  server = await createServer({
    server: { middlewareMode: true, hmr: false },
    optimizeDeps: { noDiscovery: true, include: [] },
    appType: 'custom',
  });
  MainItemCard = (await server.ssrLoadModule('/src/pages/main/components/MainItemCard.jsx'))
    .default;
  MainPage = (await server.ssrLoadModule('/src/pages/main/MainPage.jsx')).default;
  MainLayout = (await server.ssrLoadModule('/src/components/layout/MainLayout.jsx')).default;
});
after(async () => {
  await server?.close();
});

function render(element) {
  return renderToStaticMarkup(React.createElement(MemoryRouter, null, element));
}

test('카드 전체가 C 상세 링크이며 가격·할인 정상가·예약 상태를 렌더한다', () => {
  const html = render(
    React.createElement(MainItemCard, {
      detailEnabled: true,
      item: {
        id: 101,
        title: '젤 네일',
        shopName: '예시 네일샵',
        price: 35000,
        originalPrice: 50000,
        status: 'AVAILABLE',
        startsAt: '2026-10-23T18:00:00+09:00',
      },
    }),
  );
  assert.match(html, /href="\/items\/101"/);
  assert.match(html, /젤 네일/);
  assert.match(html, /35,000/);
  assert.match(html, /<del[^>]*>.*50,000/);
  assert.match(html, /예약 가능/);
  assert.doesNotMatch(html, /<button/);
});

test('누락 데이터는 확인 안내를 표시하며 잘못된 ID에는 상세 링크를 만들지 않는다', () => {
  const html = render(
    React.createElement(MainItemCard, { detailEnabled: true, item: { title: '확인 필요' } }),
  );
  assert.match(html, /가격 확인 필요/);
  assert.match(html, /상태 확인 필요/);
  assert.match(html, /일정 확인 필요/);
  assert.doesNotMatch(html, /<a\b[^>]*href=/);
});

test('C 화면 미등록 시 탐색 이동을 차단하고 오류·빈 결과와 재시도는 유지한다', () => {
  const state = {
    fetchAll: async () => {},
    fetchSection: async () => {},
    sections: {
      TODAY: { status: 'failed', error: { message: '오늘 조회 실패' } },
      CLOSING_SOON: { status: 'succeeded', data: { content: [] } },
    },
  };
  const html = render(
    React.createElement(MainPage, { useMainStore: (selector) => selector(state) }),
  );
  assert.match(html, /오늘 조회 실패/);
  assert.match(html, /다시 시도/);
  assert.match(html, /24시간 이내/);
  assert.match(html, /조건으로 탐색/);
  assert.match(html, /type="date"/);
  assert.doesNotMatch(html, /<a\b[^>]*href=/);
  assert.match(html, /탐색 화면 준비 중/);
  assert.match(html, /type="submit" disabled=""/);
  assert.doesNotMatch(html, /<main/);
});

test('C 화면 미등록 기본 상태에서는 유효한 카드도 링크 없이 표시한다', () => {
  const html = render(
    React.createElement(MainItemCard, {
      item: {
        id: 101,
        title: '젤 네일',
        price: 35000,
        status: 'AVAILABLE',
        startsAt: '2026-10-23T18:00:00+09:00',
      },
    }),
  );
  assert.match(html, /35,000/);
  assert.match(html, /예약 가능/);
  assert.match(html, /<article/);
  assert.doesNotMatch(html, /<a\b[^>]*href=/);
  assert.doesNotMatch(html, /상세 보기/);
});

test('등록 완료 설정을 주입하면 상세와 탐색 링크 및 조건 선택을 활성화할 수 있다', () => {
  const state = {
    fetchAll: async () => {},
    fetchSection: async () => {},
    sections: {
      TODAY: {
        status: 'succeeded',
        data: { content: [{ id: 101, title: '젤 네일', startsAt: '2026-10-23T18:00:00+09:00' }] },
      },
      CLOSING_SOON: { status: 'succeeded', data: { content: [] } },
    },
  };
  const html = render(
    React.createElement(MainPage, {
      useMainStore: (selector) => selector(state),
      navigationReady: { EXPLORE: true, ITEM_DETAIL: true },
    }),
  );
  assert.match(html, /href="\/items\/101"/);
  assert.match(html, /href="\/explore\?/);
  assert.doesNotMatch(html, /disabled=""/);
  assert.doesNotMatch(html, /탐색 화면 준비 중/);
});

test('기존 디자인 카드에 API 이미지가 연결되고 이미지 누락 시 안내를 표시한다', () => {
  const item = {
    id: 101,
    title: 'API 시술',
    startsAt: '2026-10-23T18:00:00+09:00',
    imageUrl: 'https://example.test/shop.jpg',
  };
  const html = render(React.createElement(MainItemCard, { item }));
  assert.match(html, /b-service-card/);
  assert.match(html, /src="https:\/\/example.test\/shop.jpg"/);
  assert.match(html, /loading="lazy"/);
  assert.doesNotMatch(html, /이미지 준비 중/);
  const missing = render(
    React.createElement(MainItemCard, { item: { ...item, imageUrl: undefined } }),
  );
  assert.match(missing, /이미지 준비 중/);
});

test('기존 디자인 배너와 카드에 실제 메인 store 결과를 연결하고 디자인 시연 데이터는 사용하지 않는다', async () => {
  const requests = [];
  const item = {
    id: 701,
    title: 'API에서 받은 테스트 시술',
    shopName: 'API 매장',
    price: 32500,
    status: 'AVAILABLE',
    startsAt: '2026-10-23T18:00:00+09:00',
  };
  const store = createMainStore(createStore, {
    search: async (params) => {
      requests.push(params);
      return { content: params.startDate ? [item] : [] };
    },
  });
  await store.getState().fetchAll(new Date('2026-10-23T03:00:00Z'));
  const html = render(
    React.createElement(MainPage, { useMainStore: (selector) => selector(store.getState()) }),
  );
  assert.equal(requests.length, 2);
  assert.equal(requests[0].startDate, '2026-10-23');
  assert.equal(requests[1].closingWithinHours, 24);
  assert.match(html, /b-hero/);
  assert.match(html, /뷰티 빈자리/);
  assert.match(html, /API에서 받은 테스트 시술/);
  assert.match(html, /32,500/);
  assert.doesNotMatch(html, /살롱 드 르네/);
  assert.doesNotMatch(html, /<a\b[^>]*href=/);
});

test('공통 헤더·푸터도 디자인 테마를 사용하며 시연용 고객·예약 링크는 노출하지 않는다', () => {
  const html = render(React.createElement(MainLayout));
  assert.match(html, /땡잡은 언니/);
  assert.match(html, /beauty-app b-layout/);
  assert.match(html, /href="\/login"/);
  assert.equal((html.match(/<main\b/g) || []).length, 1);
  assert.doesNotMatch(html, /href="\/design/);
  assert.doesNotMatch(html, /테스트.*고객 계정/);
  assert.match(html, /aria-label="검색 기능 준비 중" disabled=""/);
});

test('예약 마감이 없으면 확인 안내를 표시하고 유효한 마감 날짜는 그대로 표시한다', () => {
  const item = { id: 101, title: '젤 네일', startsAt: '2026-10-23T18:00:00+09:00' };
  for (const applicationClosesAt of [undefined, null]) {
    const html = render(React.createElement(MainItemCard, { item: { ...item, applicationClosesAt } }));
    assert.match(html, /예약 마감 일정 확인 필요/);
  }
  const html = render(React.createElement(MainItemCard, { item: { ...item, applicationClosesAt: '2026-10-23T17:00:00+09:00' } }));
  assert.match(html, /예약 마감 10월 23일 오후 05:00/);
  assert.doesNotMatch(html, /예약 마감 일정 확인 필요/);
});