import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { createServer } from 'vite';

let server;
let MainItemCard;
let MainPage;

before(async () => {
  server = await createServer({
    server: { middlewareMode: true, hmr: false },
    optimizeDeps: { noDiscovery: true, include: [] },
    appType: 'custom',
  });
  MainItemCard = (await server.ssrLoadModule('/src/pages/main/components/MainItemCard.jsx'))
    .default;
  MainPage = (await server.ssrLoadModule('/src/pages/main/MainPage.jsx')).default;
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
  const html = render(React.createElement(MainItemCard, { item: { title: '확인 필요' } }));
  assert.match(html, /가격 확인 필요/);
  assert.match(html, /상태 확인 필요/);
  assert.match(html, /일정 확인 필요/);
  assert.doesNotMatch(html, /href=/);
});

test('영역별 오류·빈 결과를 독립적으로 렌더하고 더보기와 조건 선택을 제공한다', () => {
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
  assert.match(html, /href="\/explore\?/);
  assert.doesNotMatch(html, /<main/);
});
