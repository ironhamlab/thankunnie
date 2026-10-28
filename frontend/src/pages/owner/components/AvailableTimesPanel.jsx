import { useEffect } from 'react';
import { useScheduleStore } from '@/store/useScheduleStore';
import { timePart } from '@/utils/scheduleDate';
import Panel from '@/components/common/Panel';
import EmptyState from '@/components/common/EmptyState';

/**
 * 선택한 날짜에 고객이 예약할 수 있는 30분 칸. 영업시간·열기·닫기·예약을 서버가 반영해 계산한다.
 */
export default function AvailableTimesPanel({ date, minDate, maxDate, onDateChange, reloadKey }) {
  const loadAvailableTimes = useScheduleStore((state) => state.loadAvailableTimes);
  const availableTimes = useScheduleStore((state) => state.availableTimes);
  const timesStatus = useScheduleStore((state) => state.timesStatus);

  useEffect(() => {
    loadAvailableTimes(date);
  }, [loadAvailableTimes, date, reloadKey]);

  return (
    <Panel as="section" padded>
      <div className="b-row b-panel-heading">
        <h2>날짜별 예약 가능 시간</h2>
        <label>
          <span className="b-visually-hidden">조회 날짜</span>
          <input
            type="date"
            min={minDate}
            max={maxDate}
            value={date}
            onChange={(event) => onDateChange(event.target.value)}
          />
        </label>
      </div>
      <p className="b-muted">
        고객 화면에 보이는 30분 단위 시작 시각입니다. 시작 20분 전이 지난 시각과 예약된 시간은
        빠집니다.
      </p>
      {timesStatus === 'loading' && <p role="status">예약 가능 시간을 불러오는 중입니다.</p>}
      {timesStatus === 'failed' && <p role="alert">예약 가능 시간을 불러오지 못했습니다.</p>}
      {timesStatus === 'succeeded' && !availableTimes.length && (
        <EmptyState as="p">이 날은 예약 가능한 시간이 없습니다.</EmptyState>
      )}
      {timesStatus === 'succeeded' && availableTimes.length > 0 && (
        <div className="b-time-grid">
          {availableTimes.map((time) => (
            <button type="button" key={time} disabled>
              {timePart(time)}
            </button>
          ))}
        </div>
      )}
    </Panel>
  );
}
