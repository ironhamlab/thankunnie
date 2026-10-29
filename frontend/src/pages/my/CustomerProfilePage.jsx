import { useEffect, useState } from 'react';
import { generatePath, Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { PAYMENT_STATUS, reservationStatusOf } from '@/constants/reservationStatus';
import { SERVICES } from '@/utils/demoData';
import { formatMoney, formatReservationTime } from '@/utils/reservationFormat';
import { todayKst } from '@/utils/scheduleDate';
import { useDesignStore } from '@/store/useDesignStore';
import { useNotificationStore } from '@/store/useNotificationStore';
import { useReservationStore } from '@/store/useReservationStore';
import Asset from '@/components/common/Asset';
import Dialog from '@/components/common/Dialog';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import Notice from '@/components/common/Notice';
import EmptyState from '@/components/common/EmptyState';
import Alert from '@/components/common/Alert';
import SubscriptionSettings from './components/SubscriptionSettings';
import styles from './CustomerProfilePage.module.css';

// 예약 전체 목록은 내 예약 화면(MY_RESERVATIONS)에서만 보여주고, 마이페이지는 요약만 둔다
const tabs = ['찜한 매장', '빈자리 알림 조건 설정', '전체 알림함'];
// 실제 서버 데이터로 그리는 탭. 나머지는 가상 데이터 시연이다
const LIVE_TABS = ['빈자리 알림 조건 설정'];

// 내 예약 '이용 예정' 탭과 같은 조건. 시작 시각 오름차순이라 첫 건이 가장 가까운 예약이다
const upcomingFilters = () => ({
  from: todayKst(),
  statuses: ['HOLD', 'CONFIRMED'],
  page: 0,
  size: 1,
});

export default function CustomerProfilePage() {
  const profile = useDesignStore((s) => s.profile);
  const saveProfile = useDesignStore((s) => s.saveProfile);
  const favorites = useDesignStore((s) => s.favorites);
  const toggleFavorite = useDesignStore((s) => s.toggleFavorite);
  const loadReservations = useReservationStore((s) => s.loadReservations);
  const reservations = useReservationStore((s) => s.reservations);
  const pageInfo = useReservationStore((s) => s.pageInfo);
  const listStatus = useReservationStore((s) => s.listStatus);
  const listError = useReservationStore((s) => s.listError);
  const loadSubscriptions = useNotificationStore((s) => s.loadSubscriptions);
  const subscriptionCount = useNotificationStore((s) =>
    s.listStatus === 'succeeded' ? s.subscriptions.length : '-',
  );
  const [tab, setTab] = useState(tabs[0]);
  const [showFavorites, setShowFavorites] = useState(true);
  const [dialog, setDialog] = useState('');
  const [name, setName] = useState(profile.name);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    loadReservations('my', upcomingFilters());
    loadSubscriptions();
  }, [loadReservations, loadSubscriptions]);

  const nearest = listStatus === 'succeeded' ? reservations[0] : null;
  const nearestStatus = nearest && reservationStatusOf(nearest.status);

  return (
    <div className={`b-container b-page ${styles.root}`}>
      <Notice className={styles.ribbon}>
        <strong>MVP 데모:</strong> 알림영역
      </Notice>
      <Panel as="section" padded className={styles.summary}>
        <div className="b-row">
          <div className="b-flex">
            <div className={styles.avatar}>언니</div>
            <div>
              <div className="b-flex">
                <h1>{profile.name}</h1>
                <Badge tone="neutral">테스트 고객 계정</Badge>
                <Badge>VIP 퍼스트패스</Badge>
              </div>
              <p>가입일: 2025.01.15 · 최근 접속: 오늘 12:40</p>
            </div>
          </div>
          <div className="b-flex">
            <Button
              onClick={() => {
                setName(profile.name);
                setDialog('profile');
              }}
            >
              프로필 수정
            </Button>
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
      <Panel as="nav" className={styles.tabs} aria-label="마이페이지 메뉴">
        {tabs.map((label) => (
          <button
            key={label}
            className={tab === label ? 'is-selected' : ''}
            aria-pressed={tab === label}
            onClick={() => setTab(label)}
          >
            {label}
          </button>
        ))}
      </Panel>
      <section aria-label={tab} className={styles.tabPanel}>
        <div className="b-row b-panel-heading">
          <h2>{tab}</h2>
          {!LIVE_TABS.includes(tab) && <small className="b-muted">가상 데이터 시연</small>}
        </div>
        {tab === tabs[0] && (
          <div className="b-stack">
            {showFavorites && favorites.length ? (
              SERVICES.filter((s) => favorites.includes(s.id)).map((s) => (
                <Panel as="article" padded className="b-row" key={s.id}>
                  <div className="b-flex">
                    <Asset name={s.image} alt={s.shop} className="b-thumb" />
                    <div>
                      <h2>{s.shop}</h2>
                      <p>{s.location}</p>
                      <Link
                        className="b-pink"
                        to={generatePath(ROUTES.SERVICE_DETAIL, { id: s.id })}
                      >
                        매장 상세 보기 →
                      </Link>
                    </div>
                  </div>
                  <Button onClick={() => toggleFavorite(s.id)}>찜 해제</Button>
                </Panel>
              ))
            ) : (
              <EmptyState className="b-panel">
                <h2>찜한 매장이 없어요</h2>
                <Link to={ROUTES.HOME}>마음에 드는 매장을 찾아보세요</Link>
              </EmptyState>
            )}
          </div>
        )}
        {tab === tabs[1] && <SubscriptionSettings />}
        {tab === tabs[2] && (
          <div className="b-stack">
            <Notice>
              <h3>예약 안내</h3>
              <p>방문 예정 예약은 내 예약 상세에서 확인할 수 있습니다.</p>
            </Notice>
            <Notice>
              <h3>빈자리 타임세일</h3>
              <p>
                찜한 매장의 새 빈자리를 탐색해 보세요. 알림 예시이며 실제 메시지는 발송되지
                않습니다.
              </p>
            </Notice>
          </div>
        )}
      </section>
      <div className="b-preview b-row">
        <div>
          <strong>MVP 화면 상태 시뮬레이터 컨트롤러</strong>
          <p>찜 목록의 빈 화면을 확인할 수 있습니다.</p>
        </div>
        <div className="b-options">
          <button
            aria-pressed={!showFavorites}
            onClick={() => {
              setShowFavorites(!showFavorites);
              setTab(tabs[0]);
            }}
          >
            찜 목록 채움 ↔ 비움 토글
          </button>
        </div>
      </div>
      {notice && (
        <Alert as="p" tone="success" role="status">
          {notice}
        </Alert>
      )}
      {dialog && (
        <Dialog
          title={{ profile: '프로필 수정', share: '초대코드 공유' }[dialog]}
          onClose={() => setDialog('')}
        >
          {dialog === 'profile' && (
            <form
              className="b-stack"
              onSubmit={(e) => {
                e.preventDefault();
                if (!name.trim()) return;
                saveProfile({ name: name.trim() });
                setDialog('');
                setNotice('프로필을 저장했습니다.');
              }}
            >
              <label className={styles.alertCondition}>
                닉네임
                <input
                  autoFocus
                  required
                  maxLength={20}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </label>
              <Button variant="primary">프로필 저장</Button>
            </form>
          )}
          {dialog === 'share' && (
            <>
              <p>초대코드: UNNIE23</p>
              <p>{window.location.origin + ROUTES.HOME + '?ref=UNNIE23'}</p>
            </>
          )}
        </Dialog>
      )}
    </div>
  );
}
