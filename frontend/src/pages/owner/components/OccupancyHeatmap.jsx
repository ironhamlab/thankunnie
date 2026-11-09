import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { WEEKDAYS, WEEKDAY_LABEL, occupancyRate } from '@/utils/ownerDashboardFormat';
import Button from '@/components/common/Button';
import ChartReveal from './ChartReveal';
import NudgeArrow from './NudgeArrow';
import styles from './OccupancyHeatmap.module.css';

// 진하기 단계는 가장 바쁜 칸과 비교하지 않고 점유율(%) 구간으로 고정한다.
// 상대 비교면 40% 칸도 진하게 보여 예약이 많은 것처럼 읽히기 때문이다
const LEVEL_RANGES = [
  { level: 1, min: 1, label: '1~24%' },
  { level: 2, min: 25, label: '25~49%' },
  { level: 3, min: 50, label: '50~74%' },
  { level: 4, min: 75, label: '75% 이상' },
];
const levelOf = (rate) =>
  LEVEL_RANGES.reduce((level, range) => (rate >= range.min ? range.level : level), 0);
// 1위 뒤에 함께 보여 줄 후보 수
const MORE_COUNT = 2;
// 칸이 대각선으로 퍼지며 나타나는 간격(ms)
const CELL_WAVE_MS = 45;
// 표가 이만큼 보이면 시작한다. 윗줄만 걸쳤을 때 시작하면 아래 칸이 화면 밖에서 끝나 버린다
const GRID_REVEAL_THRESHOLD = 0.4;

const keyOf = (day, hour) => `${day}-${hour}`;
const shortName = (cell) => `${WEEKDAY_LABEL[cell.dayOfWeek]} ${cell.hour}시`;

/**
 * 요일 × 시간대 예약 점유. 칸마다 "그 시간대에 영업한 시간 중 몇 %가 찼는지"를 보여 준다.
 * 매장은 한 번에 한 예약만 받으므로 예약 분 ÷ 영업한 분이다. 영업한 분은 서버가 휴무일·영업 외 시간을 빼고 센다.
 * 위에는 가장 바쁜·한가한 시간을 크게 보여 주고, 한가한 시간에서 빈자리 관리로 바로 간다.
 * 한가한 시간은 영업한 칸 중에서 고르므로 예약이 없던 영업 시간(0%)도 후보가 되고, 휴무·영업 외 칸은 빠진다.
 *
 * @param {{ dayOfWeek: string, hour: number, bookedMinutes: number, openMinutes: number }[]} cells
 *   영업도 예약도 없던 칸은 없다
 */
export default function OccupancyHeatmap({ cells }) {
  const rated = cells.map((cell) => ({
    ...cell,
    rate: occupancyRate(cell.bookedMinutes, cell.openMinutes),
  }));
  const byKey = new Map(rated.map((cell) => [keyOf(cell.dayOfWeek, cell.hour), cell]));
  const hours = rated.map((cell) => cell.hour);
  const firstHour = Math.min(...hours);
  const lastHour = Math.max(...hours);
  const rows = Array.from({ length: lastHour - firstHour + 1 }, (_, index) => firstHour + index);

  // 바쁜 시간은 예약이 있던 칸에서 고른다. 같은 % 면 예약 분이 많은 칸을 앞에 둔다
  const busy = rated
    .filter((cell) => cell.bookedMinutes > 0)
    .sort((a, b) => b.rate - a.rate || b.bookedMinutes - a.bookedMinutes)
    .slice(0, MORE_COUNT + 1);
  const busyKeys = new Set(busy.map((cell) => keyOf(cell.dayOfWeek, cell.hour)));
  // 한가한 시간은 영업한 칸에서 고른다. 같은 % 면 영업한 분이 많은(자주 비어 있던) 칸을 앞에 둔다.
  // 칸이 적으면 바쁜 시간과 겹치지 않는 것만 보여 준다
  const quiet = rated
    .filter((cell) => cell.openMinutes > 0 && !busyKeys.has(keyOf(cell.dayOfWeek, cell.hour)))
    .sort((a, b) => a.rate - b.rate || b.openMinutes - a.openMinutes)
    .slice(0, MORE_COUNT + 1);

  // 고른 칸. 처음에는 가장 바쁜 시간을 보여 준다
  const [selectedKey, setSelectedKey] = useState(() =>
    busy[0] ? keyOf(busy[0].dayOfWeek, busy[0].hour) : null,
  );
  const selected = byKey.get(selectedKey);
  const [selectedDay, selectedHourText] = selectedKey ? selectedKey.split('-') : [];

  const describe = (day, hour, cell) => {
    const state = !cell?.openMinutes
      ? '영업 안 함'
      : cell.rate
        ? `평균 ${cell.rate}% 찼어요`
        : '예약 없음';
    return `${WEEKDAY_LABEL[day]}요일 ${hour}~${hour + 1}시 · ${state}`;
  };

  const pick = (cell) => setSelectedKey(keyOf(cell.dayOfWeek, cell.hour));
  // 1위 옆(오른쪽)에 다음 후보를 점유율과 함께 세로로 보여 준다. 누르면 아래 표에서 그 칸을 고른다
  const candidates = (title, list) =>
    list.length > 0 && (
      <div className={styles.candidates}>
        <small>{title}</small>
        {list.map((cell) => {
          const key = keyOf(cell.dayOfWeek, cell.hour);
          return (
            <button
              key={key}
              type="button"
              className={styles.candidate}
              aria-pressed={selectedKey === key}
              onClick={() => pick(cell)}
            >
              <span>{shortName(cell)}</span>
              <strong>{cell.rate}%</strong>
            </button>
          );
        })}
      </div>
    );

  return (
    <div className="b-stack">
      <div className={styles.summary}>
        {busy[0] && (
          <div className={`${styles.card} ${styles.busyCard}`}>
            <div className={styles.lead}>
              <small>가장 바쁜 시간</small>
              <button
                type="button"
                className={styles.main}
                aria-pressed={selectedKey === keyOf(busy[0].dayOfWeek, busy[0].hour)}
                onClick={() => pick(busy[0])}
              >
                {shortName(busy[0])}
              </button>
              <p className={styles.rate}>평균 {busy[0].rate}% 찼어요</p>
            </div>
            {candidates('다음으로 바쁜', busy.slice(1))}
          </div>
        )}
        {quiet[0] && (
          <div className={`${styles.card} ${styles.quietCard}`}>
            <div className={styles.lead}>
              <small>가장 한가한 시간</small>
              <button
                type="button"
                className={styles.main}
                aria-pressed={selectedKey === keyOf(quiet[0].dayOfWeek, quiet[0].hour)}
                onClick={() => pick(quiet[0])}
              >
                {shortName(quiet[0])}
              </button>
              <p className={styles.rate}>평균 {quiet[0].rate}% 찼어요</p>
            </div>
            {candidates('다음으로 한가한', quiet.slice(1))}
            <Button
              as={Link}
              variant="primary"
              wide
              className={styles.cta}
              to={ROUTES.OWNER_VACANCIES}
            >
              이 시간에 할인 빈자리 열기 <NudgeArrow />
            </Button>
          </div>
        )}
      </div>

      {/* 고른 칸의 설명. 칸을 바꿀 때마다 화면 읽기 프로그램이 읽어 준다 */}
      <p className={styles.detail} aria-live="polite">
        {selectedKey
          ? describe(selectedDay, Number(selectedHourText), selected)
          : '칸을 누르면 그 시간이 얼마나 찼는지 볼 수 있어요.'}
      </p>

      {/* 위 요약 카드와 따로, 표가 눈에 들어올 때 칸이 차오르기 시작한다 */}
      <ChartReveal threshold={GRID_REVEAL_THRESHOLD} className="b-stack">
        <div className={styles.scroll}>
          <div className={styles.grid} role="group" aria-label="요일·시간대별 평균 점유율">
            <span aria-hidden="true" />
            {WEEKDAYS.map((day) => (
              <span key={day} className={styles.head} aria-hidden="true">
                {WEEKDAY_LABEL[day]}
              </span>
            ))}
            {rows.map((hour, rowIndex) => [
              <span key={`h-${hour}`} className={styles.hour} aria-hidden="true">
                {hour}시
              </span>,
              ...WEEKDAYS.map((day, dayIndex) => {
                const key = keyOf(day, hour);
                const cell = byKey.get(key);
                const level = levelOf(cell?.rate ?? 0);
                return (
                  <button
                    key={key}
                    type="button"
                    className={`${styles.cell} ${styles[`level${level}`]}`}
                    // 왼쪽 위 칸부터 대각선으로 퍼지듯 나타난다
                    style={{ '--wave-delay': `${(rowIndex + dayIndex) * CELL_WAVE_MS}ms` }}
                    aria-pressed={selectedKey === key}
                    aria-label={describe(day, hour, cell)}
                    onClick={() => setSelectedKey(key)}
                    onMouseEnter={() => setSelectedKey(key)}
                    onFocus={() => setSelectedKey(key)}
                  />
                );
              }),
            ])}
          </div>
        </div>
        <ul className={styles.legend} aria-label="색 구분">
          {LEVEL_RANGES.map((range) => (
            <li key={range.level}>
              <i className={styles[`level${range.level}`]} aria-hidden="true" />
              {range.label}
            </li>
          ))}
        </ul>
      </ChartReveal>
    </div>
  );
}
