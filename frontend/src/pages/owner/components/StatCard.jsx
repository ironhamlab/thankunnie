import Panel from '@/components/common/Panel';
import styles from './StatCard.module.css';

/**
 * 매장 대시보드·통계의 숫자 카드. 큰 숫자 하나(value) 또는 나란히 놓인 큰 숫자 여러 개(values)를 보여 준다.
 *
 * @param {string} label 카드 제목
 * @param {string|number} [value] 큰 숫자
 * @param {string} [unit] 큰 숫자 단위
 * @param {{ label: string, value: string|number, unit?: string }[]} [values] 나란히 보여 줄 큰 숫자들
 * @param {string[]} [notes] 아래 보조 설명 줄
 * @param {import('react').ReactNode} [action] 맨 아래 링크·버튼 (예: 설정 화면으로 이동)
 */
export default function StatCard({ label, value, unit, values, notes = [], action }) {
  return (
    <Panel as="article" padded>
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
      {notes.map((note) => (
        <small key={note} className={styles.note}>
          {note}
        </small>
      ))}
      {action && <div className={styles.action}>{action}</div>}
    </Panel>
  );
}

function BigNumber({ value, unit }) {
  return (
    <h2 className="b-pink">
      {value}
      {unit && <small>{unit}</small>}
    </h2>
  );
}
