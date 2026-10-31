const DEFAULT_BASE_URL = 'http://localhost:8080/api';

/**
 * 스트림 data 가 안 읽은 개수인지 확인한다. 숫자가 아니면 null 이다.
 *
 * @param {string} data 이벤트 data
 * @returns {number | null}
 */
export function readUnreadCount(data) {
  const text = String(data ?? '').trim();
  if (!/^\d+$/.test(text)) {
    return null;
  }
  return Number(text);
}

/**
 * text/event-stream 연결.
 * 브라우저 EventSource는 Authorization 헤더를 넣지 못해서 fetch를 쓴다.
 * 로그인 토큰과 개발용 테스트 계정은 다른 API와 같이 Bearer로 보낸다.
 */
export async function connectEventStream({ baseURL, path, token, signal, onEvent }) {
  const root = (baseURL || DEFAULT_BASE_URL).replace(/\/$/, '');
  try {
    const response = await fetch(`${root}${path}`, {
      method: 'GET',
      headers: {
        // 권한 오류는 JSON이다. event-stream만 받으면 그 오류 본문을 쓰지 못한다.
        Accept: 'text/event-stream, application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      signal,
      cache: 'no-store',
    });

    if (!response.ok) {
      await response.body?.cancel?.();
      const error = new Error('알림 스트림 연결에 실패했습니다.');
      error.status = response.status;
      throw error;
    }
    if (!response.body) {
      throw new Error('알림 스트림 본문이 없습니다.');
    }

    await readEventStream(response.body, onEvent);
  } catch (error) {
    if (error?.name === 'AbortError') {
      return;
    }
    throw error;
  }
}

/**
 * 스트림 본문을 한 줄씩 읽어 빈 줄이 나오면 이벤트 하나로 넘긴다.
 * data가 여러 줄이면 줄바꿈으로 이어 붙인다. ':'로 시작하는 줄은 주석이라 버린다.
 */
export async function readEventStream(stream, onEvent) {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  const current = { eventName: 'message', dataLines: [] };

  const takeLine = (line) => {
    const event = applyLine(line, current);
    if (event) {
      onEvent(event);
    }
  };

  while (true) {
    const { value, done } = await reader.read();
    if (value) {
      buffer += decoder.decode(value, { stream: true });
    }
    if (done) {
      buffer += decoder.decode();
    }

    // \r 로 끝나면 다음 글자가 \n 인지 아직 모른다. 그때는 다음 조각까지 붙잡고 있는다.
    let heldCarriageReturn = '';
    if (!done && buffer.endsWith('\r')) {
      heldCarriageReturn = '\r';
      buffer = buffer.slice(0, -1);
    }
    buffer = buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n');

    let newlineAt = buffer.indexOf('\n');
    while (newlineAt !== -1) {
      takeLine(buffer.slice(0, newlineAt));
      buffer = buffer.slice(newlineAt + 1);
      newlineAt = buffer.indexOf('\n');
    }
    buffer += heldCarriageReturn;

    if (done) {
      if (buffer.length > 0) {
        takeLine(buffer);
      }
      takeLine('');
      return;
    }
  }
}

function applyLine(line, current) {
  if (line === '') {
    if (current.dataLines.length === 0) {
      current.eventName = 'message';
      return null;
    }
    const event = {
      event: current.eventName,
      data: current.dataLines.join('\n'),
    };
    current.eventName = 'message';
    current.dataLines = [];
    return event;
  }

  if (line.startsWith(':')) {
    return null;
  }

  const colonAt = line.indexOf(':');
  const field = colonAt === -1 ? line : line.slice(0, colonAt);
  let value = colonAt === -1 ? '' : line.slice(colonAt + 1);
  if (value.startsWith(' ')) {
    value = value.slice(1);
  }

  if (field === 'data') {
    current.dataLines.push(value);
  } else if (field === 'event' && value) {
    current.eventName = value;
  }
  return null;
}
