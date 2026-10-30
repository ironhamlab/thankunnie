import { formatDayLabel, overlappingCloses } from '@/utils/scheduleSlots';
import { durationLabel, NO_MORE_TODAY } from '@/utils/scheduleForm';
import CloseOverlapNotice from '@/pages/owner/components/CloseOverlapNotice';
import DateShortcuts from '@/pages/owner/components/DateShortcuts';
import TimeRangePicker from '@/pages/owner/components/TimeRangePicker';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import Alert from '@/components/common/Alert';
import { EXCEPTION_TYPES } from '@/constants/scheduleException';
import styles from './ScheduleExceptionForm.module.css';

/**
 * 빈자리 닫기·열기 입력란. 30분 칸을 눌러 시간을 고르고,
 * 예약이 걸린 시간 닫기·닫아 둔 시간 열기·지난 시각은 저장 전에 알려준다.
 */
export default function ScheduleExceptionForm({
  form,
  editingId,
  slots,
  ready,
  fresh,
  failed,
  exceptions,
  minStart,
  minDate,
  maxDate,
  isSaving,
  errorMessage,
  onChange,
  onSubmit,
  onCancelEdit,
  onDeleteClose,
  onRetry,
}) {
  const type = EXCEPTION_TYPES[form.exceptionType];
  const inRange = slots.filter((slot) => slot.time >= form.startTime && slot.time < form.endTime);
  const hasBooking =
    form.exceptionType === 'CLOSE' && inRange.some((slot) => slot.status === 'BOOKED');
  const closes =
    form.exceptionType === 'OPEN'
      ? overlappingCloses(exceptions, form.date, form.startTime, form.endTime, editingId)
      : [];
  const tooEarly = Boolean(minStart) && form.startTime < minStart;
  // 닫기는 지금 일정 기준으로 받은 칸으로 예약을 확인한 뒤에만 저장한다. 서버도 막지만 헛된 실패 요청을 줄인다
  const closeUnchecked = form.exceptionType === 'CLOSE' && (!fresh || failed);

  return (
    <form className={styles.root} onSubmit={onSubmit} aria-busy={isSaving}>
      {editingId && (
        <Alert as="div" tone="success" className={styles.editing}>
          <span>등록한 일정을 수정하고 있어요.</span>
          <Button type="button" size="small" onClick={onCancelEdit}>
            수정 취소
          </Button>
        </Alert>
      )}
      <div className={styles.types} role="radiogroup" aria-label="구분">
        {Object.entries(EXCEPTION_TYPES).map(([key, item]) => (
          <button
            key={key}
            type="button"
            role="radio"
            aria-checked={form.exceptionType === key}
            className={`${styles.type} ${styles[key.toLowerCase()]}`}
            onClick={() => onChange({ exceptionType: key })}
          >
            <strong>{item.title}</strong>
            <small>{item.description}</small>
          </button>
        ))}
      </div>

      <Panel as="section" padded className={styles.section}>
        <h2>
          <span className={styles.step}>1</span> 날짜
        </h2>
        <DateShortcuts
          value={form.date}
          min={minDate}
          max={maxDate}
          onChange={(date) => onChange({ date })}
        />
      </Panel>

      <Panel as="section" padded className={styles.section}>
        <h2>
          <span className={styles.step}>2</span> 시간
        </h2>
        {failed && (
          <Alert as="div" tone="error" role="alert" className={styles.retry}>
            <span>
              시간 칸을 불러오지 못했어요. 예약 여부를 확인할 수 없어 닫기는 저장할 수 없어요.
            </span>
            <Button type="button" size="small" onClick={onRetry}>
              다시 시도
            </Button>
          </Alert>
        )}
        {!failed && !ready && <p role="status">시간 칸을 불러오는 중입니다.</p>}
        {!failed && ready && (
          <TimeRangePicker
            slots={slots}
            startTime={form.startTime}
            endTime={form.endTime}
            minStart={minStart}
            onChange={onChange}
          />
        )}
      </Panel>

      <Panel as="section" padded className={styles.section}>
        <h2>
          <span className={styles.step}>3</span> 사유 (선택)
        </h2>
        <div className={styles.chips}>
          {type.reasons.map((reason) => (
            <button
              key={reason}
              type="button"
              aria-pressed={form.reason === reason}
              onClick={() => onChange({ reason })}
            >
              {reason}
            </button>
          ))}
        </div>
        <input
          maxLength={500}
          placeholder="직접 입력"
          aria-label="사유 직접 입력"
          value={form.reason}
          onChange={(event) => onChange({ reason: event.target.value })}
        />
      </Panel>

      {tooEarly && (
        <Alert as="p" tone="error" role="alert">
          {minStart === NO_MORE_TODAY &&
            '오늘은 더 고를 수 있는 시간이 없어요. 다른 날짜를 골라 주세요.'}
          {minStart !== NO_MORE_TODAY &&
            (form.exceptionType === 'OPEN'
              ? `빈자리는 시작 30분 전까지만 열 수 있어요. 오늘은 ${minStart}부터 고를 수 있어요.`
              : '이미 지난 시간은 닫을 수 없어요.')}
        </Alert>
      )}
      {hasBooking && (
        <Alert as="p" tone="error" role="alert">
          예약이 있는 칸이 포함돼 있어 닫을 수 없어요. 예약이 없는 시간만 골라 주세요.
        </Alert>
      )}
      <CloseOverlapNotice closes={closes} disabled={isSaving} onDelete={onDeleteClose} />
      {errorMessage && (
        <Alert as="p" tone="error" role="alert">
          {errorMessage}
        </Alert>
      )}
      {/* 한 단 화면에서는 아래에 붙어, 칸을 고르면서 결과를 보고 바로 저장할 수 있다 */}
      <div className={styles.footer}>
        <p className={`${styles.summary} ${styles[form.exceptionType.toLowerCase()]}`}>
          {formatDayLabel(form.date)} {form.startTime}~{form.endTime} ·{' '}
          {durationLabel(form.startTime, form.endTime)} {type.verb}
        </p>
        <Button
          variant="primary"
          wide
          type="submit"
          disabled={isSaving || tooEarly || hasBooking || closeUnchecked}
        >
          {editingId ? '수정 저장' : `${form.startTime}~${form.endTime} ${type.verb}`}
        </Button>
      </div>
    </form>
  );
}
