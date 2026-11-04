import { useEffect, useRef } from 'react';

// 빈자리는 다른 사람의 예약으로 바뀌므로 화면을 보는 동안 이 간격으로 다시 받는다
const AUTO_REFRESH_MS = 5000;

/**
 * 화면을 보는 동안 값을 주기적으로 다시 받는다. 처음 불러오기는 화면이 직접 한다.
 * 탭을 보고 있지 않으면 요청하지 않고, 탭으로 돌아오거나 enabled 가 다시 켜지면 바로 받는다.
 * 이전 요청이 끝나기 전에는 다시 보내지 않아, 응답이 느려도 요청이 쌓이지 않는다.
 *
 * @param refresh 다시 받는 함수. Promise 를 돌려주면 끝날 때까지 다음 요청을 보내지 않는다
 * @param enabled false 면 멈춘다
 */
export default function useAutoRefresh(refresh, enabled = true) {
  const refreshRef = useRef(refresh);
  useEffect(() => {
    refreshRef.current = refresh;
  });
  const startedRef = useRef(false);

  useEffect(() => {
    if (!enabled) return undefined;
    let inFlight = false;
    const run = async () => {
      if (inFlight || document.visibilityState !== 'visible') return;
      inFlight = true;
      try {
        await refreshRef.current();
      } catch {
        // 다음 주기에 다시 받는다
      } finally {
        inFlight = false;
      }
    };
    // 처음 켜질 때는 화면이 불러오는 중이므로 다시 켜질 때만 바로 받는다
    if (startedRef.current) run();
    startedRef.current = true;
    const timer = window.setInterval(run, AUTO_REFRESH_MS);
    document.addEventListener('visibilitychange', run);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', run);
    };
  }, [enabled]);
}
