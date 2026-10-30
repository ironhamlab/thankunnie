import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { PAYMENT_STATUS, reservationStatusOf } from '@/constants/reservationStatus';
import { formatMoney, formatReservationTime } from '@/utils/reservationFormat';
import { todayKst } from '@/utils/scheduleDate';
import { useDesignStore } from '@/store/useDesignStore';
import { useNotificationStore } from '@/store/useNotificationStore';
import { useReservationStore } from '@/store/useReservationStore';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import EmptyState from '@/components/common/EmptyState';
import Alert from '@/components/common/Alert';
import MyNotificationsPage from './MyNotificationsPage';
import styles from './CustomerProfilePage.module.css';

// 내 예약 '이용 예정' 탭과 같은 조건. 시작 시각 오름차순이라 첫 건이 가장 가까운 예약이다
const upcomingFilters = () => ({
  from: todayKst(),
  statuses: ['HOLD', 'CONFIRMED'],
  page: 0,
  size: 1,
});

export default function CustomerProfilePage() {
  const profile = useDesignStore((s) => s.profile);
  const favorites = useDesignStore((s) => s.favorites);
  const loadReservations = useReservationStore((s) => s.loadReservations);
  const reservations = useReservationStore((s) => s.reservations);
  const pageInfo = useReservationStore((s) => s.pageInfo);
  const listStatus = useReservationStore((s) => s.listStatus);
  const listError = useReservationStore((s) => s.listError);
  // 구독 목록은 아래 알림 영역의 SubscriptionList가 불러온다
  const subscriptionCount = useNotificationStore((s) =>
    s.listStatus === 'succeeded' ? s.subscriptions.length : '-',
  );

  useEffect(() => {
    loadReservations('my', upcomingFilters());
  }, [loadReservations]);

  const nearest = listStatus === 'succeeded' ? reservations[0] : null;
  const nearestStatus = nearest && reservationStatusOf(nearest.status);

  return (
    <div className={`b-container b-page ${styles.root}`}>
      <Panel as="section" padded className={styles.summary}>
        <div className="b-row">
          <div className="b-flex">
            <div className={styles.avatar}>언니</div>
            <div>
              <div className="b-flex">
                <h1>{profile.name}</h1>
              </div>
              <p>가입일: 2025.01.15 · 최근 접속: 오늘 12:40</p>
            </div>
          </div>
        </div>
        <div className={`b-stat-grid ${styles.stats}`}>
          {[
            ['방문 예정 예약', listStatus === 'succeeded' ? pageInfo.totalElements : '-', '건'],
            ['빈자리 알림 조건', subscriptionCount, '개'],
            ['찜한 뷰티샵', favorites.length, '곳'],
            ['미확인 알림', profile.alerts ? 2 : 0, '건'],
          ].map(([label, value, unit]) => (
            <div key={label}>
              <small>{label}</small>
              <strong>
                {value}
                <span>{unit}</span>
              </strong>
            </div>
          ))}
        </div>
      </Panel>
      {listStatus === 'loading' && (
        <p role="status" className={styles.nearestState}>
          다가오는 예약을 불러오는 중입니다.
        </p>
      )}
      {listStatus === 'failed' && (
        <div className={`b-row ${styles.nearestState}`}>
          <Alert as="p" tone="error">
            {listError.message}
          </Alert>
          <Button as={Link} to={ROUTES.MY_RESERVATIONS}>
            내 예약 보기
          </Button>
        </div>
      )}
      {nearest && (
        <Panel as="section" padded className={styles.nearest} aria-label="가장 가까운 예약">
          <div>
            <div className="b-flex">
              <Badge>◷ {formatReservationTime(nearest.startsAt, nearest.endsAt)}</Badge>
              <Badge className={nearestStatus.tone}>{nearestStatus.label}</Badge>
              <small className="b-muted">예약번호 {nearest.reservationId}</small>
            </div>
            <h2>{nearest.shopName}</h2>
            <p>{nearest.treatmentName}</p>
            <div className={`b-flex ${styles.nearestPrices}`}>
              <span>
                예약금: <strong>{formatMoney(nearest.deposit)}</strong>
                {nearest.paymentStatus && ` (${PAYMENT_STATUS[nearest.paymentStatus]})`}
              </span>
              <span>
                현장 결제 잔금: <strong>{formatMoney(nearest.balance)}</strong>
              </span>
            </div>
          </div>
          <div className="b-flex">
            <Button as={Link} variant="primary" to={ROUTES.MY_RESERVATIONS}>
              전체 예약 보기
            </Button>
          </div>
        </Panel>
      )}
      {listStatus === 'succeeded' && !nearest && (
        <EmptyState className={`b-panel ${styles.nearestState}`}>
          <h2>다가오는 예약이 없어요</h2>
          <div className="b-flex">
            <Button as={Link} variant="primary" to={ROUTES.HOME}>
              지금 빈자리 찾아보기
            </Button>
            <Button as={Link} to={ROUTES.MY_RESERVATIONS}>
              지난 예약 보기
            </Button>
          </div>
        </EmptyState>
      )}
      <MyNotificationsPage embedded />
    </div>
  );
}
