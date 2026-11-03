import { useLayoutEffect, useRef, useState } from 'react';
import styles from './CategoryTabs.module.css';

/**
 * 홈 시술 분야 탭. 선택 표시가 고른 탭으로 미끄러져 이동한다.
 *
 * @param categories 분야 목록
 * @param selectedId 지금 고른 분야 id
 * @param onSelect   분야를 고르면 id 를 받는 콜백
 */
export default function CategoryTabs({ categories, selectedId, onSelect }) {
  const selectedRef = useRef(null);
  const [indicator, setIndicator] = useState(null);
  // 처음 그릴 때와 분야를 받아 올 때는 움직이지 않고, 사용자가 고른 뒤부터 움직인다
  const [hasChosen, setHasChosen] = useState(false);

  useLayoutEffect(() => {
    const selected = selectedRef.current;
    if (!selected) {
      return undefined;
    }
    const measure = () => setIndicator({ x: selected.offsetLeft, width: selected.offsetWidth });
    measure();
    // 글꼴을 늦게 받거나 화면 폭이 바뀌면 탭 크기가 달라지므로 다시 잰다
    const observer = new ResizeObserver(measure);
    observer.observe(selected);
    return () => observer.disconnect();
  }, [selectedId]);

  const handleSelect = (id) => {
    setHasChosen(true);
    onSelect(id);
  };

  return (
    <div className={styles.root} role="tablist" aria-label="시술 분야">
      {indicator && (
        <span
          className={`${styles.indicator} ${hasChosen ? styles.moving : ''}`}
          style={{ width: indicator.width, transform: `translateX(${indicator.x}px)` }}
          aria-hidden="true"
        />
      )}
      {categories.map((item) => {
        const isSelected = item.categoryId === selectedId;
        return (
          <button
            key={item.categoryId}
            ref={isSelected ? selectedRef : null}
            type="button"
            role="tab"
            aria-selected={isSelected}
            onClick={() => handleSelect(item.categoryId)}
          >
            {item.name}
          </button>
        );
      })}
    </div>
  );
}
