import { useState } from 'react';
import { ERROR_MESSAGES } from '@/constants/errorMessages';
import { addDays, toKstDateTime, todayKst } from '@/utils/scheduleDate';
import Button from '@/components/common/Button';
import styles from './SubscriptionCreateForm.module.css';

const HOPE_DAYS = 30;
const toStamp = (date, time) => `${date}T${time}`;

const emptyTimeForm = () => {
  const startDate = todayKst();
  return {
    startDate,
    startTime: '14:00',
    endDate: addDays(startDate, 2),
    endTime: '20:00',
  };
};

/**
 * 업장 알림 희망 시간. 카테고리·지역 조건 추가와 같은 입력 칸을 쓴다.
 * 희망 날짜는 오늘부터 30일 안만, 시각은 기본 time 입력(30분 단위)으로 고른다.
 * 종료가 시작보다 같거나 이르거나 이미 지났으면 요청 전에 막는다.
 */
export default function StoreHopeForm({ onSubmit, saving = false, className = '' }) {
  const [form, setForm] = useState(emptyTimeForm);
  const minDate = todayKst();
  const maxDate = addDays(minDate, HOPE_DAYS);

  const change = (field) => (event) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }));
  };

  const startDateEmpty = !form.startDate;
  const endDateEmpty = !form.endDate;
  const startTimeEmpty = !form.startTime;
  const endTimeEmpty = !form.endTime;
  const hasFullStamp = Boolean(form.startDate && form.startTime && form.endDate && form.endTime);
  const invalidTime =
    hasFullStamp && toStamp(form.endDate, form.endTime) <= toStamp(form.startDate, form.startTime);
  const startOutOfRange =
    Boolean(form.startDate) && (form.startDate < minDate || form.startDate > maxDate);
  const endOutOfRange =
    Boolean(form.endDate) && (form.endDate < minDate || form.endDate > maxDate);
  const outOfRange = hasFullStamp && (startOutOfRange || endOutOfRange);
  const startPast =
    hasFullStamp && new Date(toKstDateTime(form.startDate, form.startTime)).getTime() <= Date.now();
  const expired =
    hasFullStamp && new Date(toKstDateTime(form.endDate, form.endTime)).getTime() <= Date.now();
  const canSubmit = hasFullStamp && !invalidTime && !outOfRange && !startPast && !expired;

  const handleSubmit = (event) => {
    event.preventDefault();
    if (!canSubmit) return;
    onSubmit({
      startTime: toKstDateTime(form.startDate, form.startTime),
      endTime: toKstDateTime(form.endDate, form.endTime),
    });
  };

  return (
    <form
      className={[styles.form, className].filter(Boolean).join(' ')}
      onSubmit={handleSubmit}
      noValidate
    >
      <div className={styles.timeRow}>
        <div className={styles.field}>
          <label>
            희망 시작일
            <input
              required
              type="date"
              min={minDate}
              max={maxDate}
              value={form.startDate}
              onChange={change('startDate')}
            />
          </label>
          {startDateEmpty && (
            <p role="alert" className={styles.error}>
              희망 시작일을 선택해 주세요.
            </p>
          )}
          {!startDateEmpty && startOutOfRange && (
            <p role="alert" className={styles.error}>
              날짜는 오늘부터 {HOPE_DAYS}일 안에서 선택해 주세요.
            </p>
          )}
        </div>
        <div className={styles.field}>
          <label>
            시작 시간
            <input
              required
              type="time"
              step="1800"
              value={form.startTime}
              onChange={change('startTime')}
            />
          </label>
          {startTimeEmpty && (
            <p role="alert" className={styles.error}>
              시작 시간을 선택해 주세요.
            </p>
          )}
          {!startTimeEmpty && startPast && (
            <p role="alert" className={styles.error}>
              이미 지난 시간은 선택할 수 없어요.
            </p>
          )}
        </div>
        <div className={styles.field}>
          <label>
            희망 종료일
            <input
              required
              type="date"
              min={minDate}
              max={maxDate}
              value={form.endDate}
              onChange={change('endDate')}
            />
          </label>
          {endDateEmpty && (
            <p role="alert" className={styles.error}>
              희망 종료일을 선택해 주세요.
            </p>
          )}
          {!endDateEmpty && endOutOfRange && (
            <p role="alert" className={styles.error}>
              날짜는 오늘부터 {HOPE_DAYS}일 안에서 선택해 주세요.
            </p>
          )}
        </div>
        <div className={styles.field}>
          <label>
            종료 시간
            <input
              required
              type="time"
              step="1800"
              value={form.endTime}
              onChange={change('endTime')}
            />
          </label>
          {endTimeEmpty && (
            <p role="alert" className={styles.error}>
              종료 시간을 선택해 주세요.
            </p>
          )}
          {!endTimeEmpty && (invalidTime || expired) && (
            <p role="alert" className={styles.error}>
              {ERROR_MESSAGES.NOTIFICATION_003}
            </p>
          )}
        </div>
      </div>
      <p className={styles.hint}>날짜는 오늘부터 {HOPE_DAYS}일 안, 시각은 30분 단위로 고를 수 있어요.</p>
      <Button variant="primary" wide disabled={saving || !canSubmit}>
        알림 조건 추가
      </Button>
    </form>
  );
}
