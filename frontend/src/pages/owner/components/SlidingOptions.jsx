import SlidingIndicator from '@/components/common/SlidingIndicator';
import { useSlidingIndicator } from '@/hooks/useSlidingIndicator';
import styles from './SlidingOptions.module.css';

/**
 * 고른 표시가 미끄러져 옮겨 가는 선택 버튼 묶음. 매장 화면의 필터·탭에 쓴다.
 *
 * @param {{ key: string, label: import('react').ReactNode }[]} options 선택지
 * @param {string} [selectedKey] 고른 선택지. 없으면 표시를 그리지 않는다
 * @param {(key: string) => void} onSelect 선택지를 눌렀을 때
 * @param {string} label 화면 읽기 도구가 읽는 묶음 이름
 * @param {boolean} [tabs] 큰 탭 모양(b-tabs)으로 보여 줄지
 */
export default function SlidingOptions({ options, selectedKey, onSelect, label, tabs = false }) {
  const indicator = useSlidingIndicator(selectedKey);
  return (
    <div
      className={`b-options ${tabs ? 'b-tabs' : ''} ${styles.root}`}
      role="group"
      aria-label={label}
    >
      <SlidingIndicator
        indicator={indicator}
        className={tabs ? styles.tabIndicator : styles.chipIndicator}
      />
      {options.map((option) => (
        <button
          key={option.key}
          ref={selectedKey === option.key ? indicator.selectedRef : null}
          type="button"
          aria-pressed={selectedKey === option.key}
          className={selectedKey === option.key ? 'is-selected' : ''}
          onClick={() => onSelect(option.key)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
