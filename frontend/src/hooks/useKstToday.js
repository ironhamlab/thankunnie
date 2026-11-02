import { useEffect, useState } from 'react';
import { msUntilNextKstMidnight, todayKst } from '@/utils/scheduleDate';

// 타이머가 자정 직전에 깨어나 날짜가 그대로면 다음 타이머가 걸리지 않으므로 조금 늦게 깨운다
const MIDNIGHT_MARGIN_MS = 1000;

/**
 * 오늘 날짜(YYYY-MM-DD, 한국 시간). 화면을 열어 둔 채 자정이 지나면 새 날짜로 다시 렌더링한다.
 * 기기가 잠들면 타이머가 늦어지므로 탭이 다시 보일 때도 날짜를 확인한다.
 */
export function useKstToday() {
  const [today, setToday] = useState(todayKst);

  useEffect(() => {
    const timer = window.setTimeout(
      () => setToday(todayKst()),
      msUntilNextKstMidnight() + MIDNIGHT_MARGIN_MS,
    );
    return () => window.clearTimeout(timer);
  }, [today]);

  useEffect(() => {
    const handleVisible = () => {
      if (document.visibilityState === 'visible') {
        setToday(todayKst());
      }
    };
    document.addEventListener('visibilitychange', handleVisible);
    return () => document.removeEventListener('visibilitychange', handleVisible);
  }, []);

  return today;
}
