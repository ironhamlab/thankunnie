import { useEffect, useState } from 'react';
import { useOwnerStatsStore } from '@/store/useOwnerStatsStore';
import { formatMoney } from '@/utils/reservationFormat';
import {
  formatAmount,
  formatAxisMoney,
  formatMinutes,
  formatRate,
  monthRange,
} from '@/utils/ownerDashboardFormat';
import { addDays, todayKst } from '@/utils/scheduleDate';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import Panel from '@/components/common/Panel';
import Spinner from '@/components/common/Spinner';
import DailyBarChart from './DailyBarChart';
import StatCard from './StatCard';
import TreatmentBars from './TreatmentBars';
import styles from './StatsSection.module.css';

// 서버 최대 조회 기간(92일)과 맞춘다
const MAX_DAYS = 92;

const PRESETS = [
  { key: 'thisMonth', label: '이번 달', range: (today) => monthRange(today) },
  {
    key: 'lastMonth',
    label: '지난달',
    range: (today) => monthRange(addDays(monthRange(today).from, -1)),
  },
  {
    key: 'last30',
    label: '최근 30일',
    range: (today) => ({ from: addDays(today, -29), to: today }),
  },
];

const METRICS = [
  {
    key: 'bookedAmount',
    label: '확정 예약 금액',
    getValue: (day) => day.bookedAmount,
    formatTick: formatAxisMoney,
  },
  {
    key: 'confirmedCount',
    label: '확정 건수',
    getValue: (day) => day.confirmedCount,
    formatTick: (value) => `${value}건`,
    minStep: 1,
  },
  {
    key: 'cancelledCount',
    label: '취소 건수',
    getValue: (day) => day.cancelledCount,
    formatTick: (value) => `${value}건`,
    minStep: 1,
  },
];

const describeDay = (day) => [
  `확정 ${day.confirmedCount}건 · ${formatMoney(day.bookedAmount)}`,
  `취소 ${day.cancelledCount}건`,
];

// YYYY-MM-DD 두 날짜 사이 일수 (둘 다 포함)
const daysBetween = (from, to) => (Date.parse(to) - Date.parse(from)) / 86400000 + 1;

/**
 * 사장님 메인의 기간 통계 영역. 기간을 골라 확정 예약 금액·재판매·취소·초임박 판매 요약과 날짜별·시술별 그래프를 본다.
 * 기본 기간은 이번 달이다.
 */
export default function StatsSection() {
  const stats = useOwnerStatsStore((state) => state.stats);
  const status = useOwnerStatsStore((state) => state.status);
  const loadStats = useOwnerStatsStore((state) => state.loadStats);
  const [period, setPeriod] = useState(() => monthRange(todayKst()));
  const [metricKey, setMetricKey] = useState(METRICS[0].key);

  const periodError =
    period.from > period.to
      ? '끝 날짜는 시작 날짜와 같거나 이후여야 합니다.'
      : daysBetween(period.from, period.to) > MAX_DAYS
        ? `조회 기간은 최대 ${MAX_DAYS}일입니다.`
        : '';

  useEffect(() => {
    if (!periodError) {
      loadStats(period);
    }
  }, [loadStats, period, periodError]);

  const today = todayKst();
  const activePreset = PRESETS.find((preset) => {
    const range = preset.range(today);
    return range.from === period.from && range.to === period.to;
  })?.key;
  const metric = METRICS.find((item) => item.key === metricKey);
  // 응답의 기간이 지금 고른 기간과 같고 조회가 성공했을 때만 본문을 그린다.
  // 기간을 바꾼 직후(새 요청을 보내기 전 렌더)·기간 오류·조회 중·실패 때 이전 기간 통계가 보이지 않게 한다
  const isCurrentPeriod = stats?.summary?.from === period.from && stats?.summary?.to === period.to;
  const summary = !periodError && status === 'succeeded' && isCurrentPeriod ? stats.summary : null;
  const waiting =
    !periodError && (status === 'loading' || (status === 'succeeded' && !isCurrentPeriod));
  // 확정·취소 예약이 하나도 없으면 0 카드와 빈 그래프 대신 안내를 보여 준다 (첫 가입·오래 쉰 매장 등)
  const isEmpty =
    summary &&
    summary.confirmedCount + summary.customerCancelCount + summary.shopFaultCancelCount === 0;

  return (
    <section className="b-stack">
      <div className={`b-row ${styles.header}`}>
        <div>
          <h2>매출·예약 통계</h2>
          <p className="b-muted">
            시술일 기준 확정·취소 완료 예약. 금액은 예약금과 현장 잔금을 합친 확정 예약 금액입니다.
          </p>
        </div>
      </div>

      <Panel padded className={`b-flex ${styles.filters}`}>
        <div className="b-options" role="group" aria-label="기간 빠른 선택">
          {PRESETS.map((preset) => (
            <button
              key={preset.key}
              type="button"
              className={activePreset === preset.key ? 'is-selected' : ''}
              onClick={() => setPeriod(preset.range(today))}
            >
              {preset.label}
            </button>
          ))}
        </div>
        <label>
          시작{' '}
          <input
            type="date"
            value={period.from}
            onChange={(event) =>
              event.target.value && setPeriod((prev) => ({ ...prev, from: event.target.value }))
            }
          />
        </label>
        <label>
          끝{' '}
          <input
            type="date"
            value={period.to}
            min={period.from}
            onChange={(event) =>
              event.target.value && setPeriod((prev) => ({ ...prev, to: event.target.value }))
            }
          />
        </label>
      </Panel>

      {periodError && (
        <Alert tone="error" role="alert">
          {periodError}
        </Alert>
      )}
      {!periodError && status === 'failed' && (
        <Alert tone="error" role="alert" className={styles.error}>
          <span>통계를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</span>
          <Button size="small" type="button" onClick={() => loadStats(period)}>
            다시 시도
          </Button>
        </Alert>
      )}
      {waiting && <Spinner />}

      {isEmpty && (
        <Panel padded>
          <div className={styles.empty}>
            <strong>이 기간에는 집계할 예약이 없어요</strong>
            <p>다른 기간을 골라 보세요. 예약이 확정되면 매출과 취소·재판매 흐름이 여기에 쌓여요.</p>
          </div>
        </Panel>
      )}

      {summary && !isEmpty && (
        <>
          <div className="b-stat-grid">
            <StatCard
              label="확정 예약 금액"
              value={formatAmount(summary.bookedAmount)}
              unit="원"
              notes={[`확정 ${summary.confirmedCount}건`]}
            />
            <StatCard
              label="취소 후 재판매"
              value={formatAmount(summary.rebookedAmount)}
              unit="원"
              notes={[
                `빈 시간 회수율 ${formatRate(summary.rebookRate)}`,
                `취소로 빈 ${formatMinutes(summary.cancelledMinutes)} 중 ${formatMinutes(summary.rebookedMinutes)} 다시 채움`,
              ]}
            />
            <StatCard
              label="고객 취소율"
              value={formatRate(summary.customerCancelRate)}
              notes={[
                `고객 취소 ${summary.customerCancelCount}건 · 매장 사유 ${summary.shopFaultCancelCount}건`,
                `취소로 남은 예약금 ${formatMoney(summary.retainedDepositAmount)}`,
              ]}
            />
            <StatCard
              label="초임박 판매"
              value={formatAmount(summary.lastMinuteAmount)}
              unit="원"
              notes={[`시작 3시간 이내 점유 ${summary.lastMinuteCount}건`]}
            />
          </div>

          <Panel as="section" padded className="b-stack">
            <div className={`b-row ${styles.chartHeader}`}>
              <h2>날짜별 추이</h2>
              <div className="b-options" role="group" aria-label="그래프 지표">
                {METRICS.map((item) => (
                  <button
                    key={item.key}
                    type="button"
                    className={metricKey === item.key ? 'is-selected' : ''}
                    onClick={() => setMetricKey(item.key)}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>
            <DailyBarChart
              days={stats.daily}
              getValue={metric.getValue}
              formatTick={metric.formatTick}
              minStep={metric.minStep}
              describe={describeDay}
              label={`${summary.from}부터 ${summary.to}까지 날짜별 ${metric.label}`}
            />
            <details className={styles.table}>
              <summary>표로 보기</summary>
              <div className="b-table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>날짜</th>
                      <th>확정</th>
                      <th>확정 예약 금액</th>
                      <th>취소</th>
                    </tr>
                  </thead>
                  <tbody>
                    {stats.daily.map((day) => (
                      <tr key={day.date}>
                        <td>{day.date}</td>
                        <td>{day.confirmedCount}건</td>
                        <td>{formatMoney(day.bookedAmount)}</td>
                        <td>{day.cancelledCount}건</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </details>
          </Panel>

          <Panel as="section" padded className="b-stack">
            <h2>시술별 확정 예약 금액</h2>
            {stats.treatments.length ? (
              <TreatmentBars treatments={stats.treatments} />
            ) : (
              <EmptyState as="p">이 기간에 확정된 예약이 없습니다.</EmptyState>
            )}
          </Panel>
        </>
      )}
    </section>
  );
}
