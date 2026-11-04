import { useEffect, useRef, useState } from 'react';
import styles from './ChartReveal.module.css';

// 차트를 담은 패널이 올라오는 데 걸리는 시간(ms). StatsSection.module.css 의 패널 효과와 맞춘다
const PANEL_SETTLE_MS = 650;

/**
 * 차트가 화면에 들어오면 안쪽 애니메이션을 시작한다.
 * 그 전에는 시작 전 모습(막대 0, 칸 숨김)에 멈춰 두어 차오르는 과정을 처음부터 보여 준다.
 * 패널이 아직 올라오는 중이면 자리 잡을 때까지만 기다리고, 스크롤해서 만난 차트는 바로 시작한다.
 *
 * @param {number} [threshold] 얼마나 보여야 시작할지 (0~1)
 * @param {string} [className] 감싸는 요소의 클래스
 */
export default function ChartReveal({ children, threshold = 0.1, className = '' }) {
  const ref = useRef(null);
  const [isRevealed, setIsRevealed] = useState(() => !window.IntersectionObserver);

  useEffect(() => {
    if (isRevealed) return undefined;
    const mountedAt = performance.now();
    let timer;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) return;
        observer.disconnect();
        const remaining = PANEL_SETTLE_MS - (performance.now() - mountedAt);
        if (remaining > 0) {
          timer = window.setTimeout(() => setIsRevealed(true), remaining);
        } else {
          setIsRevealed(true);
        }
      },
      { threshold },
    );
    observer.observe(ref.current);
    return () => {
      observer.disconnect();
      window.clearTimeout(timer);
    };
  }, [isRevealed, threshold]);

  return (
    <div
      ref={ref}
      className={`${className} ${isRevealed ? '' : styles.waiting}`.trim() || undefined}
    >
      {children}
    </div>
  );
}
