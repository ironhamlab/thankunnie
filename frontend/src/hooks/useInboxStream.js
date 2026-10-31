import { useEffect, useRef } from 'react';
import axiosInstance from '@/api/axiosInstance';
import { connectEventStream, readUnreadCount } from '@/api/eventStream';
import { useAuthStore } from '@/store/useAuthStore';
import { useInboxStore } from '@/store/useInboxStore';
import { getDevTestUser } from '@/utils/devTestUser';

const INBOX_STREAM_PATH = '/v1/notifications/inbox/stream';
const RECONNECT_WAIT_MS = 3000;

function wait(ms, signal) {
  return new Promise((resolve) => {
    if (signal?.aborted) {
      resolve();
      return;
    }
    const finish = () => {
      window.clearTimeout(timerId);
      signal?.removeEventListener('abort', finish);
      resolve();
    };
    const timerId = window.setTimeout(finish, ms);
    signal?.addEventListener('abort', finish);
  });
}

/**
 * 로그인한 고객이고 탭이 보일 때만 알림 스트림에 연결한다.
 * 받은 숫자로 뱃지를 바꾸고, 종을 열어 둔 상태면 목록도 다시 가져온다.
 * 탭을 숨기면 끊고, 다시 보이면 다시 연결한다.
 */
export function useInboxStream(enabled, popoverOpen) {
  const applyUnreadCount = useInboxStore((state) => state.applyUnreadCount);
  const loadInbox = useInboxStore((state) => state.loadInbox);
  const clear = useInboxStore((state) => state.clear);
  const token = useAuthStore((state) => state.token);
  const popoverOpenRef = useRef(popoverOpen);
  popoverOpenRef.current = popoverOpen;

  useEffect(() => {
    if (!enabled) {
      clear();
      return undefined;
    }

    let connectionId = 0;
    let controller = null;

    const stop = () => {
      connectionId += 1;
      if (controller == null) {
        return;
      }
      controller.abort();
      controller = null;
    };

    const start = () => {
      stop();
      if (document.visibilityState !== 'visible') {
        return;
      }
      const myId = connectionId;

      const run = async () => {
        while (myId === connectionId && document.visibilityState === 'visible') {
          const current = new AbortController();
          controller = current;
          try {
            await connectEventStream({
              baseURL: axiosInstance.defaults.baseURL,
              path: INBOX_STREAM_PATH,
              token: useAuthStore.getState().token ?? getDevTestUser(),
              signal: current.signal,
              onEvent: (event) => {
                const count = readUnreadCount(event.data);
                if (count == null) {
                  return;
                }
                applyUnreadCount(count);
                if (popoverOpenRef.current) {
                  loadInbox();
                }
              },
            });
          } catch (error) {
            if (myId !== connectionId || current.signal.aborted) {
              return;
            }
            // 권한 오류는 다시 붙이지 않는다. 그 외에는 마지막 숫자를 유지한 채 잠시 뒤 다시 연결한다.
            if (error?.status === 401 || error?.status === 403) {
              return;
            }
            await wait(RECONNECT_WAIT_MS, current.signal);
            continue;
          }
          if (myId !== connectionId || current.signal.aborted) {
            return;
          }
        }
      };

      run();
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        start();
        return;
      }
      stop();
    };

    start();
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      stop();
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [enabled, token, applyUnreadCount, loadInbox, clear]);
}
