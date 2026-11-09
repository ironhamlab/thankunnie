import { useState } from 'react';
import { ERROR_MESSAGES } from '@/constants/errorMessages';
import { selectableTreatments } from '@/utils/notificationRules';
import { addDays, defaultHopePeriod, toKstDateTime, todayKst } from '@/utils/scheduleDate';
import Button from '@/components/common/Button';
import HopeDateField from './HopeDateField';
import HopeTimeField from './HopeTimeField';
import StoreTreatmentField from './StoreTreatmentField';
import styles from './SubscriptionCreateForm.module.css';

const HOPE_DAYS = 30;
const toStamp = (date, time) => `${date}T${time}`;

const emptyTimeForm = () => defaultHopePeriod();

/**
 * 업장 알림 희망 시간. 카테고리·지역 조건 추가와 같은 입력 칸을 쓴다.
 * 판매 중인 일반 시술을 고르기 전에는 등록할 수 없다.
 * 희망 날짜는 오늘부터 30일 안만, 시각은 30분 단위 휠로 고른다.
 * 종료가 시작보다 같거나 이르거나 이미 지났으면 요청 전에 막는다.
 */
export default function StoreHopeForm({
  treatments = null,
  treatmentId,
  onTreatmentChange,
  onSubmit,
  saving = false,
  className = '',
}) {
  const [form, setForm] = useState(emptyTimeForm);
  const minDate = todayKst();
  const maxDate = addDays(minDate, HOPE_DAYS);

  const change = (field) => (event) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }));
  };
  const changeTime = (field) => (time) => {
    setForm((prev) => ({ ...prev, [field]: time }));
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
  const treatmentChosen = selectableTreatments(treatments).some(
    (item) => String(item.treatmentId) === String(treatmentId),
  );
  const canSubmit =
    treatmentChosen && hasFullStamp && !invalidTime && !outOfRange && !startPast && !expired;

  const handleSubmit = (event) => {
    event.preventDefault();
    if (!canSubmit) return;
    onSubmit({
      treatmentId: Number(treatmentId),
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
      <StoreTreatmentField treatments={treatments} value={treatmentId ?? ''} onChange={onTreatmentChange} />
      <div className={styles.timeRow}>
        <div className={styles.field}>
          <HopeDateField
            label="희망 시작일"
            min={minDate}
            max={maxDate}
            value={form.startDate}
            onChange={change('startDate')}
          />
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
          <HopeTimeField label="시작 시간" value={form.startTime} onChange={changeTime('startTime')} />
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
          <HopeDateField
            label="희망 종료일"
            min={minDate}
            max={maxDate}
            value={form.endDate}
            onChange={change('endDate')}
          />
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
          <HopeTimeField label="종료 시간" value={form.endTime} onChange={changeTime('endTime')} />
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
      <div className={styles.rule}>
        <p className={styles.lead}>
          희망 시간 안에 시작하는 빈자리만 알려 드립니다. 고른 시술은 그 시작 시각에 끝까지 받을 수
          있을 때만 해당합니다.
        </p>
        <p className={styles.hint}>예) 희망 14:00~18:00, 시술 60분</p>
        <ul className={styles.examples}>
          <li>✅ 14:30~15:30 — 시작이 희망 안이고, 시술도 끝까지 가능</li>
          <li>✅ 17:30~18:30 — 시술이 희망 종료(18:00)를 넘겨도, 시작만 희망 안이면 됩니다</li>
          <li>❌ 18:30~19:30 — 시작이 희망 밖</li>
          <li>❌ 16:30~17:30 · 매장 마감 17:00 — 시작은 희망 안이지만, 시술을 끝까지 못 받음</li>
        </ul>
        <p className={styles.hint}>날짜는 오늘부터 {HOPE_DAYS}일 안, 시각은 30분 단위로 고를 수 있어요.</p>
      </div>
      <Button variant="primary" wide disabled={saving || !canSubmit}>
        알림 조건 추가
      </Button>
    </form>
  );
}
