import { useEffect, useState } from 'react';
import { generatePath } from 'react-router-dom';
import { DESIGN_ROUTES as R } from '@/constants/designRoutes';
import { PAYMENT_STATUS, reservationStatusOf } from '@/constants/reservationStatus';
import { useReservationStore } from '@/store/useReservationStore';
import { formatReservationTime } from '@/utils/reservationFormat';
import { todayKst } from '@/utils/scheduleDate';
import ReservationDetailPanel from './ReservationDetailPanel';
import ReservationPagination from './ReservationPagination';

// 상태를 고르지 않으면 서버가 점유 만료(EXPIRED)를 뺀 전체를 준다
const STATUS_FILTERS = [
  { key: 'ALL', label: '전체', statuses: [] },
  { key: 'HOLD', label: '결제 대기', statuses: ['HOLD'] },
  { key: 'CONFIRMED', label: '예약 확정', statuses: ['CONFIRMED'] },
  { key: 'CANCEL', label: '취소', statuses: ['CANCEL_PENDING', 'CANCELLED'] },
];
const PAGE_SIZE = 10;

const cancelPath = (reservationId) => generatePath(R.OWNER_RESERVATION_CANCEL, { reservationId });

/**
 * 사장님 예약 현황 (B-F6). 본인 매장 예약을 날짜·상태로 거르고, 고른 예약의 예약자·결제·취소 상태를 본다.
 */
export default function OwnerBookingsPage() {
  const loadReservations = useReservationStore((state) => state.loadReservations);
  const loadReservation = useReservationStore((state) => state.loadReservation);
  const clearDetail = useReservationStore((state) => state.clearDetail);
  const reservations = useReservationStore((state) => state.reservations);
  const pageInfo = useReservationStore((state) => state.pageInfo);
  const listStatus = useReservationStore((state) => state.listStatus);
  const listError = useReservationStore((state) => state.listError);
  const [from, setFrom] = useState(todayKst());
  const [to, setTo] = useState('');
  const [filterKey, setFilterKey] = useState('ALL');
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState(null);

  useEffect(() => {
    const { statuses } = STATUS_FILTERS.find((filter) => filter.key === filterKey);
    loadReservations('owner', { from, to, statuses, page, size: PAGE_SIZE });
  }, [loadReservations, from, to, filterKey, page]);

  // 고른 예약이 현재 목록에 없으면 첫 예약을 보여준다
  const activeId = reservations.some((item) => item.reservationId === selectedId)
    ? selectedId
    : reservations[0]?.reservationId;

  // 새 목록이 오기 전에는 상세를 부르지 않는다. 이전 목록의 상세가 새 조건의 결과처럼 보이지 않게 한다
  useEffect(() => {
    if (listStatus !== 'succeeded') {
      return;
    }
    if (activeId) {
      loadReservation('owner', activeId);
    } else {
      clearDetail();
    }
  }, [loadReservation, clearDetail, activeId, listStatus]);

  // 필터를 바꾸면 첫 페이지부터 다시 본다
  const changeFilter = (update) => {
    update();
    setPage(0);
  };

  // 시작일을 종료일보다 늦게 바꾸면 종료일을 비운다 (YYYY-MM-DD 는 문자열 비교가 날짜 순서와 같다)
  const handleFromChange = (value) =>
    changeFilter(() => {
      setFrom(value);
      if (to && value && to < value) {
        setTo('');
      }
    });

  return (
    <div className="b-stack">
      <div className="b-row b-title-row">
        <div>
          <h1>예약 현황</h1>
          <p className="b-muted">내 매장 예약의 예약자·결제·취소 상태를 확인합니다.</p>
        </div>
      </div>

      <div className="b-panel b-pad b-stack">
        <div className="b-flex">
          <label>
            시작일부터{' '}
            <input
              type="date"
              value={from}
              onChange={(event) => handleFromChange(event.target.value)}
            />
          </label>
          <label>
            시작일까지{' '}
            <input
              type="date"
              value={to}
              min={from || undefined}
              onChange={(event) => changeFilter(() => setTo(event.target.value))}
            />
          </label>
          <button
            type="button"
            className="b-button b-small"
            onClick={() =>
              changeFilter(() => {
                setFrom(todayKst());
                setTo('');
                setFilterKey('ALL');
              })
            }
          >
            필터 초기화
          </button>
        </div>
        <div className="b-options" role="group" aria-label="예약 상태">
          {STATUS_FILTERS.map((filter) => (
            <button
              key={filter.key}
              type="button"
              className={filterKey === filter.key ? 'is-selected' : ''}
              onClick={() => changeFilter(() => setFilterKey(filter.key))}
            >
              {filter.label}
            </button>
          ))}
        </div>
      </div>

      <div className="b-owner-bookings-grid">
        <section className="b-panel b-table-panel">
          <div className="b-row b-pad">
            <h2>예약 목록</h2>
            <small>{pageInfo.totalElements}건</small>
          </div>
          {listStatus === 'loading' && <p role="status">예약을 불러오는 중입니다.</p>}
          {listStatus === 'failed' && <p role="alert">{listError.message}</p>}
          {listStatus === 'succeeded' && (
            <>
              <div className="b-table-scroll">
                <table>
                  <thead>
                    <tr>
                      {['예약번호', '예약자', '시술', '예약 일시', '결제', '상태', '상세'].map(
                        (header) => (
                          <th key={header}>{header}</th>
                        ),
                      )}
                    </tr>
                  </thead>
                  <tbody>
                    {reservations.map((item) => {
                      const status = reservationStatusOf(item.status);
                      return (
                        <tr
                          key={item.reservationId}
                          className={activeId === item.reservationId ? 'is-active' : ''}
                        >
                          <td>{item.reservationId}</td>
                          <td>
                            <strong>{item.customerName}</strong>
                          </td>
                          <td>{item.treatmentName}</td>
                          <td>{formatReservationTime(item.startsAt, item.endsAt)}</td>
                          <td>{PAYMENT_STATUS[item.paymentStatus] ?? '결제 전'}</td>
                          <td>
                            <span className={`b-badge ${status.tone}`}>{status.label}</span>
                          </td>
                          <td>
                            <button
                              type="button"
                              className="b-button b-small"
                              onClick={() => setSelectedId(item.reservationId)}
                            >
                              상세 보기
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              {!reservations.length && <p className="b-empty">조건에 맞는 예약이 없습니다.</p>}
              <ReservationPagination page={page} pageInfo={pageInfo} onPageChange={setPage} />
            </>
          )}
        </section>
        <aside className="b-stack">
          {listStatus === 'succeeded' && (
            <ReservationDetailPanel viewer="owner" cancelPath={cancelPath} />
          )}
        </aside>
      </div>
    </div>
  );
}
