import { selectableTreatments } from '@/utils/notificationRules';
import styles from './StoreTreatmentField.module.css';

/**
 * 업장 알림에서 고르는 시술. 판매 중인 일반 시술만 보여 준다.
 *
 * @param {Array|null} treatments 매장 시술. null 이면 아직 불러오는 중이다
 * @param {string} value 고른 시술 ID. 없으면 빈 문자열
 * @param {(event: import('react').ChangeEvent<HTMLSelectElement>) => void} onChange
 */
export default function StoreTreatmentField({ treatments, value, onChange, disabled = false }) {
  const loading = treatments == null;
  const choices = selectableTreatments(treatments);

  return (
    <label className={styles.field}>
      시술
      <select
        required
        value={value == null || value === '' ? '' : String(value)}
        onChange={onChange}
        disabled={disabled || loading || !choices.length}
      >
        <option value="">시술 선택</option>
        {choices.map((item) => (
          <option key={item.treatmentId} value={String(item.treatmentId)}>
            {item.name}
          </option>
        ))}
      </select>
      {loading && (
        <p className={styles.hint} role="status">
          시술을 불러오는 중입니다.
        </p>
      )}
      {!loading && !choices.length && (
        <p className={styles.error} role="alert">
          판매 중인 일반 시술이 없어요.
        </p>
      )}
      {!loading && choices.length > 0 && !value && (
        <p className={styles.error} role="alert">
          시술을 선택해 주세요.
        </p>
      )}
    </label>
  );
}
