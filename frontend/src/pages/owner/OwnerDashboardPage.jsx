import { useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { ownerReservationStatusOf } from '@/constants/reservationStatus';
import { useOwnerDashboardStore } from '@/store/useOwnerDashboardStore';
import { useShopStore } from '@/store/useShopStore';
import {
  describeChange,
  formatAmount,
  formatCompareRange,
  formatMinutes,
  formatTimeRange,
} from '@/utils/ownerDashboardFormat';
import { formatMoney } from '@/utils/reservationFormat';
import { timePart } from '@/utils/scheduleDate';
import Alert from '@/components/common/Alert';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import Spinner from '@/components/common/Spinner';
import RepresentativeImage from './components/RepresentativeImage';
import StatCard from './components/StatCard';
import StatsSection from './components/StatsSection';
import styles from './OwnerDashboardPage.module.css';

const VIEWS = [
  { key: 'today', label: '오늘' },
  { key: 'stats', label: '매출·예약 분석' },
];

/**
 * 매장 센터 운영 현황. 오늘(카드·오늘 예약)과 매출·예약 분석을 탭으로 나눈다.
 * 탭은 주소(?view=stats)에 남겨 새로고침·뒤로 가기에도 유지한다. 분석 탭을 열 때만 통계를 불러온다.
 */
export default function OwnerDashboardPage() {
  const shop = useShopStore((state) => state.shop);
  const [searchParams, setSearchParams] = useSearchParams();
  const view = searchParams.get('view') === 'stats' ? 'stats' : 'today';

  const selectView = (key) => {
    setSearchParams(key === 'today' ? {} : { view: key }, { replace: true });
  };

  return (
    <div className="b-stack">
      {shop && (
        <Panel as="section" padded className={`b-row ${styles.profile}`}>
          <div className="b-flex">
            <RepresentativeImage
              kind="shop"
              imageUrl={shop.imageUrl}
              name={shop.name}
              className="b-thumb"
            />
            <div>
              <h2>{shop.name}</h2>
              <p>{[shop.address, shop.addressDetail].filter(Boolean).join(' ')}</p>
            </div>
          </div>
          <Button as={Link} variant="primary" to={ROUTES.OWNER_SHOP}>
            매장 정보 관리 →
          </Button>
        </Panel>
      )}

      <div className={styles.tabs} role="tablist" aria-label="운영 현황 보기">
        {VIEWS.map((item) => (
          <button
            key={item.key}
            type="button"
            role="tab"
            id={`owner-view-${item.key}`}
            aria-selected={view === item.key}
            aria-controls={`owner-panel-${item.key}`}
            onClick={() => selectView(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div role="tabpanel" id={`owner-panel-${view}`} aria-labelledby={`owner-view-${view}`}>
        {view === 'today' ? (
          <TodayView onOpenStats={() => selectView('stats')} />
        ) : (
          <StatsSection />
        )}
      </div>
    </div>
  );
}

function TodayView({ onOpenStats }) {
  const dashboard = useOwnerDashboardStore((state) => state.dashboard);
  const dashboardStatus = useOwnerDashboardStore((state) => state.dashboardStatus);
  const todayReservations = useOwnerDashboardStore((state) => state.todayReservations);
  const reservationsStatus = useOwnerDashboardStore((state) => state.reservationsStatus);
  const monthSummary = useOwnerDashboardStore((state) => state.monthSummary);
  const loadDashboard = useOwnerDashboardStore((state) => state.loadDashboard);
  const loadToday = useOwnerDashboardStore((state) => state.loadToday);
  const loadTodayReservations = useOwnerDashboardStore((state) => state.loadTodayReservations);

  // 매장 정보는 매장 센터 레이아웃(OwnerLayout)이 불러온다
  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  return (
    <div className="b-stack">
      <section className="b-stack" aria-label="오늘 요약">
        {dashboardStatus === 'failed' && (
          <LoadError message="오늘 운영 현황을 불러오지 못했어요." onRetry={loadToday} />
        )}
        {dashboardStatus === 'loading' && <Spinner />}
        {dashboardStatus === 'succeeded' && dashboard && (
          <TodayCards
            dashboard={dashboard}
            monthSummary={monthSummary}
            todayReservations={reservationsStatus === 'succeeded' ? todayReservations : null}
          />
        )}
      </section>

      <TodayReservations
        reservations={todayReservations}
        status={reservationsStatus}
        onRetry={loadTodayReservations}
      />

      <Panel as="section" padded className={`b-row ${styles.statsLink}`}>
        <div>
          <h2>매출·예약 흐름이 궁금하다면</h2>
          <p>최근 1년 금액, 피크 요일·시간, 신규·재방문 고객을 확인해 보세요.</p>
        </div>
        <Button type="button" variant="soft" onClick={onOpenStats}>
          매출·예약 분석 보기 →
        </Button>
      </Panel>
    </div>
  );
}

// 오늘 예약 목록에서 아직 시작 전인 가장 빠른 확정 예약 (노쇼 표시 제외)
function findNextReservation(reservations) {
  const now = Date.now();
  return reservations
    .filter(
      (item) => item.status === 'CONFIRMED' && !item.noShow && Date.parse(item.startsAt) > now,
    )
    .sort((a, b) => Date.parse(a.startsAt) - Date.parse(b.startsAt))[0];
}

function TodayCards({ dashboard, monthSummary, todayReservations }) {
  const { today, monthToDate } = dashboard;
  const change = describeChange(monthToDate.completedAmount, monthToDate.previousCompletedAmount);
  // 분석 탭과 같이 비교한 기간을 날짜로 적는다 (예: 10/1~10/4 대비)
  // 오늘 예약 목록을 받았을 때만 다음 예약을 보여 준다
  const next = todayReservations ? findNextReservation(todayReservations) : null;
  const changeLabel = formatCompareRange(
    { from: monthToDate.previousFrom, to: monthToDate.previousTo },
    dashboard.date,
  );
  return (
    <div className={`b-stat-grid ${styles.todayGrid}`}>
      <StatCard
        label="오늘 예약"
        values={[
          { label: '남은', value: today.upcomingCount, unit: '건' },
          { label: '오늘 전체', value: today.confirmedCount, unit: '건' },
        ]}
        notes={todayReservations && !next ? ['오늘 남은 예약이 없어요'] : []}
      >
        {/* 다음 예약: 첫 줄 시간·고객, 둘째 줄 시술명(길면 말줄임) */}
        {next && (
          <div className={styles.next}>
            <p>
              다음 {formatTimeRange(next)} <strong>{next.customerName}</strong>
            </p>
            <p className={styles.treatment}>{next.treatmentName}</p>
          </div>
        )}
      </StatCard>
      <TodayAvailableCard today={today} />
      <StatCard
        label="내일 확정 예약"
        value={dashboard.tomorrowConfirmedCount}
        unit="건"
        action={<Link to={ROUTES.OWNER_BOOKINGS}>예약 관리 →</Link>}
      />
      <StatCard
        label="이번 달 시술 매출"
        value={formatAmount(monthToDate.completedAmount)}
        unit="원"
        change={change}
        changeLabel={changeLabel}
        notes={[
          monthSummary &&
            `시술 ${monthSummary.completedCount}건${
              monthSummary.averageAmount
                ? ` · 객단가 ${formatMoney(monthSummary.averageAmount)}`
                : ''
            }`,
          change
            ? `지난달 같은 기간 ${formatMoney(monthToDate.previousCompletedAmount)}`
            : '지난달 같은 기간 이용 내역이 없어요',
        ].filter(Boolean)}
      />
      <StatCard
        label="우리 매장 찜 고객"
        value={dashboard.favoriteCustomerCount}
        unit="명"
        notes={['빈자리를 열면 알림을 받아요']}
        action={<Link to={ROUTES.OWNER_VACANCIES}>빈자리 열기 →</Link>}
      />
    </div>
  );
}

// 휴무(열린 시간 없음)와 만석(열렸지만 남은 빈 시간 없음)을 구분해 보여 준다
function TodayAvailableCard({ today }) {
  if (today.closed) {
    return (
      <StatCard
        label="오늘 남은 빈 시간"
        value="휴무"
        notes={['오늘은 예약받는 시간이 없어요']}
        action={<Link to={ROUTES.OWNER_SCHEDULE}>영업시간 설정하기 →</Link>}
      />
    );
  }
  const ranges = today.availableRanges;
  return (
    <StatCard
      label="오늘 남은 빈 시간"
      value={formatMinutes(today.availableMinutes)}
      notes={ranges.length ? [] : ['남은 시간이 모두 찼어요']}
    >
      {/* 남은 시간대를 모두 칩으로 보여 준다. 바로가기(빈자리 열기)는 찜 고객 카드에 둔다 */}
      {ranges.length > 0 && (
        <ul className={styles.ranges}>
          {ranges.map((range) => (
            <li key={range.startsAt}>{formatTimeRange(range)}</li>
          ))}
        </ul>
      )}
    </StatCard>
  );
}

// 영역별 불러오기 실패 안내. 서버 메시지 대신 매장이 이해할 문구와 다시 시도를 보여 준다
function LoadError({ message, onRetry }) {
  return (
    <Alert tone="error" role="alert" className={styles.error}>
      <span>{message} 잠시 후 다시 시도해 주세요.</span>
      <Button size="small" type="button" onClick={onRetry}>
        다시 시도
      </Button>
    </Alert>
  );
}

function TodayReservations({ reservations, status, onRetry }) {
  return (
    <Panel as="section" padded className="b-stack">
      <div className="b-row">
        <h2>오늘 예약</h2>
        <Link to={ROUTES.OWNER_BOOKINGS}>예약 관리에서 전체 보기 →</Link>
      </div>
      {status === 'failed' && (
        <LoadError message="오늘 예약을 불러오지 못했어요." onRetry={onRetry} />
      )}
      {status === 'loading' && <Spinner />}
      {status === 'succeeded' && <TodayReservationList reservations={reservations} />}
    </Panel>
  );
}

// 고객 번호가 있으면 상세까지 열고, 없으면 이름으로 찾는다
const customerPath = (item) => {
  const params = new URLSearchParams({ keyword: item.customerName });
  if (item.customerId) params.set('customer', String(item.customerId));
  return `${ROUTES.OWNER_CUSTOMERS}?${params}`;
};

function TodayReservationList({ reservations }) {
  return reservations.length ? (
    <ul className={styles.timeline}>
      {reservations.map((item) => {
        const reservationStatus = ownerReservationStatusOf(item);
        return (
          <li key={item.reservationId}>
            <strong className={styles.time}>
              {timePart(item.startsAt)}~{timePart(item.endsAt)}
            </strong>
            <span className={styles.treatment}>{item.treatmentName}</span>
            {/* 고객 관리에서 이 고객의 방문 이력·메모를 바로 연다 */}
            <Link className={`b-muted ${styles.customer}`} to={customerPath(item)}>
              {item.customerName}
            </Link>
            <Badge className={`${reservationStatus.tone} ${styles.badge}`}>
              {reservationStatus.label}
            </Badge>
          </li>
        );
      })}
    </ul>
  ) : (
    <div className={styles.empty}>
      <strong>오늘 잡힌 예약이 없어요</strong>
      <p>빈자리를 열어 두면 고객이 바로 예약할 수 있어요.</p>
      <Button as={Link} size="small" to={ROUTES.OWNER_VACANCIES}>
        빈자리 열기·닫기
      </Button>
    </div>
  );
}
