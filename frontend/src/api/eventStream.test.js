import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import { connectEventStream, readEventStream, readUnreadCount } from './eventStream.js';

const originalFetch = globalThis.fetch;

afterEach(() => {
  globalThis.fetch = originalFetch;
});

function streamFrom(chunks) {
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(typeof chunk === 'string' ? new TextEncoder().encode(chunk) : chunk);
      }
      controller.close();
    },
  });
}

test('안 읽은 개수는 숫자만 받는다', () => {
  assert.equal(readUnreadCount('2'), 2);
  assert.equal(readUnreadCount('0'), 0);
  assert.equal(readUnreadCount(' 4 '), 4);
  assert.equal(readUnreadCount(''), null);
  assert.equal(readUnreadCount('2건'), null);
});

test('data 한 줄을 이벤트 하나로 읽는다', async () => {
  const events = [];
  await readEventStream(streamFrom(['data: 2\n\n']), (event) => events.push(event));
  assert.deepEqual(events, [{ event: 'message', data: '2' }]);
});

test('이벤트 이름과 여러 줄 data를 이어 붙인다', async () => {
  const events = [];
  await readEventStream(streamFrom(['event: unread\ndata: 첫줄\ndata: 둘째줄\n\n']), (event) =>
    events.push(event),
  );
  assert.deepEqual(events, [{ event: 'unread', data: '첫줄\n둘째줄' }]);
});

test('줄이 청크 중간에서 끊겨도 한 이벤트로 합친다', async () => {
  const events = [];
  const bytes = new TextEncoder().encode('data: 알림\n\n');
  await readEventStream(streamFrom([bytes.slice(0, 7), bytes.slice(7)]), (event) =>
    events.push(event),
  );
  assert.deepEqual(events, [{ event: 'message', data: '알림' }]);
});

test('주석과 빈 data는 이벤트로 넘기지 않는다', async () => {
  const events = [];
  await readEventStream(streamFrom([': keep-alive\n\ndata: 1\n\n']), (event) => events.push(event));
  assert.deepEqual(events, [{ event: 'message', data: '1' }]);
});

test('CRLF가 청크 경계에서 나뉘어도 한 줄로 읽는다', async () => {
  const events = [];
  await readEventStream(streamFrom(['data: 5\r', '\n\r\n']), (event) => events.push(event));
  assert.deepEqual(events, [{ event: 'message', data: '5' }]);
});

test('CRLF로 구분된 이벤트도 읽는다', async () => {
  const events = [];
  await readEventStream(streamFrom(['data: 3\r\n\r\ndata: 4\r\n\r\n']), (event) =>
    events.push(event),
  );
  assert.deepEqual(events, [
    { event: 'message', data: '3' },
    { event: 'message', data: '4' },
  ]);
});

test('Bearer 토큰을 Authorization 헤더에 넣어 연결한다', async () => {
  let seen = null;
  globalThis.fetch = async (url, options) => {
    seen = { url, headers: options.headers, cache: options.cache };
    return { ok: true, body: streamFrom(['data: 1\n\n']) };
  };

  const events = [];
  await connectEventStream({
    baseURL: 'http://localhost:8080/api/',
    path: '/v1/notifications/inbox/stream',
    token: 'CUSTOMER_1',
    onEvent: (event) => events.push(event),
  });

  assert.equal(seen.url, 'http://localhost:8080/api/v1/notifications/inbox/stream');
  assert.equal(seen.headers.Authorization, 'Bearer CUSTOMER_1');
  assert.equal(seen.headers.Accept, 'text/event-stream, application/json');
  assert.equal(seen.cache, 'no-store');
  assert.deepEqual(events, [{ event: 'message', data: '1' }]);
});

test('토큰이 없으면 Authorization 헤더를 넣지 않는다', async () => {
  let headers = null;
  globalThis.fetch = async (_url, options) => {
    headers = options.headers;
    return { ok: true, body: streamFrom(['data: 0\n\n']) };
  };

  await connectEventStream({
    baseURL: 'http://localhost:8080/api',
    path: '/v1/notifications/inbox/stream',
    onEvent: () => {},
  });

  assert.equal(headers.Authorization, undefined);
});

test('연결이 실패하면 상태 코드를 담아 던지고 이벤트는 없다', async () => {
  globalThis.fetch = async () => ({ ok: false, status: 401, body: streamFrom(['data: 1\n\n']) });
  const events = [];

  await assert.rejects(
    () =>
      connectEventStream({
        baseURL: 'http://localhost:8080/api',
        path: '/v1/notifications/inbox/stream',
        token: 'CUSTOMER_1',
        onEvent: (event) => events.push(event),
      }),
    (error) => error.status === 401,
  );
  assert.equal(events.length, 0);
});

test('연결을 끊으면 오류로 올리지 않는다', async () => {
  const controller = new AbortController();
  globalThis.fetch = (_url, options) =>
    new Promise((_resolve, reject) => {
      options.signal.addEventListener('abort', () => {
        const error = new Error('aborted');
        error.name = 'AbortError';
        reject(error);
      });
    });

  const pending = connectEventStream({
    baseURL: 'http://localhost:8080/api',
    path: '/v1/notifications/inbox/stream',
    token: 'CUSTOMER_1',
    signal: controller.signal,
    onEvent: () => {},
  });
  controller.abort();
  await pending;
});
