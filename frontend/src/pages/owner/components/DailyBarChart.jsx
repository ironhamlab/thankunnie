import { useEffect, useRef, useState } from 'react';
import { formatShortDate } from '@/utils/ownerDashboardFormat';
import styles from './DailyBarChart.module.css';

const HEIGHT = 220;
const PAD = { top: 12, right: 8, bottom: 28, left: 52 };
const TICK_COUNT = 4;
// 날짜 라벨 하나에 필요한 가로 폭(px). 좁은 화면에서는 라벨을 더 띄엄띄엄 단다
const X_LABEL_WIDTH = 48;

// 1·2·5 × 10^n 중 가장 작은 눈금 간격으로 축 최댓값을 정한다
const niceStep = (rough) => {
  if (rough <= 0) return 1;
  const power = 10 ** Math.floor(Math.log10(rough));
  const unit = [1, 2, 5, 10].find((candidate) => candidate * power >= rough);
  return unit * power;
};

// 아래 모서리는 각지게(기준선에 붙음), 위 모서리만 둥글게
const barPath = (x, y, width, height, radius) => {
  const r = Math.min(radius, width / 2, height);
  return `M${x},${y + height} V${y + r} Q${x},${y} ${x + r},${y} H${x + width - r} Q${x + width},${y} ${x + width},${y + r} V${y + height} Z`;
};

const useWidth = () => {
  const ref = useRef(null);
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const element = ref.current;
    if (!element) return undefined;
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  return [ref, width];
};

/**
 * 날짜별 막대 그래프 (한 가지 지표). 막대에 마우스를 올리면 그날의 지표 전체를 보여 준다.
 *
 * @param {object[]} days 날짜별 행 (date 포함)
 * @param {(day: object) => number} getValue 막대 높이로 쓸 값
 * @param {(value: number) => string} formatTick 세로축 눈금 표시
 * @param {(day: object) => string[]} describe 툴팁 내용 줄
 * @param {string} label 스크린 리더용 그래프 설명
 * @param {number} [minStep] 세로축 눈금 최소 간격. 건수처럼 정수만 의미 있는 지표는 1 을 넘긴다
 */
export default function DailyBarChart({
  days,
  getValue,
  formatTick,
  describe,
  label,
  minStep = 0,
}) {
  const [containerRef, width] = useWidth();
  const [hovered, setHovered] = useState(null);

  const values = days.map(getValue);
  const step = Math.max(niceStep(Math.max(...values, 0) / TICK_COUNT), minStep);
  const top = step * TICK_COUNT;
  const plotWidth = Math.max(0, width - PAD.left - PAD.right);
  const plotHeight = HEIGHT - PAD.top - PAD.bottom;
  const slot = days.length ? plotWidth / days.length : 0;
  const barWidth = Math.max(2, Math.min(28, slot * 0.7));
  const labelEvery = Math.max(1, Math.ceil(days.length / Math.max(2, plotWidth / X_LABEL_WIDTH)));
  // 마지막 날은 앞 라벨과 충분히 떨어져 있을 때만 단다
  const showLabel = (index) =>
    index % labelEvery === 0 ||
    (index === days.length - 1 && index % labelEvery >= Math.ceil(labelEvery / 2));
  const yOf = (value) => PAD.top + plotHeight - (value / top) * plotHeight;

  const hoveredDay = hovered === null ? null : days[hovered];
  const tooltipLeft = hovered === null ? 0 : PAD.left + slot * (hovered + 0.5);

  return (
    <div ref={containerRef} className={styles.root} onMouseLeave={() => setHovered(null)}>
      {width > 0 && (
        <svg width={width} height={HEIGHT} role="img" aria-label={label}>
          {Array.from({ length: TICK_COUNT + 1 }, (_, index) => {
            const value = step * index;
            const y = yOf(value);
            return (
              <g key={value}>
                <line
                  x1={PAD.left}
                  x2={width - PAD.right}
                  y1={y}
                  y2={y}
                  className={index ? styles.grid : styles.baseline}
                />
                <text x={PAD.left - 8} y={y} className={styles.tick} textAnchor="end">
                  {formatTick(value)}
                </text>
              </g>
            );
          })}
          {days.map((day, index) => {
            const value = values[index];
            const x = PAD.left + slot * index + (slot - barWidth) / 2;
            const y = yOf(value);
            const dimmed = hovered !== null && hovered !== index;
            return (
              <g key={day.date}>
                {value > 0 && (
                  <path
                    d={barPath(x, y, barWidth, PAD.top + plotHeight - y, 4)}
                    className={dimmed ? `${styles.bar} ${styles.dimmed}` : styles.bar}
                  />
                )}
                {showLabel(index) && (
                  <text
                    x={PAD.left + slot * (index + 0.5)}
                    y={HEIGHT - 8}
                    className={styles.tick}
                    textAnchor="middle"
                  >
                    {formatShortDate(day.date)}
                  </text>
                )}
                {/* 막대보다 넓은 마우스 영역 */}
                <rect
                  x={PAD.left + slot * index}
                  y={PAD.top}
                  width={slot}
                  height={plotHeight}
                  fill="transparent"
                  onMouseEnter={() => setHovered(index)}
                />
              </g>
            );
          })}
        </svg>
      )}
      {hoveredDay && (
        <div
          className={styles.tooltip}
          style={{
            left: Math.min(Math.max(tooltipLeft, 80), width - 80),
          }}
          role="status"
        >
          <strong>{hoveredDay.date}</strong>
          {describe(hoveredDay).map((line) => (
            <span key={line}>{line}</span>
          ))}
        </div>
      )}
    </div>
  );
}
