import Panel from '@/components/common/Panel';
import RollingValue from '@/components/common/RollingValue';
import styles from './StatCard.module.css';

/**
 * 매장 대시보드·통계의 숫자 카드. 큰 숫자 하나(value) 또는 나란히 놓인 큰 숫자 여러 개(values)를 보여 준다.
 *
 * @param {string} label 카드 제목
 * @param {string|number} [value] 큰 숫자
 * @param {string} [unit] 큰 숫자 단위
 * @param {{ label: string, value: string|number, unit?: string }[]} [values] 나란히 보여 줄 큰 숫자들
 * @param {{ text: string, tone: 'good'|'bad'|'flat' } | null} [change] 비교 기간 대비 변화 (예: ▲ 10.8%)
 * @param {string} [changeLabel] 변화 옆 설명 (예: 직전 기간 대비)
 * @param {string[]} [notes] 아래 보조 설명 줄
 * @param {import('react').ReactNode} [children] 보조 설명 아래에 둘 내용 (예: 시간대 칩)
 * @param {import('react').ReactNode} [action] 맨 아래 링크·버튼 (예: 설정 화면으로 이동)
 */
export default function StatCard({
  label,
  value,
  unit,
  values,
  change,
  changeLabel,
  notes = [],
  children,
  action,
}) {
  return (
    <Panel as="article" padded className={styles.card}>
      <small>{label}</small>
      {values ? (
        <div className={styles.values}>
          {values.map((item) => (
            <div key={item.label}>
              <small className={styles.valueLabel}>{item.label}</small>
              <BigNumber value={item.value} unit={item.unit} />
            </div>
          ))}
        </div>
      ) : (
        <BigNumber value={value} unit={unit} />
      )}
      {change && (
        <small className={styles.note}>
          <span className={styles[change.tone]}>{change.text}</span>
          {changeLabel && ` ${changeLabel}`}
        </small>
      )}
      {notes.map((note) => (
        <small key={note} className={styles.note}>
          {note}
        </small>
      ))}
      {children}
      {action && <div className={styles.action}>{action}</div>}
    </Panel>
  );
}

function BigNumber({ value, unit }) {
  return (
    <h2 className="b-pink">
      <RollingValue value={String(value)} />
      {unit && <small>{unit}</small>}
    </h2>
  );
}
