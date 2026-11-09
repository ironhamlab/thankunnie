import styles from './HopeDateField.module.css';

/**
 * 알림 희망 날짜. 칸 어디를 눌러도 캘린더가 열리게 한다.
 */
export default function HopeDateField({ label, value, onChange, min, max }) {
  const openPicker = (event) => {
    try {
      event.currentTarget.showPicker?.();
    } catch {
      // 이미 열린 경우나 지원하지 않는 브라우저는 기본 동작을 쓴다.
    }
  };

  return (
    <label className={styles.root}>
      {label}
      <input
        required
        type="date"
        className={styles.input}
        min={min}
        max={max}
        value={value}
        onChange={onChange}
        onClick={openPicker}
      />
    </label>
  );
}
