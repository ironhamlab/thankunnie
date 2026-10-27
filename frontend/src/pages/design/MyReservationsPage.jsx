import { useEffect, useState } from 'react';
import { generatePath, Link } from 'react-router-dom';
import { DESIGN_ROUTES as R } from '@/constants/designRoutes';
import { PAYMENT_STATUS, reservationStatusOf } from '@/constants/reservationStatus';
import { useReservationStore } from '@/store/useReservationStore';
import { formatMoney, formatReservationTime } from '@/utils/reservationFormat';
import { addDays, todayKst } from '@/utils/scheduleDate';
import ReservationDetailPanel from './ReservationDetailPanel';

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
  },
  {
    key: 'cancelled',
    label: '취소',
    filters: () => ({ statuses: ['CANCEL_PENDING', 'CANCELLED'] }),
  },
];
const PAGE_SIZE = 10;

const cancelPath = (reservationId) => generatePath(R.MY_RESERVATION_CANCEL, { reservationId });

/**
 * 고객 내 예약 (B-F5). 여러 매장 예약을 이용 예정·지난 이용·취소 탭으로 나눠 보고, 고른 예약의 결제·취소 상태를 본다.
 */
export default function MyReservationsPage() {
  const loadReservations = useReservationStore((state) => state.loadReservations);
  const loadReservation = useReservationStore((state) => state.loadReservation);
  const clearDetail = useReservationStore((state) => state.clearDetail);
  const reservations = useReservationStore((state) => state.reservations);
  const pageInfo = useReservationStore((state) => state.pageInfo);
  const listStatus = useReservationStore((state) => state.listStatus);
  const listError = useReservationStore((state) => state.listError);
  const [tabKey, setTabKey] = useState('upcoming');
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState(null);

  useEffect(() => {
    const tab = TABS.find((item) => item.key === tabKey);
    loadReservations('my', { ...tab.filters(), page, size: PAGE_SIZE });
  }, [loadReservations, tabKey, page]);

  // 고른 예약이 현재 목록에 없으면 첫 예약을 보여준다
  const activeId = reservations.some((item) => item.reservationId === selectedId)
    ? selectedId
    : reservations[0]?.reservationId;

  useEffect(() => {
    if (activeId) {
      loadReservation('my', activeId);
    } else {
      clearDetail();
    }
  }, [loadReservation, clearDetail, activeId]);

  const handleTabChange = (key) => {
    setTabKey(key);
    setPage(0);
  };

  return (
    <div className="b-container b-page">
      <div className="b-row b-title-row">
        <h1>
          내 예약 <span className="b-badge">{pageInfo.totalElements}건</span>
        </h1>
      </div>
      <div className="b-options b-tabs" role="tablist" aria-label="예약 구분">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={tabKey === tab.key}
            className={tabKey === tab.key ? 'is-selected' : ''}
            onClick={() => handleTabChange(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {listStatus === 'loading' && <p role="status">예약을 불러오는 중입니다.</p>}
      {listStatus === 'failed' && <p role="alert">{listError.message}</p>}
      {listStatus === 'succeeded' && !reservations.length && (
        <div className="b-panel b-empty">
          <h2>예약 내역이 없어요</h2>
          <p>실시간 빈자리를 찾아보세요.</p>
          <Link to={R.HOME} className="b-button b-primary">
            빈자리 탐색하기
          </Link>
        </div>
      )}
      {listStatus === 'succeeded' && reservations.length > 0 && (
        <div className="b-bookings-grid">
          <div className="b-stack">
            {reservations.map((item) => {
              const status = reservationStatusOf(item.status);
              return (
                <button
                  key={item.reservationId}
                  type="button"
                  className={`b-panel b-booking-card ${activeId === item.reservationId ? 'is-active' : ''}`}
                  onClick={() => setSelectedId(item.reservationId)}
                >
                  <div className="b-flex">
                    <span className={`b-badge ${status.tone}`}>{status.label}</span>
                    {item.paymentStatus && (
                      <small className="b-muted">{PAYMENT_STATUS[item.paymentStatus]}</small>
                    )}
                  </div>
                  <small>{item.shopName}</small>
                  <h3>{item.treatmentName}</h3>
                  <p>{formatReservationTime(item.startsAt, item.endsAt)}</p>
                  <div className="b-notice b-row">
                    <span>예약금 {formatMoney(item.deposit)}</span>
                    <span>현장 잔금 {formatMoney(item.balance)}</span>
                  </div>
                  <span className="b-pink">예약 상세 보기 →</span>
                </button>
              );
            })}
            {pageInfo.totalPages > 1 && (
              <div className="b-flex">
                <button
                  type="button"
                  className="b-button b-small"
                  disabled={page === 0}
                  onClick={() => setPage((current) => current - 1)}
                >
                  이전
                </button>
                <span>
                  {page + 1} / {pageInfo.totalPages}
                </span>
                <button
                  type="button"
                  className="b-button b-small"
                  disabled={!pageInfo.hasNext}
                  onClick={() => setPage((current) => current + 1)}
                >
                  다음
                </button>
              </div>
            )}
          </div>
          <ReservationDetailPanel viewer="customer" cancelPath={cancelPath} />
        </div>
      )}
    </div>
  );
}
