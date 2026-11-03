import { useEffect, useRef, useState } from 'react';
import { generatePath, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { PAYMENT_STATUS, reservationStatusOf } from '@/constants/reservationStatus';
import { useReservationStore } from '@/store/useReservationStore';
import { formatReservationTime } from '@/utils/reservationFormat';
import { todayKst } from '@/utils/scheduleDate';
import ReservationDetailPanel from '@/components/reservation/ReservationDetailPanel';
import ReservationPagination from '@/components/reservation/ReservationPagination';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import EmptyState from '@/components/common/EmptyState';
import styles from './OwnerBookingsPage.module.css';
import motion from './components/OwnerMotion.module.css';

// 상태를 고르지 않으면 서버가 점유 만료(EXPIRED)를 뺀 전체를 준다
const STATUS_FILTERS = [
  { key: 'ALL', label: '전체', statuses: [] },
  { key: 'HOLD', label: '결제 대기', statuses: ['HOLD'] },
  { key: 'CONFIRMED', label: '예약 확정', statuses: ['CONFIRMED'] },
  { key: 'CANCEL', label: '취소', statuses: ['CANCEL_PENDING', 'CANCELLED'] },
];
const PAGE_SIZE = 10;
// 목록과 상세가 한 단으로 쌓이는 폭 (OwnerBookingsPage.module.css 의 .grid 와 같게 둔다)
const SINGLE_COLUMN_QUERY = '(max-width: 1200px)';

const cancelPath = (reservationId) =>
  generatePath(ROUTES.OWNER_RESERVATION_CANCEL, { reservationId });

// 알림 링크의 ?reservationId= . 숫자만 예약 번호로 본다
function reservationIdFromSearch(searchParams) {
  const value = searchParams.get('reservationId');
  if (!value || !/^\d+$/.test(value)) {
    return null;
  }
  return Number(value);
}

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
  // 상세 응답이 도착했을 때 효과를 시작하도록, 받은 예약 번호로 상세를 다시 그린다 (받기 전에는 로딩 표시)
  const loadedDetailId = useReservationStore((state) =>
    state.detailStatus === 'succeeded' ? state.detail?.summary?.reservationId : null,
  );
  const [from, setFrom] = useState(todayKst());
  const [to, setTo] = useState('');
  const [filterKey, setFilterKey] = useState('ALL');
  const [page, setPage] = useState(0);
  const [searchParams, setSearchParams] = useSearchParams();
  const linkedId = reservationIdFromSearch(searchParams);
  const detailRef = useRef(null);

  useEffect(() => {
    const { statuses } = STATUS_FILTERS.find((filter) => filter.key === filterKey);
    loadReservations('owner', { from, to, statuses, page, size: PAGE_SIZE });
  }, [loadReservations, from, to, filterKey, page]);

  // 알림이 지정한 예약 번호가 있으면 그 상세를 연다. 목록에 없어도 그 번호를 조회한다.
  // 번호가 없으면 지금 목록의 첫 예약을 보여준다.
  const activeId = linkedId ?? reservations[0]?.reservationId;

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

  // 알림이나 상세 보기로 예약을 고르면 포커스를 상세로 옮긴다.
  // 한 단 화면에서는 상세가 목록 아래에 있어 상세 위치로 스크롤도 한다.
  useEffect(() => {
    if (!linkedId || listStatus !== 'succeeded') {
      return;
    }
    detailRef.current?.focus({ preventScroll: true });
    if (window.matchMedia(SINGLE_COLUMN_QUERY).matches) {
      const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      detailRef.current?.scrollIntoView({
        behavior: reduceMotion ? 'auto' : 'smooth',
        block: 'start',
      });
    }
  }, [linkedId, listStatus]);

  const showDetail = (reservationId) => {
    const next = new URLSearchParams(searchParams);
    next.set('reservationId', String(reservationId));
    setSearchParams(next);
  };

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

      <Panel padded className="b-stack">
        <div className={styles.filters}>
          <label>
            시작일부터
            <input
              type="date"
              value={from}
              onChange={(event) => handleFromChange(event.target.value)}
            />
          </label>
          <label>
            시작일까지
            <input
              type="date"
              value={to}
              min={from || undefined}
              onChange={(event) => changeFilter(() => setTo(event.target.value))}
            />
          </label>
          <Button
            size="small"
            type="button"
            onClick={() =>
              changeFilter(() => {
                setFrom(todayKst());
                setTo('');
                setFilterKey('ALL');
              })
            }
          >
            필터 초기화
          </Button>
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
      </Panel>

      <div className={styles.grid}>
        <Panel as="section" className="b-table-panel">
          <div className="b-row b-pad">
            <h2>예약 목록</h2>
            <small>{pageInfo.totalElements}건</small>
          </div>
          {listStatus === 'loading' && <p role="status">예약을 불러오는 중입니다.</p>}
          {listStatus === 'failed' && <p role="alert">{listError.message}</p>}
          {listStatus === 'succeeded' && (
            <>
              {/* 필터·페이지를 바꿔 목록을 새로 받을 때마다 다시 그려져 내용이 바뀐 것을 알린다 */}
              <div className={`b-table-scroll ${motion.step}`}>
                <table className={styles.table}>
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
                          <td className={styles.id}>{item.reservationId}</td>
                          <td className={styles.name}>
                            <strong>{item.customerName}</strong>
                          </td>
                          <td className={styles.treatment}>{item.treatmentName}</td>
                          <td className={styles.time}>
                            {formatReservationTime(item.startsAt, item.endsAt)}
                          </td>
                          <td className={styles.payment}>
                            {PAYMENT_STATUS[item.paymentStatus] ?? '결제 전'}
                          </td>
                          <td className={styles.status}>
                            <Badge className={status.tone}>{status.label}</Badge>
                          </td>
                          <td className={styles.detail}>
                            <Button
                              size="small"
                              type="button"
                              onClick={() => showDetail(item.reservationId)}
                            >
                              상세 보기
                            </Button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              {!reservations.length && <EmptyState as="p">조건에 맞는 예약이 없습니다.</EmptyState>}
              <ReservationPagination page={page} pageInfo={pageInfo} onPageChange={setPage} />
            </>
          )}
        </Panel>
        <aside ref={detailRef} tabIndex={-1} aria-label="예약 상세" className="b-stack">
          {/* 다른 예약을 고르면 상세가 새로 나타나 바뀐 것을 알린다 */}
          {listStatus === 'succeeded' && (
            <div
              key={loadedDetailId ?? 'pending'}
              className={loadedDetailId ? motion.enter : undefined}
            >
              <ReservationDetailPanel viewer="owner" cancelPath={cancelPath} />
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
