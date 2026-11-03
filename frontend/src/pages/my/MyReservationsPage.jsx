import { useEffect, useRef, useState } from 'react';
import { generatePath, Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import {
  PAST_RESERVATION_STATUS,
  PAYMENT_STATUS,
  reservationStatusOf,
} from '@/constants/reservationStatus';
import { useReservationStore } from '@/store/useReservationStore';
import { useShopDetailStore } from '@/store/useShopDetailStore';
import { formatReservationTime } from '@/utils/reservationFormat';
import { addDays, todayKst } from '@/utils/scheduleDate';
import ReservationDetailPanel from '@/components/reservation/ReservationDetailPanel';
import ReservationPagination from '@/components/reservation/ReservationPagination';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import Panel from '@/components/common/Panel';
import EmptyState from '@/components/common/EmptyState';
import VisitShopLocation from './components/VisitShopLocation';
import styles from './MyReservationsPage.module.css';

// 탭별 조회 조건. 날짜는 시술 시작일 기준이다
const TABS = [
  {
    key: 'upcoming',
    label: '이용 예정',
    filters: () => ({ from: todayKst(), statuses: ['HOLD', 'CONFIRMED'] }),
  },
  {
    key: 'past',
    label: '지난 이용',
    filters: () => ({ to: addDays(todayKst(), -1), statuses: ['CONFIRMED'] }),
    // 확정 상태로 시작일이 지난 예약만 오므로 이용을 마친 것으로 보여준다
    displayStatus: PAST_RESERVATION_STATUS,
  },
  {
    key: 'cancelled',
    label: '취소',
    filters: () => ({ statuses: ['CANCEL_PENDING', 'CANCELLED'] }),
  },
];
const PAGE_SIZE = 10;
// 카드가 세로로 쌓이는 폭 (MyReservationsPage.module.css 와 같게 둔다)
const CARD_LAYOUT_QUERY = '(max-width: 640px)';

const cancelPath = (reservationId) => generatePath(ROUTES.MY_RESERVATION_CANCEL, { reservationId });

/**
 * 고객 내 예약 (B-F5). 여러 매장 예약을 이용 예정·지난 이용·취소 탭으로 나눠 보고, 고른 예약의 결제·취소 상태를 창에서 본다.
 */
export default function MyReservationsPage() {
  const loadReservations = useReservationStore((state) => state.loadReservations);
  const loadReservation = useReservationStore((state) => state.loadReservation);
  const reservations = useReservationStore((state) => state.reservations);
  const pageInfo = useReservationStore((state) => state.pageInfo);
  const listStatus = useReservationStore((state) => state.listStatus);
  const listError = useReservationStore((state) => state.listError);
  const loadShop = useShopDetailStore((state) => state.loadShop);
  const clearShop = useShopDetailStore((state) => state.clearShop);
  const [tabKey, setTabKey] = useState('upcoming');
  const [page, setPage] = useState(0);
  // 이 화면에서 목록을 한 번 받은 뒤에는, 다음 목록을 받는 동안에도 목록을 남겨 화면이 튀지 않게 한다
  const [hasLoadedList, setHasLoadedList] = useState(false);
  const [isDetailOpen, setIsDetailOpen] = useState(false);
  const listRef = useRef(null);
  const tab = TABS.find((item) => item.key === tabKey);

  useEffect(() => {
    const { filters } = TABS.find((item) => item.key === tabKey);
    loadReservations('my', { ...filters(), page, size: PAGE_SIZE }).then(() =>
      setHasLoadedList(true),
    );
  }, [loadReservations, tabKey, page]);

  const handleTabChange = (key) => {
    setTabKey(key);
    setPage(0);
  };

  const showDetail = (item) => {
    loadReservation('my', item.reservationId);
    loadShop(item.shopId);
    setIsDetailOpen(true);
  };

  const closeDetail = () => {
    setIsDetailOpen(false);
    clearShop();
  };

  // 폰은 목록이 화면보다 길어 페이지를 넘겨도 스크롤이 아래에 남으므로 목록 맨 위로 올린다
  const changePage = (next) => {
    // 받는 동안에는 이전 페이지 정보가 남아 있어, 더 넘기면 없는 페이지를 요청할 수 있다
    if (listStatus === 'loading') {
      return;
    }
    setPage(next);
    if (window.matchMedia(CARD_LAYOUT_QUERY).matches) {
      listRef.current?.scrollIntoView({ block: 'start' });
    }
  };

  const isEmpty = listStatus === 'succeeded' && !reservations.length;

  return (
    <div className="b-container b-page">
      <div className="b-row b-title-row">
        <h1>
          내 예약 <Badge>{pageInfo.totalElements}건</Badge>
        </h1>
      </div>
      <div className="b-options b-tabs" role="group" aria-label="예약 구분">
        {TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            aria-pressed={tabKey === item.key}
            className={tabKey === item.key ? 'is-selected' : ''}
            onClick={() => handleTabChange(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>

      {listStatus === 'loading' && !hasLoadedList && <p role="status">예약을 불러오는 중입니다.</p>}
      {listStatus === 'failed' && <p role="alert">{listError.message}</p>}
      {isEmpty && (
        <EmptyState className="b-panel">
          <h2>예약 내역이 없어요</h2>
          <p>실시간 빈자리를 찾아보세요.</p>
          <Button as={Link} variant="primary" to={ROUTES.HOME}>
            빈자리 탐색하기
          </Button>
        </EmptyState>
      )}
      {listStatus !== 'failed' && hasLoadedList && !isEmpty && (
        <div className="b-stack" ref={listRef}>
          <div
            className={`b-stack ${styles.cards} ${pageInfo.totalPages > 1 ? styles.fullPage : ''}`}
          >
            {reservations.map((item) => {
              const status = tab.displayStatus ?? reservationStatusOf(item.status);
              return (
                <Panel
                  as="button"
                  className={`b-booking-card ${styles.card}`}
                  key={item.reservationId}
                  type="button"
                  onClick={() => showDetail(item)}
                >
                  <span className={`b-flex ${styles.status}`}>
                    <Badge className={status.tone}>{status.label}</Badge>
                    {item.paymentStatus && (
                      <small className="b-muted">{PAYMENT_STATUS[item.paymentStatus]}</small>
                    )}
                  </span>
                  <strong className={styles.treatment}>{item.treatmentName}</strong>
                  <span className={styles.shop}>{item.shopName}</span>
                  <span className={styles.time}>
                    {formatReservationTime(item.startsAt, item.endsAt)}
                  </span>
                  <span className={styles.chevron} aria-hidden="true">
                    ›
                  </span>
                </Panel>
              );
            })}
          </div>
          <div className={styles.pager}>
            <ReservationPagination page={page} pageInfo={pageInfo} onPageChange={changePage} />
          </div>
        </div>
      )}
      {isDetailOpen && (
        <Dialog title="예약 상세" className={styles.detailDialog} onClose={closeDetail}>
          <VisitShopLocation />
          <ReservationDetailPanel
            viewer="customer"
            cancelPath={cancelPath}
            displayStatus={tab.displayStatus}
            hideHeading
          />
        </Dialog>
      )}
    </div>
  );
}
