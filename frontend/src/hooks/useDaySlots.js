import { useEffect, useState } from 'react';
import { useScheduleStore } from '@/store/useScheduleStore';
import { buildDaySlots } from '@/utils/scheduleSlots';

/**
 * 그날의 30분 칸 상태 (예약 가능·예약됨·닫음·마감). 열기·닫기 시간 선택에 쓴다.
 * 영업시간·일정은 화면이 불러오고, 이 훅은 일정이 바뀔 때마다(닫기 삭제 등) 그날 가능 시간을 다시 받는다.
 *
 * @return ready 그날 칸을 한 번이라도 받았는지, fresh 지금 일정 기준으로 받은 칸인지,
 *         failed 가능 시간 조회에 실패했는지 (실패하면 칸을 비우고 retry 로 다시 받는다)
 */
export default function useDaySlots(date) {
  const status = useScheduleStore((state) => state.status);
  const fetchAvailableTimes = useScheduleStore((state) => state.fetchAvailableTimes);
  const businessHours = useScheduleStore((state) => state.businessHours);
  const exceptions = useScheduleStore((state) => state.exceptions);
  // 가능 시간을 받을 때의 일정을 같이 둔다. 일정만 먼저 바뀐 순간에 둘을 섞으면 빈 칸이 예약됨으로 잘못 보인다
  const [result, setResult] = useState({ date: null, startTimes: [], source: [], failed: false });
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    if (status !== 'succeeded') {
      return undefined;
    }
    // 날짜를 빠르게 바꾸면 늦게 온 이전 날짜 응답을 버린다
    let ignore = false;
    fetchAvailableTimes(date)
      .then(
        (startTimes) =>
          !ignore && setResult({ date, startTimes, source: exceptions, failed: false }),
      )
      .catch(
        () => !ignore && setResult({ date, startTimes: [], source: exceptions, failed: true }),
      );
    return () => {
      ignore = true;
    };
  }, [status, fetchAvailableTimes, date, exceptions, retryKey]);

  const ready = result.date === date;
  // 가능 시간을 못 받으면 영업 중인 칸이 전부 예약됨으로 추론되므로 칸을 만들지 않는다
  const failed = ready && result.failed;
  return {
    slots:
      ready && !failed
        ? buildDaySlots({
            date,
            businessHours,
            exceptions: result.source,
            availableTimes: result.startTimes,
          })
        : [],
    ready,
    fresh: ready && result.source === exceptions,
    failed,
    retry: () => setRetryKey((key) => key + 1),
    exceptions,
  };
}
