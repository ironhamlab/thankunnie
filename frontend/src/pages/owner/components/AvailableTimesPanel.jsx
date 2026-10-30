import { useEffect } from 'react';
import { useScheduleStore } from '@/store/useScheduleStore';
import { buildDaySlots } from '@/utils/scheduleSlots';
import Panel from '@/components/common/Panel';
import EmptyState from '@/components/common/EmptyState';
import styles from './AvailableTimesPanel.module.css';

// 칸 상태별 표시. 색만으로 구분하지 않도록 안 되는 칸에는 이유 글자를 같이 쓴다
const SLOT_STATUS = {
  AVAILABLE: { label: '예약 가능', className: styles.available },
  BOOKED: { label: '예약됨', className: styles.booked },
  CLOSED: { label: '닫음', className: styles.closed },
  PASSED: { label: '마감', className: styles.passed },
  OFF: { label: '영업 외', className: styles.off },
};

/**
 * 선택한 날짜의 30분 칸별 예약 가능 여부 (B-F3). 가능한 칸은 서버가 영업시간·열기·닫기·예약을 반영해 계산한 값이고,
 * 안 되는 칸은 영업시간·일정으로 이유(닫음·예약됨·마감)를 구분해 보여준다.
 */
export default function AvailableTimesPanel({ date, minDate, maxDate, onDateChange }) {
  const loadAvailableTimes = useScheduleStore((state) => state.loadAvailableTimes);
  const availableTimes = useScheduleStore((state) => state.availableTimes);
  const timesStatus = useScheduleStore((state) => state.timesStatus);
  const businessHours = useScheduleStore((state) => state.businessHours);
  const exceptions = useScheduleStore((state) => state.exceptions);
  // 매장 ID 는 영업시간·일정을 불러오며 정해진다. 그 전에 부르면 shopId 없이 요청이 나간다
  const scheduleReady = useScheduleStore((state) => state.status === 'succeeded');

  // 열기·닫기가 바뀌면(창에서 닫기 지우기 등) 가능 시간도 다시 받는다. 일정과 가능 시간이 어긋나면
  // 안 되는 이유를 잘못 추론해 빈 칸을 예약됨으로 보이게 된다
  useEffect(() => {
    if (scheduleReady) {
      loadAvailableTimes(date);
    }
  }, [loadAvailableTimes, scheduleReady, date, exceptions]);

  const slots =
    timesStatus === 'succeeded'
      ? buildDaySlots({ date, businessHours, exceptions, availableTimes })
      : [];
  const counts = slots.reduce(
    (acc, slot) => ({ ...acc, [slot.status]: (acc[slot.status] ?? 0) + 1 }),
    {},
  );

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
        30분 단위 시작 시각입니다. 초록 칸은 30분 시술 기준으로 지금 예약할 수 있고, 긴 시술은
        이어지는 칸이 모두 비어 있어야 예약됩니다. 마감은 시작 20분 전이 지난 칸입니다.
      </p>
      {timesStatus === 'loading' && <p role="status">예약 가능 시간을 불러오는 중입니다.</p>}
      {timesStatus === 'failed' && <p role="alert">예약 가능 시간을 불러오지 못했습니다.</p>}
      {timesStatus === 'succeeded' && !slots.length && (
        <EmptyState as="p">이 날은 영업하지 않습니다.</EmptyState>
      )}
      {slots.length > 0 && (
        <>
          <ul className={styles.legend} aria-label="칸 상태 범례">
            {Object.entries(SLOT_STATUS)
              .filter(([status]) => counts[status])
              .map(([status, { label, className }]) => (
                <li key={status}>
                  <span className={`${styles.swatch} ${className}`} aria-hidden="true" />
                  {label} {counts[status]}
                </li>
              ))}
          </ul>
          <ul className={styles.grid} aria-label="30분 칸별 예약 가능 여부">
            {slots.map(({ time, status }) => {
              const { label, className } = SLOT_STATUS[status];
              return (
                <li key={time} className={`${styles.chip} ${className}`} title={`${time} ${label}`}>
                  {time}
                  {status !== 'AVAILABLE' && <small>{label}</small>}
                </li>
              );
            })}
          </ul>
        </>
      )}
    </Panel>
  );
}
