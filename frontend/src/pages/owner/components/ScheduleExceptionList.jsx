import { useState } from 'react';
import { datePart, timePart, todayKst } from '@/utils/scheduleDate';
import { clipToDate, dayHoursOf, formatDayLabel, overlappingCloses } from '@/utils/scheduleSlots';
import { toMinutes, toTime } from '@/utils/vacancyPreview';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import EmptyState from '@/components/common/EmptyState';
import styles from './ScheduleExceptionList.module.css';
import motion from './OwnerMotion.module.css';

const TYPE_BADGES = {
  OPEN: ['열기', 'b-green'],
  CLOSE: ['닫기', 'b-neutral'],
};

// 시작 시각 순으로 온 목록을 날짜별로 묶는다
const groupByDate = (exceptions) => {
  const groups = new Map();
  exceptions.forEach((exception) => {
    const date = datePart(exception.startsAt);
    groups.set(date, [...(groups.get(date) ?? []), exception]);
  });
  return [...groups.entries()];
};

/**
 * 그날 영업시간과 일정 범위를 한 줄 막대로 그린다. 닫기는 열기 위에 칠해 닫기 우선을 그대로 보여준다.
 */
function DayBar({ date, hours, items }) {
  const ranges = items.map((item) => ({
    id: item.exceptionId,
    type: item.exceptionType,
    // 날짜별로 묶어 그날 시작한 일정이므로 늘 그날 몫이 있다
    start: clipToDate(item, date)[0],
    end: clipToDate(item, date)[1],
  }));
  const business = hours && { start: toMinutes(hours.openTime), end: toMinutes(hours.closeTime) };
  const all = business ? [...ranges, business] : ranges;
  const from = Math.min(...all.map((range) => range.start));
  const to = Math.max(...all.map((range) => range.end));
  const position = ({ start, end }) => ({
    left: `${((start - from) / (to - from)) * 100}%`,
    width: `${((end - start) / (to - from)) * 100}%`,
  });
  const hourLabel = (minutes) => `${Math.floor(minutes / 60)}시`;
  // 열기를 먼저, 닫기를 나중에 그려 겹친 곳은 닫기 색이 보이게 한다
  const ordered = [
    ...ranges.filter((range) => range.type === 'OPEN'),
    ...ranges.filter((range) => range.type === 'CLOSE'),
  ];

  return (
    <div aria-hidden="true">
      <div className={styles.bar}>
        {business && (
          <span className={`${styles.segment} ${styles.business}`} style={position(business)} />
        )}
        {ordered.map((range) => (
          <span
            key={range.id}
            className={`${styles.segment} ${styles[range.type.toLowerCase()]}`}
            style={position(range)}
          />
        ))}
      </div>
      <div className={styles.axis}>
        <span>{hourLabel(from)}</span>
        <span>{hourLabel(to)}</span>
      </div>
    </div>
  );
}

/**
 * 등록한 빈자리 열기·닫기 일정 (오늘부터 60일)을 날짜별로 묶어 보여준다.
 * 날짜를 누르면 등록 탭이 그 날짜로, 수정을 누르면 그 일정으로 열린다.
 * 사유는 기본으로 접어 두고 시간 옆 ▼ 로 펼친다. 닫기와 겹친 열기 경고는 늘 보여준다.
 * leavingId 일정은 지우기 직전이라 사라지는 효과를 준다.
 */
export default function ScheduleExceptionList({
  className = '',
  exceptions,
  businessHours,
  editingId,
  leavingId,
  disabled,
  onSelectDate,
  onEdit,
  onDelete,
}) {
  const today = todayKst();
  const [openReasonIds, setOpenReasonIds] = useState(() => new Set());
  const toggleReason = (exceptionId) =>
    setOpenReasonIds((prev) => {
      const next = new Set(prev);
      if (!next.delete(exceptionId)) {
        next.add(exceptionId);
      }
      return next;
    });

  return (
    <Panel as="section" padded className={`${styles.root} ${className}`}>
      <h2>
        열기·닫기 일정 <small className="b-muted">(오늘부터 60일, {exceptions.length}건)</small>
      </h2>
      {!exceptions.length && <EmptyState as="p">등록된 열기·닫기 일정이 없습니다.</EmptyState>}
      {groupByDate(exceptions).map(([date, items]) => {
        const hours = dayHoursOf(businessHours, date);
        return (
          <article key={date} className={styles.day}>
            <header className={styles.dayHeader}>
              <button type="button" className={styles.date} onClick={() => onSelectDate(date)}>
                {formatDayLabel(date)}
              </button>
              <small className="b-muted">
                {date === today && '오늘 · '}
                {hours ? `영업 ${hours.openTime}~${hours.closeTime}` : '휴무일'}
              </small>
            </header>
            <DayBar date={date} hours={hours} items={items} />
            <ul className={styles.rows}>
              {items.map((exception) => {
                const [label, badge] = TYPE_BADGES[exception.exceptionType];
                // 전날부터 이어진 닫기도 찾도록 그날 묶음이 아니라 전체 일정과 비교하고, 다음 날 0시 종료는 24:00 으로 본다
                const [rangeStart, rangeEnd] = clipToDate(exception, date);
                const blocked =
                  exception.exceptionType === 'OPEN' &&
                  overlappingCloses(exceptions, date, toTime(rangeStart), toTime(rangeEnd)).length >
                    0;
                const reasonOpen =
                  Boolean(exception.reason) && openReasonIds.has(exception.exceptionId);
                return (
                  <li
                    key={exception.exceptionId}
                    className={[
                      exception.exceptionId === editingId && styles.editing,
                      exception.exceptionId === leavingId && motion.leaving,
                    ]
                      .filter(Boolean)
                      .join(' ')}
                  >
                    <Badge className={`${badge} ${styles.badge}`}>{label}</Badge>
                    <span className={styles.time}>
                      <strong>
                        {timePart(exception.startsAt)}~{timePart(exception.endsAt)}
                      </strong>
                      {exception.reason && (
                        <button
                          type="button"
                          className={styles.toggle}
                          aria-expanded={reasonOpen}
                          aria-label={reasonOpen ? '사유 접기' : '사유 보기'}
                          onClick={() => toggleReason(exception.exceptionId)}
                        >
                          {reasonOpen ? '▲' : '▼'}
                        </button>
                      )}
                    </span>
                    <span className={styles.actions}>
                      <Button
                        size="small"
                        type="button"
                        disabled={disabled}
                        onClick={() => onEdit(exception)}
                      >
                        수정
                      </Button>
                      <Button
                        size="small"
                        type="button"
                        disabled={disabled}
                        onClick={() => onDelete(exception)}
                      >
                        삭제
                      </Button>
                    </span>
                    {blocked && (
                      <span className={styles.warning}>닫기가 우선이라 예약을 받지 않아요</span>
                    )}
                    {!blocked && reasonOpen && (
                      <>
                        <span className={styles.reasonLabel}>사유:</span>
                        <span className={styles.reason}>{exception.reason}</span>
                      </>
                    )}
                  </li>
                );
              })}
            </ul>
          </article>
        );
      })}
    </Panel>
  );
}
