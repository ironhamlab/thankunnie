import styles from './SlidingIndicator.module.css';

/**
 * 고른 항목 뒤로 미끄러져 이동하는 선택 표시. 위치는 useSlidingIndicator 로 잰다.
 * 항목을 감싼 요소에 position: relative 와 isolation: isolate 를 주어 이 표시가 글자 뒤에 깔리게 한다.
 *
 * @param indicator useSlidingIndicator 의 결과
 * @param className 표시의 색·모서리
 */
export default function SlidingIndicator({ indicator, className = '' }) {
  if (!indicator.style) {
    return null;
  }
  return (
    <span
      className={`${styles.indicator} ${indicator.isMoving ? styles.moving : ''} ${className}`}
      style={indicator.style}
      aria-hidden="true"
    />
  );
}
