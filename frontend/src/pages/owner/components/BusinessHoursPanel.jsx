import { useState } from 'react';
import { useScheduleStore } from '@/store/useScheduleStore';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import Alert from '@/components/common/Alert';
import { DAY_LABELS } from '@/utils/scheduleSlots';
import { TIME_OPTIONS, toMinutes, toTime } from '@/utils/vacancyPreview';
import WeekHoursSummary from '@/pages/owner/components/WeekHoursSummary';
import BusinessHoursTips from '@/pages/owner/components/BusinessHoursTips';
import ClosingImpactDialog from '@/pages/owner/components/ClosingImpactDialog';
import styles from './BusinessHoursPanel.module.css';
import motion from './OwnerMotion.module.css';

const DEFAULT_OPEN = '10:00';
const DEFAULT_CLOSE = '20:00';
// 자정을 넘기는 영업은 받지 않아 종료는 23:30 까지, 시작은 종료 칸이 남도록 23:00 까지 고른다
const LAST_OPEN = '23:00';
const OPEN_OPTIONS = TIME_OPTIONS.filter((time) => time <= LAST_OPEN);

// 30분 단위가 아닌 예전 값도 그대로 보이도록 목록에 끼워 넣는다
const withCurrent = (options, current) =>
  !current || options.includes(current) ? options : [...options, current].sort();

/**
 * 요일별 영업시간·휴무 설정. 요일마다 한 줄로 영업·휴무와 30분 단위 시작~종료를 고른다.
 * 영업하는 요일의 예약이 걸린 시간을 줄이면 서버가 거절한다. 저장하면 예약 가능 시간이 바뀌므로 onSaved 로 부모에게 알린다.
 * 요일을 휴무로 바꿀 때 그 요일에 예약이나 연 시간이 있으면 저장 전에 확인창으로 알린다.
 */
export default function BusinessHoursPanel({ hours, onSaved }) {
  const saveBusinessHours = useScheduleStore((state) => state.saveBusinessHours);
  const fetchClosingImpact = useScheduleStore((state) => state.fetchClosingImpact);
  const isSaving = useScheduleStore((state) => state.mutationStatus === 'loading');
  const [days, setDays] = useState(hours);
  const [message, setMessage] = useState(null);
  // 확인창에 보여줄 내용. { labels, impact }
  const [closing, setClosing] = useState(null);
  const [isChecking, setIsChecking] = useState(false);
  const isBusy = isSaving || isChecking;
  // 영업·휴무를 바꾼 요일만 시간 칸이 나타나는 효과를 준다 (처음 불러올 때는 움직이지 않게)
  const [toggledDays, setToggledDays] = useState(() => new Set());

  const handleDayChange = (dayOfWeek, key, value) => {
    setMessage(null);
    setDays((prev) =>
      prev.map((day) => {
        if (day.dayOfWeek !== dayOfWeek) {
          return day;
        }
        const next = { ...day, [key]: value };
        // 시작을 종료 이후로 옮기면 종료를 시작 30분 뒤로 따라 옮겨 잘못된 구간이 생기지 않게 한다
        if (key === 'openTime' && next.closeTime <= value) {
          next.closeTime = toTime(toMinutes(value) + 30);
        }
        return next;
      }),
    );
  };

  const enterOf = (day) => (toggledDays.has(day.dayOfWeek) ? motion.enter : '');

  const handleClosedChange = (day, closed) => {
    setMessage(null);
    setToggledDays((prev) => new Set(prev).add(day.dayOfWeek));
    setDays((prev) =>
      prev.map((item) =>
        item.dayOfWeek === day.dayOfWeek
          ? {
              ...item,
              closed,
              openTime: item.openTime ?? DEFAULT_OPEN,
              closeTime: item.closeTime ?? DEFAULT_CLOSE,
            }
          : item,
      ),
    );
  };

  const save = async (closeOpenExceptions, keptReservations) => {
    const payload = days.map(({ dayOfWeek, closed, openTime, closeTime }) => ({
      dayOfWeek,
      closed,
      openTime: closed ? null : openTime,
      closeTime: closed ? null : closeTime,
    }));
    try {
      await saveBusinessHours(payload, closeOpenExceptions);
      setMessage({
        type: 'success',
        text:
          keptReservations > 0
            ? `영업시간을 저장했습니다. 남은 예약 ${keptReservations}건은 그대로 유지됩니다.`
            : '영업시간을 저장했습니다.',
      });
      onSaved?.();
    } catch (error) {
      setMessage({ type: 'error', text: error.message });
    } finally {
      setClosing(null);
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (isBusy) return;
    // HH:mm 문자열은 사전순 비교가 시각 순서와 같다
    const invalidDay = days.find((day) => !day.closed && day.closeTime <= day.openTime);
    if (invalidDay) {
      setMessage({
        type: 'error',
        text: `${DAY_LABELS[invalidDay.dayOfWeek]}요일 영업 종료는 시작보다 뒤여야 합니다.`,
      });
      return;
    }
    const newlyClosed = days.filter(
      (day) => day.closed && !hours.find((saved) => saved.dayOfWeek === day.dayOfWeek)?.closed,
    );
    if (!newlyClosed.length) {
      await save(false, 0);
      return;
    }
    setMessage(null);
    setIsChecking(true);
    try {
      const impact = await fetchClosingImpact(newlyClosed.map((day) => day.dayOfWeek));
      if (impact.reservations.length || impact.openExceptions.length) {
        setClosing({
          labels: newlyClosed.map((day) => DAY_LABELS[day.dayOfWeek]).join(', '),
          impact,
        });
      } else {
        await save(false, 0);
      }
    } catch (error) {
      setMessage({ type: 'error', text: error.message });
    } finally {
      setIsChecking(false);
    }
  };

  return (
    <div className={styles.layout}>
      <Panel as="section" padded>
        <div className="b-row b-panel-heading">
          <h2>요일별 영업시간</h2>
          <small className="b-muted">30분 단위 · 자정을 넘기는 영업은 지원하지 않습니다</small>
        </div>
        <form className="b-stack" onSubmit={handleSubmit} aria-busy={isBusy}>
          <ul className={styles.days}>
            {days.map((day) => {
              const label = DAY_LABELS[day.dayOfWeek];
              const closeOptions = TIME_OPTIONS.filter((time) => time > day.openTime);
              return (
                <li key={day.dayOfWeek} className={styles.day}>
                  <strong className={styles.label}>{label}</strong>
                  <div
                    className={`b-options ${styles.toggle}`}
                    role="group"
                    aria-label={`${label}요일 영업 여부`}
                  >
                    {[
                      [false, '영업'],
                      [true, '휴무'],
                    ].map(([closed, text]) => (
                      <button
                        key={text}
                        type="button"
                        aria-pressed={day.closed === closed}
                        className={day.closed === closed ? 'is-selected' : ''}
                        onClick={() => handleClosedChange(day, closed)}
                      >
                        {text}
                      </button>
                    ))}
                  </div>
                  {day.closed ? (
                    <span className={`${styles.off} ${enterOf(day)}`}>쉬는 날</span>
                  ) : (
                    <div className={`${styles.times} ${enterOf(day)}`}>
                      <select
                        aria-label={`${label}요일 영업 시작`}
                        value={day.openTime}
                        onChange={(event) =>
                          handleDayChange(day.dayOfWeek, 'openTime', event.target.value)
                        }
                      >
                        {withCurrent(OPEN_OPTIONS, day.openTime).map((time) => (
                          <option key={time} value={time}>
                            {time}
                          </option>
                        ))}
                      </select>
                      <span aria-hidden="true">~</span>
                      <select
                        aria-label={`${label}요일 영업 종료`}
                        value={day.closeTime}
                        onChange={(event) =>
                          handleDayChange(day.dayOfWeek, 'closeTime', event.target.value)
                        }
                      >
                        {withCurrent(closeOptions, day.closeTime).map((time) => (
                          <option key={time} value={time}>
                            {time}
                          </option>
                        ))}
                      </select>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
          {message && (
            <Alert
              key={message.text}
              className={motion.enter}
              as="p"
              tone={message.type === 'success' ? 'success' : 'error'}
              role={message.type === 'success' ? 'status' : 'alert'}
            >
              {message.text}
            </Alert>
          )}
          <Button variant="primary" disabled={isBusy}>
            영업시간 저장
          </Button>
        </form>
      </Panel>
      {closing && (
        <ClosingImpactDialog
          labels={closing.labels}
          impact={closing.impact}
          isSaving={isSaving}
          onCancel={() => {
            if (!isSaving) setClosing(null);
          }}
          onConfirm={(closeOpens) => save(closeOpens, closing.impact.reservations.length)}
        />
      )}
      <div className={styles.side}>
        <WeekHoursSummary days={days} />
        <BusinessHoursTips />
      </div>
    </div>
  );
}
