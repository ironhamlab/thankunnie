import { useEffect, useRef, useState } from 'react';
import { generatePath, Link, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { PAYMENT_STATUS, reservationStatusOf } from '@/constants/reservationStatus';
import { useReservationStore } from '@/store/useReservationStore';
import { useShopDetailStore } from '@/store/useShopDetailStore';
import { formatReservationTime } from '@/utils/reservationFormat';
import ReservationDetailPanel from '@/components/reservation/ReservationDetailPanel';
import ReservationPagination from '@/components/reservation/ReservationPagination';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import Panel from '@/components/common/Panel';
import SlidingIndicator from '@/components/common/SlidingIndicator';
import { useSlidingIndicator } from '@/hooks/useSlidingIndicator';
import RollingValue from '@/components/common/RollingValue';
import EmptyState from '@/components/common/EmptyState';
import VisitShopLocation from './components/VisitShopLocation';
import styles from './MyReservationsPage.module.css';

// 탭별 조회 조건. 시술이 끝나면 바로 지난 이용으로 넘어가도록 서버가 종료 시각으로 나눈다
const TABS = [
  {
    key: 'upcoming',
    label: '이용 예정',
    filters: { timing: 'UPCOMING', statuses: ['HOLD', 'CONFIRMED'] },
  },
  {
    key: 'past',
    label: '지난 이용',
    filters: { timing: 'PAST', statuses: ['CONFIRMED'] },
  },
  {
    key: 'cancelled',
    label: '취소',
    filters: { statuses: ['CANCEL_PENDING', 'CANCELLED'] },
  },
];
const PAGE_SIZE = 10;
// 카드가 세로로 쌓이는 폭 (MyReservationsPage.module.css 와 같게 둔다)
const CARD_LAYOUT_QUERY = '(max-width: 640px)';

const cancelPath = (reservationId) => generatePath(ROUTES.MY_RESERVATION_CANCEL, { reservationId });

// 알림 링크의 ?reservationId= . 숫자만 예약 번호로 본다
function reservationIdFromSearch(searchParams) {
  const value = searchParams.get('reservationId');
  if (!value || !/^\d+$/.test(value)) {
    return null;
  }
  return Number(value);
}

// 알림 링크의 ?tab= . upcoming 은 이용 예정, cancelled 는 취소다. 없거나 다른 값이면 이용 예정이다.
// 예약 번호가 있는데 숫자가 아니면 상세는 열 수 없다. 이용 예정 링크가 아니면 취소 목록을 보여 준다.
function tabKeyFromSearch(searchParams) {
  if (searchParams.get('tab') === 'upcoming') {
    return 'upcoming';
  }
  if (searchParams.get('tab') === 'cancelled') {
    return 'cancelled';
  }
  const reservationValue = searchParams.get('reservationId');
  if (reservationValue != null && reservationIdFromSearch(searchParams) == null) {
    return 'cancelled';
  }
  return 'upcoming';
}

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
  const [searchParams, setSearchParams] = useSearchParams();
  const tabFromSearch = tabKeyFromSearch(searchParams);
  const linkedReservationId = reservationIdFromSearch(searchParams);
  const [tabKey, setTabKey] = useState(tabFromSearch);
  const [page, setPage] = useState(0);
  // 이 화면에서 목록을 한 번 받은 뒤에는, 다음 목록을 받는 동안에도 목록을 남겨 화면이 튀지 않게 한다
  const [hasLoadedList, setHasLoadedList] = useState(false);
  const [isDetailOpen, setIsDetailOpen] = useState(false);
  const listRef = useRef(null);
  const tabIndicator = useSlidingIndicator(tabKey);

  useEffect(() => {
    const load = async () => {
      const { filters } = TABS.find((item) => item.key === tabKey);
      await loadReservations('my', { ...filters, page, size: PAGE_SIZE });
      setHasLoadedList(true);
    };
    load();
  }, [loadReservations, tabKey, page]);

  // 주소의 탭이 바뀔 때만 맞춘다. 화면에서 누른 탭은 주소가 그대로면 유지한다.
  useEffect(() => {
    setTabKey(tabFromSearch);
    setPage(0);
  }, [tabFromSearch]);

  // 예약 번호가 숫자가 아니면 상세를 묻지 않고, 주소는 취소 탭만 남긴다.
  useEffect(() => {
    const reservationValue = searchParams.get('reservationId');
    if (reservationValue == null || reservationIdFromSearch(searchParams) != null) {
      return;
    }
    const next = new URLSearchParams(searchParams);
    next.set('tab', searchParams.get('tab') === 'upcoming' ? 'upcoming' : 'cancelled');
    next.delete('reservationId');
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);

  // 알림에 적힌 예약 번호로 상세를 연다. 목록 첫 페이지에 그 카드가 없어도 번호만으로 받는다.
  useEffect(() => {
    if (linkedReservationId == null) {
      return undefined;
    }

    let ignore = false;

    const openLinkedReservation = async () => {
      await loadReservation('my', linkedReservationId);
      if (ignore) {
        return;
      }

      const { detailStatus, detail } = useReservationStore.getState();
      const loadedId = Number(detail?.summary?.reservationId);
      if (detailStatus === 'succeeded' && loadedId === linkedReservationId) {
        const shopId = detail.summary.shopId;
        if (shopId) {
          loadShop(shopId);
        }
        setIsDetailOpen(true);
        return;
      }
      // 목록에서 다른 예약을 누르며 상세를 다시 받는 중이면, 방금 온 결과로 창을 닫지 않는다.
      if (detailStatus === 'loading' || detailStatus === 'succeeded') {
        return;
      }
      setIsDetailOpen(false);
      if (tabFromSearch === 'upcoming') {
        return;
      }
      setTabKey('cancelled');
      setPage(0);
    };

    openLinkedReservation();

    return () => {
      ignore = true;
    };
  }, [linkedReservationId, loadReservation, loadShop, tabFromSearch]);

  // 창을 연 채 취소 화면 등으로 떠나도 공용 스토어에 매장 정보를 남기지 않는다
  useEffect(() => clearShop, [clearShop]);

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
    if (!searchParams.has('reservationId')) {
      return;
    }
    const next = new URLSearchParams(searchParams);
    next.delete('reservationId');
    setSearchParams(next, { replace: true });
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
    <div className={`b-container b-page ${styles.page}`}>
      <div className="b-row b-title-row">
        <h1>
          내 예약{' '}
          <Badge>
            <RollingValue value={`${pageInfo.totalElements}건`} />
          </Badge>
        </h1>
      </div>
      <div className={`b-options b-tabs ${styles.tabs}`} role="group" aria-label="예약 구분">
        <SlidingIndicator indicator={tabIndicator} className={styles.tabIndicator} />
        {TABS.map((item) => (
          <button
            key={item.key}
            ref={tabKey === item.key ? tabIndicator.selectedRef : null}
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
              const status = reservationStatusOf(item.displayStatus);
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
          <ReservationDetailPanel viewer="customer" cancelPath={cancelPath} hideHeading />
        </Dialog>
      )}
    </div>
  );
}
