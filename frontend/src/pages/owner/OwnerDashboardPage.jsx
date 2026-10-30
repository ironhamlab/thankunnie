import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { reservationStatusOf } from '@/constants/reservationStatus';
import { useOwnerDashboardStore } from '@/store/useOwnerDashboardStore';
import { useShopStore } from '@/store/useShopStore';
import { formatMinutes, formatTimeRange } from '@/utils/ownerDashboardFormat';
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

// 빈 시간대는 카드에 앞의 몇 개만 보여 준다
const MAX_RANGES_ON_CARD = 3;

/**
 * 사장님 메인 (사장님 센터 첫 화면). 매장 요약, 오늘 운영 현황, 오늘 예약, 매출·예약 통계.
 */
export default function OwnerDashboardPage() {
  const shop = useShopStore((state) => state.shop);
  const dashboard = useOwnerDashboardStore((state) => state.dashboard);
  const todayReservations = useOwnerDashboardStore((state) => state.todayReservations);
  const status = useOwnerDashboardStore((state) => state.status);
  const loadDashboard = useOwnerDashboardStore((state) => state.loadDashboard);

  // 매장 정보는 사장님 센터 레이아웃(OwnerLayout)이 불러온다
  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

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

      {status === 'failed' && (
        <Alert tone="error" role="alert" className={styles.error}>
          <span>오늘 운영 현황을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</span>
          <Button size="small" type="button" onClick={loadDashboard}>
            다시 시도
          </Button>
        </Alert>
      )}
      {!dashboard && status === 'loading' && <Spinner />}

      {dashboard && (
        <>
          <section className="b-stack">
            <h2>오늘 운영 현황</h2>
            <div className={`b-stat-grid ${styles.todayGrid}`}>
              <TodayAvailableCard today={dashboard.today} />
              <StatCard
                label="오늘 예약"
                values={[
                  { label: '남은 예약', value: dashboard.today.upcomingCount, unit: '건' },
                  { label: '확정 예약', value: dashboard.today.confirmedCount, unit: '건' },
                ]}
              />
            </div>
          </section>

          <TodayReservations reservations={todayReservations} />
        </>
      )}

      <StatsSection />
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
  const shown = ranges.slice(0, MAX_RANGES_ON_CARD).map(formatTimeRange).join(' · ');
  const more =
    ranges.length > MAX_RANGES_ON_CARD ? ` 외 ${ranges.length - MAX_RANGES_ON_CARD}개` : '';
  return (
    <StatCard
      label="오늘 남은 빈 시간"
      value={formatMinutes(today.availableMinutes)}
      notes={[ranges.length ? `${shown}${more}` : '남은 시간이 모두 찼어요']}
    />
  );
}

function TodayReservations({ reservations }) {
  return (
    <Panel as="section" padded className="b-stack">
      <div className="b-row">
        <h2>오늘 예약</h2>
        <Link to={ROUTES.OWNER_BOOKINGS}>예약 관리에서 전체 보기 →</Link>
      </div>
      {reservations.length ? (
        <ul className={styles.timeline}>
          {reservations.map((item) => {
            const reservationStatus = reservationStatusOf(item.status);
            return (
              <li key={item.reservationId}>
                <strong className={styles.time}>
                  {timePart(item.startsAt)}~{timePart(item.endsAt)}
                </strong>
                <span>{item.treatmentName}</span>
                <span className="b-muted">{item.customerName}</span>
                <Badge className={reservationStatus.tone}>{reservationStatus.label}</Badge>
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
      )}
    </Panel>
  );
}
