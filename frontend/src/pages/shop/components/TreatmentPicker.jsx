import Badge from '@/components/common/Badge';
import Panel from '@/components/common/Panel';
import styles from '../ShopDetailPage.module.css';

const money = (value) => `${value.toLocaleString('ko-KR')}원`;

/**
 * 판매 중인 시술 중 하나를 고른다. 목록은 정상 가격만 보여주고, 시각별 할인 가격은 예약 요약에서 서버 값으로 보여준다
 *
 * @param emptyMessage 고를 시술이 없을 때 안내 문구
 */
export default function TreatmentPicker({ treatments, selectedId, onSelect, emptyMessage }) {
  return (
    <Panel as="section" padded>
      <div className="b-row b-panel-heading">
        <h2>시술 선택</h2>
        <Badge tone="neutral">{treatments.length}개</Badge>
      </div>
      {treatments.length === 0 ? (
        <p className={styles.empty}>{emptyMessage}</p>
      ) : (
        <ul className={styles.treatments}>
          {treatments.map((treatment) => {
            const selected = treatment.treatmentId === selectedId;
            return (
              <li key={treatment.treatmentId}>
                <button
                  type="button"
                  className={selected ? 'is-selected' : undefined}
                  aria-pressed={selected}
                  onClick={() => onSelect(treatment.treatmentId)}
                >
                  <span className={styles.treatmentName}>
                    <strong>{treatment.name}</strong>
                    {treatment.treatmentType === 'MODEL' && <Badge>초보 시술 모집</Badge>}
                  </span>
                  {treatment.description && <span>{treatment.description}</span>}
                  <span className={styles.treatmentMeta}>
                    <span>{treatment.durationMinutes}분</span>
                    <strong>{money(treatment.regularPrice)}</strong>
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </Panel>
  );
}
