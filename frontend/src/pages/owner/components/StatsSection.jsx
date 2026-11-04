import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { statsPeriodKey, useOwnerStatsStore } from '@/store/useOwnerStatsStore';
import { formatMoney } from '@/utils/reservationFormat';
import {
  daysBetween,
  describeChange,
  formatAmount,
  formatAxisMoney,
  formatCompareRange,
  formatMinutes,
  formatRate,
  formatShortDate,
  formatShortMonth,
  monthRange,
} from '@/utils/ownerDashboardFormat';
import { addDays, todayKst } from '@/utils/scheduleDate';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import Panel from '@/components/common/Panel';
import Spinner from '@/components/common/Spinner';
import DailyBarChart from './DailyBarChart';
import OccupancyHeatmap from './OccupancyHeatmap';
import StatCard from './StatCard';
import TreatmentBars from './TreatmentBars';
import WeekdayBars from './WeekdayBars';
import styles from './StatsSection.module.css';

// 서버 최대 조회 기간(3660일)과 맞춘다
const MAX_DAYS = 3660;

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
  {
    key: 'last365',
    label: '최근 1년',
    range: (today) => ({ from: addDays(today, -364), to: today }),
  },
  { key: 'all', label: '서비스 이용 이후 전체', range: () => ({ all: true }) },
];

// 예약 금액·건수는 이용 추정 + 예정을 합쳐 막대로 그리고, 툴팁에서 나눠 보여 준다
const METRICS = [
  {
    key: 'amount',
    label: '예약 금액',
    getValue: (row) => row.completedAmount + row.upcomingAmount,
    formatTick: formatAxisMoney,
  },
  {
    key: 'count',
    label: '예약 건수',
    getValue: (row) => row.completedCount + row.upcomingCount,
    formatTick: (value) => `${value}건`,
    minStep: 1,
  },
  {
    key: 'cancelled',
    label: '취소 건수',
    getValue: (row) => row.cancelledCount,
    formatTick: (value) => `${value}건`,
    minStep: 1,
  },
];

const describeRow = (row) =>
  [
    `시술 ${row.completedCount}건 · ${formatMoney(row.completedAmount)}`,
    row.upcomingCount ? `예정 ${row.upcomingCount}건 · ${formatMoney(row.upcomingAmount)}` : null,
    `취소 ${row.cancelledCount}건`,
  ].filter(Boolean);

/**
 * 매장 매출·예약 분석. 기간을 골라 이용 추정·예정 금액, 취소·재판매, 재방문, 요일·시간대·시술별 흐름을 본다.
 * 기본 기간은 이번 달이다. 금액은 예약 시점 가격 기준이다.
 */
// 직접 고른 기간의 오류 문구. 문제없으면 빈 문자열
function validatePeriod(period) {
  if (period.all) return '';
  if (period.from > period.to) return '끝 날짜는 시작 날짜와 같거나 이후여야 합니다.';
  if (daysBetween(period.from, period.to) > MAX_DAYS)
    return `조회 기간은 최대 ${MAX_DAYS}일입니다.`;
  return '';
}

export default function StatsSection() {
  const stats = useOwnerStatsStore((state) => state.stats);
  const statsKey = useOwnerStatsStore((state) => state.statsKey);
  const status = useOwnerStatsStore((state) => state.status);
  const loadStats = useOwnerStatsStore((state) => state.loadStats);
  const [period, setPeriod] = useState(() => monthRange(todayKst()));
  const [metricKey, setMetricKey] = useState(METRICS[0].key);

  const periodError = validatePeriod(period);

  useEffect(() => {
    if (!periodError) {
      loadStats(period);
    }
  }, [loadStats, period, periodError]);

  const today = todayKst();
  const activePreset = PRESETS.find(
    (preset) => statsPeriodKey(preset.range(today)) === statsPeriodKey(period),
  )?.key;
  const metric = METRICS.find((item) => item.key === metricKey);
  // 응답이 지금 고른 기간의 결과이고 조회가 성공했을 때만 본문을 그린다.
  // 기간을 바꾼 직후·기간 오류·조회 중·실패 때 이전 기간 통계가 보이지 않게 한다
  const isCurrentPeriod = statsKey === statsPeriodKey(period);
  const current = !periodError && status === 'succeeded' && isCurrentPeriod ? stats : null;
  const summary = current?.summary ?? null;
  const previous = current?.previous ?? null;
  const waiting = !periodError && (status === 'loading' || !isCurrentPeriod);
  // 확정·취소 예약이 하나도 없으면 0 카드와 빈 그래프 대신 안내를 보여 준다 (첫 가입·오래 쉰 매장 등)
  const isEmpty =
    summary &&
    summary.completedCount +
      summary.upcomingCount +
      summary.customerCancelCount +
      summary.shopFaultCancelCount +
      (summary.noShowCount ?? 0) ===
      0;
  // 전체 기간은 응답의 기간을 날짜 칸에 보여 준다. 날짜를 고치면 그 날짜로 직접 조회한다
  const shown = period.all
    ? { from: summary?.from ?? '', to: summary?.to ?? today }
    : { from: period.from, to: period.to };
  const changeOf = (pick, options) =>
    previous ? describeChange(pick(summary), pick(previous), options) : null;
  const monthly = current?.granularity === 'MONTH';
  // 비교한 직전 같은 길이 기간을 날짜로 적는다 (예: 10/2~10/31 대비)
  const changeLabel = previous ? formatCompareRange(previous, today) : '';
  // 확정 예약(이용·예정·노쇼) 중 초임박 구간(시작 3시간 이내)에 들어온 비율
  const confirmedCount = summary
    ? summary.completedCount + summary.upcomingCount + (summary.noShowCount ?? 0)
    : 0;
  const lastMinuteShare = confirmedCount
    ? Math.round((summary.lastMinuteCount / confirmedCount) * 1000) / 10
    : null;

  return (
    <section className="b-stack">
      <Panel padded className={`b-flex ${styles.filters}`}>
        <div className="b-options" role="group" aria-label="기간 빠른 선택">
          {PRESETS.map((preset) => (
            <button
              key={preset.key}
              type="button"
              className={activePreset === preset.key ? 'is-selected' : ''}
              aria-pressed={activePreset === preset.key}
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
            value={shown.from}
            min={current?.serviceStartDate}
            onChange={(event) =>
              event.target.value && setPeriod({ from: event.target.value, to: shown.to })
            }
          />
        </label>
        <label>
          끝{' '}
          <input
            type="date"
            value={shown.to}
            min={shown.from}
            onChange={(event) =>
              event.target.value && setPeriod({ from: shown.from || today, to: event.target.value })
            }
          />
        </label>
      </Panel>

      <p className={styles.notice}>
        시술일 기준 확정·취소 완료 예약입니다. 예약 시점 가격 기준이며, 실제 방문 여부와 현장
        할인·추가 결제에 따라 실제 매출과 다를 수 있어요.
        {previous && ` 비교는 직전 같은 길이 기간(${previous.from}~${previous.to}) 기준이에요.`}
      </p>

      {periodError && (
        <Alert tone="error" role="alert">
          {periodError}
        </Alert>
      )}
      {!periodError && status === 'failed' && isCurrentPeriod && (
        <Alert tone="error" role="alert" className={styles.error}>
          <span>통계를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</span>
          <Button size="small" type="button" onClick={() => loadStats(period)}>
            다시 시도
          </Button>
        </Alert>
      )}
      {waiting && status !== 'failed' && <Spinner />}

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
          <div className={`b-stat-grid ${styles.kpis}`}>
            <StatCard
              label="시술 매출 (추정)"
              value={formatAmount(summary.completedAmount)}
              unit="원"
              change={changeOf((item) => item.completedAmount)}
              changeLabel={changeLabel}
              notes={['끝난 확정 예약 기준']}
            />
            <StatCard
              label="시술 건수"
              value={summary.completedCount}
              unit="건"
              change={changeOf((item) => item.completedCount)}
              changeLabel={changeLabel}
            />
            <StatCard
              label="객단가"
              value={summary.averageAmount === null ? '-' : formatAmount(summary.averageAmount)}
              unit={summary.averageAmount === null ? undefined : '원'}
              change={changeOf((item) => item.averageAmount)}
              changeLabel={changeLabel}
              notes={['시술 매출 ÷ 시술 건수']}
            />
            <StatCard
              label="예정 예약 금액"
              value={formatAmount(summary.upcomingAmount)}
              unit="원"
              notes={[`아직 끝나지 않은 확정 예약 ${summary.upcomingCount}건`]}
            />
            <StatCard
              label="고객 취소율"
              value={formatRate(summary.customerCancelRate)}
              change={changeOf((item) => item.customerCancelRate, {
                isRate: true,
                higherIsBetter: false,
              })}
              changeLabel={changeLabel}
              notes={[
                `고객 취소 ${summary.customerCancelCount}건 · 매장 사유 ${summary.shopFaultCancelCount}건`,
                `환불되지 않은 예약금 ${formatMoney(summary.nonRefundedDepositAmount)}`,
                `노쇼 ${summary.noShowCount ?? 0}건 · 노쇼율 ${formatRate(summary.noShowRate)}`,
              ]}
            />
            <StatCard
              label="취소 후 다시 채운 시간"
              value={formatRate(summary.rebookRate)}
              change={changeOf((item) => item.rebookRate, { isRate: true })}
              changeLabel={changeLabel}
              notes={
                summary.cancelledMinutes > 0
                  ? [
                      `취소로 빈 ${formatMinutes(summary.cancelledMinutes)} 중 ${formatMinutes(summary.rebookedMinutes)}을 다시 채웠어요`,
                      `그 시간에 다시 들어온 예약 ${formatMoney(summary.rebookedBookingAmount)}`,
                    ]
                  : ['이 기간에는 취소로 빈 시간이 없었어요']
              }
            />
            <StatCard
              label="재방문율"
              value={formatRate(summary.revisitRate)}
              change={changeOf((item) => item.revisitRate, { isRate: true })}
              changeLabel={changeLabel}
              notes={[
                `이용 고객 ${summary.customerCount}명 · 신규 ${summary.newCustomerCount} · 재방문 ${summary.returningCustomerCount}`,
              ]}
              action={<Link to={ROUTES.OWNER_CUSTOMERS}>고객 관리 →</Link>}
            />
            <StatCard
              label="초임박 예약 매출"
              value={formatAmount(summary.lastMinuteAmount)}
              unit="원"
              change={changeOf((item) => item.lastMinuteAmount)}
              changeLabel={changeLabel}
              notes={[
                `시술 시작 3시간 내 예약 ${summary.lastMinuteCount}건 · 전체의 ${formatRate(lastMinuteShare)}`,
              ]}
              action={<Link to={ROUTES.OWNER_MENUS}>초임박가 설정 →</Link>}
            />
          </div>

          <Panel as="section" padded className="b-stack">
            <div className={`b-row ${styles.chartHeader}`}>
              <h2>{monthly ? '월별 추이' : '날짜별 추이'}</h2>
              <div className="b-options" role="group" aria-label="그래프 지표">
                {METRICS.map((item) => (
                  <button
                    key={item.key}
                    type="button"
                    className={metricKey === item.key ? 'is-selected' : ''}
                    aria-pressed={metricKey === item.key}
                    onClick={() => setMetricKey(item.key)}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>
            <DailyBarChart
              days={current.trend}
              getValue={metric.getValue}
              formatTick={metric.formatTick}
              minStep={metric.minStep}
              describe={describeRow}
              getKey={(row) => row.periodStart}
              getLabel={(row) =>
                monthly ? formatShortMonth(row.periodStart) : formatShortDate(row.periodStart)
              }
              getTitle={(row) =>
                monthly ? `${row.periodStart} ~ ${row.periodEnd}` : row.periodStart
              }
              label={`${summary.from}부터 ${summary.to}까지 ${monthly ? '월별' : '날짜별'} ${metric.label}`}
            />
            {monthly && <p className={styles.hint}>62일이 넘는 기간은 월별로 묶어 보여 드려요.</p>}
            <details className={styles.table}>
              <summary>표로 보기</summary>
              <div className="b-table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>{monthly ? '기간' : '날짜'}</th>
                      <th>시술</th>
                      <th>시술 매출 (추정)</th>
                      <th>예정</th>
                      <th>취소</th>
                    </tr>
                  </thead>
                  <tbody>
                    {current.trend.map((row) => (
                      <tr key={row.periodStart}>
                        <td>
                          {monthly ? `${row.periodStart} ~ ${row.periodEnd}` : row.periodStart}
                        </td>
                        <td>{row.completedCount}건</td>
                        <td>{formatMoney(row.completedAmount)}</td>
                        <td>
                          {row.upcomingCount}건 · {formatMoney(row.upcomingAmount)}
                        </td>
                        <td>{row.cancelledCount}건</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </details>
          </Panel>

          <div className={styles.twoColumns}>
            <Panel as="section" padded className="b-stack">
              <h2>요일별 예약</h2>
              <WeekdayBars weekdays={current.byWeekday} from={summary.from} to={summary.to} />
            </Panel>
            <Panel as="section" padded className="b-stack">
              <h2>시술별 비중</h2>
              {current.treatments.length ? (
                <TreatmentBars treatments={current.treatments} />
              ) : (
                <EmptyState as="p">이 기간에 확정된 예약이 없습니다.</EmptyState>
              )}
            </Panel>
          </div>

          <Panel as="section" padded className="b-stack">
            <h2>예약이 차 있는 시간</h2>
            {current.heatmap.length ? (
              <OccupancyHeatmap
                // 기간을 바꾸면 고른 칸을 그 기간의 가장 바쁜 시간으로 다시 잡는다
                key={`${summary.from}-${summary.to}`}
                cells={current.heatmap}
                from={summary.from}
                to={summary.to}
              />
            ) : (
              <EmptyState as="p">이 기간에 확정된 예약이 없습니다.</EmptyState>
            )}
            <p className={styles.hint}>
              색은 그 시간대 1시간 중 예약이 찬 비율(평균)이에요. 칸을 누르면 숫자를 볼 수 있어요.
              기간 안 모든 날짜 기준이라 휴무일이 있으면 실제보다 조금 낮게 나와요.
            </p>
          </Panel>
        </>
      )}
    </section>
  );
}
