import { useEffect, useId, useRef, useState } from 'react';
import { TimePicker } from 'seed-design/ui/time-picker';
import styles from './HopeTimeField.module.css';

const MINUTE_STEP = 30;
const ITEM_SIZE = 32;
const VISIBLE_ITEM_COUNT = 3;

function pickerValue(time) {
  const [rawHour = '0', rawMinute = '0'] = (time || '00:00').split(':');
  const hour = Math.min(23, Math.max(0, Number(rawHour) || 0));
  const minuteNumber = Number(rawMinute) || 0;
  // TimePicker는 minuteStep에 맞는 분만 받는다. 화면 값은 사용자가 휠을 움직일 때 맞춘다.
  const snapped = Math.floor(minuteNumber / MINUTE_STEP) * MINUTE_STEP;
  return { hour, minute: snapped };
}

function formatTime(time) {
  const [rawHour = '0', rawMinute = '0'] = time.split(':');
  const hour = Number(rawHour) || 0;
  const minute = Number(rawMinute) || 0;
  const period = hour < 12 ? '오전' : '오후';
  const displayHour = hour % 12 || 12;
  return `${period} ${displayHour}:${String(minute).padStart(2, '0')}`;
}

/**
 * 알림 희망 시각. 날짜 칸은 그대로 두고 시각만 30분 단위 휠로 고른다.
 */
export default function HopeTimeField({ label, value, onChange }) {
  const labelId = useId();
  const rootRef = useRef(null);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return undefined;

    const handlePointerDown = (event) => {
      if (!rootRef.current?.contains(event.target)) setOpen(false);
    };
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') setOpen(false);
    };

    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open]);

  return (
    <div className={styles.root} ref={rootRef}>
      <span id={labelId}>{label}</span>
      <button
        type="button"
        className={styles.trigger}
        aria-expanded={open}
        aria-labelledby={labelId}
        onClick={() => setOpen((prev) => !prev)}
      >
        {value ? formatTime(value) : '시간 선택'}
      </button>
      {open && (
        <div className={styles.picker}>
          <TimePicker
            minuteStep={MINUTE_STEP}
            itemSize={ITEM_SIZE}
            visibleItemCount={VISIBLE_ITEM_COUNT}
            aria-labelledby={labelId}
            value={pickerValue(value)}
            onValueChange={({ hour, minute }) =>
              onChange(`${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`)
            }
          />
        </div>
      )}
    </div>
  );
}
