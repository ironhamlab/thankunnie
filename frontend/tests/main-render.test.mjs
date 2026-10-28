import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

// SDK 연결 대신 지도에 전달된 실제 마커 props를 검사한다.
const server = await createServer({
  optimizeDeps: { noDiscovery: true, include: [] },
  server: { middlewareMode: true },
  appType: 'custom',
  plugins: [
    {
      name: 'inspect-main-map',
      enforce: 'pre',
      load(id) {
        if (id.endsWith('/components/map/KakaoMap.jsx')) {
          return `import { createElement } from 'react';
          export default function Map({ markers }) {
            return createElement('output', { 'data-markers': JSON.stringify(markers) });
          }`;
        }
      },
    },
  ],
});
after(() => server.close());
const { default: MainSection } = await server.ssrLoadModule(
  '/src/pages/main/components/MainSection.jsx',
);
const { default: MainPage } = await server.ssrLoadModule('/src/pages/main/MainPage.jsx');
const item = {
  id: 101,
  title: '서버 시술',
  shopName: '서버 매장',
  price: 0,
  startsAt: '2026-10-28T18:00:00+09:00',
  applicationClosesAt: null,
  latitude: 37.4982,
  longitude: 127.0276,
};
const render = (section) =>
  renderToStaticMarkup(
    createElement(MainSection, {
      preset: 'TODAY',
      useMainStore: (selector) =>
        selector({ sections: { TODAY: section }, fetchSection: () => {} }),
    }),
  );

test('복원한 메인은 오늘·마감 임박 영역을 함께 렌더링한다', () => {
  const section = { status: 'succeeded', data: { content: [] } };
  const html = renderToStaticMarkup(
    createElement(MainPage, {
      useMainStore: (selector) =>
        selector({
          sections: { TODAY: section, CLOSING_SOON: section },
          fetchAll: () => {},
        }),
    }),
  );
  assert.match(html, /main-TODAY/);
  assert.match(html, /main-CLOSING_SOON/);
});

test('카드는 서버 매장명·0원 판매가를 표시하고 지도에 위도·경도를 전달한다', () => {
  const html = render({ status: 'succeeded', data: { content: [item] } });
  assert.match(html, /서버 매장/);
  assert.match(html, /서버 시술/);
  assert.match(html, /₩0/);
  assert.match(html, /&quot;lat&quot;:37.4982/);
  assert.match(html, /&quot;lng&quot;:127.0276/);
  assert.doesNotMatch(html, /href=/);
});

test('미등록 좌표도 목록에는 남고 마커에서는 제외하며 0 좌표는 유지한다', () => {
  const html = render({
    status: 'succeeded',
    data: {
      content: [
        { ...item, latitude: 0, longitude: 0 },
        { ...item, id: 102, shopName: '좌표 없는 매장', latitude: null, longitude: null },
      ],
    },
  });
  assert.match(html, /좌표 없는 매장/);
  assert.match(html, /위치 정보 준비 중/);
  assert.match(html, /&quot;lat&quot;:0/);
  assert.doesNotMatch(html, /&quot;id&quot;:102/);
});

test('로딩·실패·빈 결과를 구분하며 빈 지도는 생성하지 않는다', () => {
  assert.match(render({ status: 'loading', data: null }), /불러오는 중/);
  const failed = render({ status: 'failed', data: null, error: { message: '조회 실패' } });
  assert.match(failed, /role="alert"/);
  assert.match(failed, /다시 시도/);
  const empty = render({ status: 'succeeded', data: { content: [] } });
  assert.match(empty, /오늘 예약 가능한 빈자리가 없습니다/);
  assert.doesNotMatch(empty, /data-markers/);
});
