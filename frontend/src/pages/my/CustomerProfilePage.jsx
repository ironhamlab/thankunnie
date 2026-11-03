import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { PAYMENT_STATUS, reservationStatusOf } from '@/constants/reservationStatus';
import { formatMoney, formatVisitDate, visitDayLabel } from '@/utils/reservationFormat';
import { isPendingFavorite } from '@/utils/notificationRules';
import { datePart, timePart } from '@/utils/scheduleDate';
import { useKstToday } from '@/hooks/useKstToday';
import { useNotificationStore } from '@/store/useNotificationStore';
import { useReservationStore } from '@/store/useReservationStore';
import { useUserStore } from '@/store/useUserStore';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import EmptyState from '@/components/common/EmptyState';
import Alert from '@/components/common/Alert';
import MyNotificationsPage from './MyNotificationsPage';
import WithdrawDialog from './components/WithdrawDialog';
import styles from './CustomerProfilePage.module.css';

// 내 예약 '이용 예정' 탭과 같은 조건. 시작 시각 오름차순이라 첫 건이 가장 가까운 예약이다
const upcomingFilters = (today) => ({
  from: today,
  statuses: ['HOLD', 'CONFIRMED'],
  page: 0,
  size: 1,
});

export default function CustomerProfilePage() {
  const profile = useUserStore((s) => s.profile);
  const profileStatus = useUserStore((s) => s.profileStatus);
  const profileError = useUserStore((s) => s.profileError);
  const loadProfile = useUserStore((s) => s.loadProfile);
  const resetProfile = useUserStore((s) => s.resetProfile);
  const loadReservations = useReservationStore((s) => s.loadReservations);
  const reservations = useReservationStore((s) => s.reservations);
  const pageInfo = useReservationStore((s) => s.pageInfo);
  const listStatus = useReservationStore((s) => s.listStatus);
  const listError = useReservationStore((s) => s.listError);
  // 구독 목록은 아래 알림 영역의 SubscriptionList가 불러온다
  const subscriptions = useNotificationStore((s) => s.subscriptions);
  const subscriptionStatus = useNotificationStore((s) => s.listStatus);
  const [withdrawOpen, setWithdrawOpen] = useState(false);

  // 로그아웃 뒤 다른 계정에 이전 개인정보가 보이지 않도록 화면을 벗어나면 지운다
  useEffect(() => {
    loadProfile();
    return resetProfile;
  }, [loadProfile, resetProfile]);

  // 자정이 지나면 예약을 다시 불러온다. 재렌더링으로 오늘·내일 표시도 함께 바뀐다
  const today = useKstToday();

  useEffect(() => {
    loadReservations('my', upcomingFilters(today));
  }, [loadReservations, today]);

  // 찜은 희망 시간 없는 업장 구독으로 저장된다. 한 매장에 구독이 여러 건이어도 한 번만 센다
  const favoriteShopIds = new Set(
    subscriptions
      .filter((item) => item.subscriptionType === 'STORE')
      .map((item) => Number(item.shopId)),
  );
  const alertCount = subscriptions.filter((item) => !isPendingFavorite(item)).length;
  const subscriptionsLoaded = subscriptionStatus === 'succeeded';
  const nearest = listStatus === 'succeeded' ? reservations[0] : null;
  const nearestStatus = nearest && reservationStatusOf(nearest.status);

  return (
    <div className={`b-container b-page ${styles.root}`}>
      <Panel as="section" padded className={styles.summary}>
        <div className="b-row">
          <div className="b-flex">
            <div className={styles.avatar}>언니</div>
            <div>
              {profile && (
                <>
                  <h1>{profile.name}</h1>
                  <p>
                    가입일: {datePart(profile.createdAt)} · 휴대폰: {profile.phone}
                  </p>
                </>
              )}
              {profileStatus === 'loading' && (
                <p role="status">회원 정보를 불러오는 중입니다.</p>
              )}
              {profileStatus === 'failed' && (
                <Alert as="p" tone="error">
                  {profileError.message}
                </Alert>
              )}
            </div>
          </div>
        </div>
        <div className={`b-stat-grid ${styles.stats}`}>
          {[
            ['방문 예정 예약', listStatus === 'succeeded' ? pageInfo.totalElements : '-', '건'],
            ['빈자리 알림 조건', subscriptionsLoaded ? alertCount : '-', '개'],
            ['찜한 뷰티샵', subscriptionsLoaded ? favoriteShopIds.size : '-', '곳'],
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
          <div className={styles.nearestWhen}>
            <span className={styles.nearestDay}>{visitDayLabel(nearest.startsAt)}</span>
            <strong className={styles.nearestDate}>{formatVisitDate(nearest.startsAt)}</strong>
            <strong className={styles.nearestTime}>
              {timePart(nearest.startsAt)}
              <small> ~ {timePart(nearest.endsAt)}</small>
            </strong>
          </div>
          <div className={styles.nearestInfo}>
            <div className="b-flex">
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
          <div className={`b-flex ${styles.nearestActions}`}>
            {nearest.status === 'HOLD' && (
              <Button
                as={Link}
                variant="primary"
                to={`${ROUTES.CHECKOUT}?reservationId=${nearest.reservationId}`}
              >
                이어서 결제하기
              </Button>
            )}
            <Button
              as={Link}
              variant={nearest.status === 'HOLD' ? undefined : 'primary'}
              to={ROUTES.MY_RESERVATIONS}
            >
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
      <MyNotificationsPage />
      {/* 실수로 누르지 않도록 페이지 맨 아래에 작은 글씨로 둔다 */}
      <div className={styles.withdraw}>
        <button type="button" onClick={() => setWithdrawOpen(true)}>
          회원탈퇴
        </button>
      </div>
      {withdrawOpen && <WithdrawDialog onClose={() => setWithdrawOpen(false)} />}
    </div>
  );
}
