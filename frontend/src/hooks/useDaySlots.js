import { useEffect, useState } from 'react';
import { useScheduleStore } from '@/store/useScheduleStore';
import { buildDaySlots } from '@/utils/scheduleSlots';

/**
 * 그날의 30분 칸 상태 (예약 가능·예약됨·닫음·마감). 열기·닫기 시간 선택에 쓴다.
 * 영업시간·일정을 아직 안 불러왔으면 먼저 불러오고, 일정이 바뀌면(닫기 삭제 등) 가능 시간을 다시 받는다.
 */
export default function useDaySlots(date) {
  const status = useScheduleStore((state) => state.status);
  const loadSchedule = useScheduleStore((state) => state.loadSchedule);
  const fetchAvailableTimes = useScheduleStore((state) => state.fetchAvailableTimes);
  const businessHours = useScheduleStore((state) => state.businessHours);
  const exceptions = useScheduleStore((state) => state.exceptions);
  // 가능 시간을 받을 때의 일정을 같이 둔다. 일정만 먼저 바뀐 순간에 둘을 섞으면 빈 칸이 예약됨으로 잘못 보인다
  const [result, setResult] = useState({ date: null, startTimes: [], source: [], failed: false });

  useEffect(() => {
    if (status === 'idle') {
      loadSchedule();
    }
  }, [status, loadSchedule]);

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
  }, [status, fetchAvailableTimes, date, exceptions]);

  const ready = result.date === date;
  return {
    slots: ready
      ? buildDaySlots({
          date,
          businessHours,
          exceptions: result.source,
          availableTimes: result.startTimes,
        })
      : [],
    ready,
    failed: ready && result.failed,
    exceptions,
  };
}
