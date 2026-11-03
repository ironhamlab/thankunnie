import SlidingIndicator from '@/components/common/SlidingIndicator';
import { useSlidingIndicator } from '@/hooks/useSlidingIndicator';
import styles from './CategoryTabs.module.css';

/**
 * 홈 시술 분야 탭. 선택 표시가 고른 탭으로 미끄러져 이동한다.
 *
 * @param categories 분야 목록
 * @param selectedId 지금 고른 분야 id
 * @param onSelect   분야를 고르면 id 를 받는 콜백
 */
export default function CategoryTabs({ categories, selectedId, onSelect }) {
  const indicator = useSlidingIndicator(selectedId);

  return (
    <div className={styles.root} role="tablist" aria-label="시술 분야">
      <SlidingIndicator indicator={indicator} className={styles.indicator} />
      {categories.map((item) => {
        const isSelected = item.categoryId === selectedId;
        return (
          <button
            key={item.categoryId}
            ref={isSelected ? indicator.selectedRef : null}
            type="button"
            role="tab"
            aria-selected={isSelected}
            onClick={() => onSelect(item.categoryId)}
          >
            {item.name}
          </button>
        );
      })}
    </div>
  );
}
