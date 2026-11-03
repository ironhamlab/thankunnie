import { useEffect, useRef, useState } from 'react';
import { generatePath } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { PAYMENT_STATUS, reservationStatusOf } from '@/constants/reservationStatus';
import { useReservationStore } from '@/store/useReservationStore';
import { formatReservationTime } from '@/utils/reservationFormat';
import { todayKst } from '@/utils/scheduleDate';
import ReservationDetailPanel from '@/components/reservation/ReservationDetailPanel';
import ReservationPagination from '@/components/reservation/ReservationPagination';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
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
const SINGLE_COLUMN_QUERY = '(max-width: 1480px)';
// 표가 카드로 바뀌는 폭. 폰에서는 상세가 목록 아래로 멀어지므로 창으로 연다 (module.css 와 같게 둔다)
const CARD_LAYOUT_QUERY = '(max-width: 640px)';

const cancelPath = (reservationId) =>
  generatePath(ROUTES.OWNER_RESERVATION_CANCEL, { reservationId });

/**
 * 매장 예약 현황 (B-F6). 본인 매장 예약을 날짜·상태로 거르고, 고른 예약의 예약자·결제·취소 상태를 본다.
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
  const [selectedId, setSelectedId] = useState(null);
  // 이 화면에서 목록을 한 번 받은 뒤에는, 다음 목록을 받는 동안에도 표를 남겨 화면이 튀지 않게 한다
  const [hasLoadedList, setHasLoadedList] = useState(false);
  const [isDetailDialogOpen, setIsDetailDialogOpen] = useState(false);
  const [isCardLayout, setIsCardLayout] = useState(
    () => window.matchMedia(CARD_LAYOUT_QUERY).matches,
  );
  const listRef = useRef(null);
  const detailRef = useRef(null);

  useEffect(() => {
    const media = window.matchMedia(CARD_LAYOUT_QUERY);
    const handleChange = (event) => {
      setIsCardLayout(event.matches);
      // 넓은 화면에는 창이 없으므로, 다시 좁혔을 때 저절로 열리지 않게 닫아 둔다
      if (!event.matches) {
        setIsDetailDialogOpen(false);
      }
    };
    media.addEventListener('change', handleChange);
    return () => media.removeEventListener('change', handleChange);
  }, []);

  useEffect(() => {
    const load = async () => {
      const { statuses } = STATUS_FILTERS.find((filter) => filter.key === filterKey);
      await loadReservations('owner', { from, to, statuses, page, size: PAGE_SIZE });
      setHasLoadedList(true);
    };
    load();
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

  // 예약을 고르면 포커스를 상세로 옮겨, 키보드 사용자가 남은 목록을 거치지 않고 바로 상세를 읽게 한다.
  // 한 단 화면에서는 상세가 목록 아래에 있어 상세 위치로 스크롤도 한다
  const showDetail = (reservationId) => {
    setSelectedId(reservationId);
    if (isCardLayout) {
      setIsDetailDialogOpen(true);
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
  };

  // 폰은 목록이 화면보다 길어 페이지를 넘겨도 스크롤이 아래에 남으므로 목록 맨 위로 올린다
  const changePage = (next) => {
    // 받는 동안에는 이전 페이지 정보가 남아 있어, 더 넘기면 없는 페이지를 요청할 수 있다
    if (listStatus === 'loading') {
      return;
    }
    setPage(next);
    if (isCardLayout) {
      listRef.current?.scrollIntoView({ block: 'start' });
    }
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
        <Panel as="section" className="b-table-panel" ref={listRef}>
          <div className="b-row b-pad">
            <h2>예약 목록</h2>
            <small>{pageInfo.totalElements}건</small>
          </div>
          {listStatus === 'loading' && !hasLoadedList && (
            <p role="status">예약을 불러오는 중입니다.</p>
          )}
          {listStatus === 'failed' && <p role="alert">{listError.message}</p>}
          {listStatus !== 'failed' && hasLoadedList && (
            <>
              {/* 필터·페이지를 바꿔 목록을 새로 받을 때마다 다시 그려져 내용이 바뀐 것을 알린다 */}
              <div
                key={reservations[0]?.reservationId ?? 'empty'}
                className={`b-table-scroll ${styles.list} ${motion.step}`}
              >
                <table className={styles.table}>
                  <thead>
                    <tr>
                      {['예약번호', '예약자', '시술', '예약 일시', '결제', '상태'].map((header) => (
                        <th key={header}>{header}</th>
                      ))}
                      <th aria-label="상세" />
                    </tr>
                  </thead>
                  <tbody>
                    {reservations.map((item) => {
                      const status = reservationStatusOf(item.status);
                      return (
                        // 행 어디를 눌러도 상세를 연다. 키보드와 화면 읽기 도구는 끝의 버튼을 쓰고, 그 클릭도 여기서 받는다
                        <tr
                          key={item.reservationId}
                          className={activeId === item.reservationId ? 'is-active' : ''}
                          aria-current={activeId === item.reservationId ? 'true' : undefined}
                          onClick={() => showDetail(item.reservationId)}
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
                          <td className={styles.chevron}>
                            <button
                              type="button"
                              aria-label={`${item.customerName} 예약 상세 보기`}
                            >
                              ›
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              {listStatus === 'succeeded' && !reservations.length && (
                <EmptyState as="p">조건에 맞는 예약이 없습니다.</EmptyState>
              )}
              <div className={styles.pager}>
                <ReservationPagination page={page} pageInfo={pageInfo} onPageChange={changePage} />
              </div>
            </>
          )}
        </Panel>
        {!isCardLayout && (
          <aside ref={detailRef} tabIndex={-1} aria-label="예약 상세" className="b-stack">
            {/* 다른 예약을 고르면 상세가 새로 나타나 바뀐 것을 알린다 */}
            {listStatus !== 'failed' && hasLoadedList && (
              <div
                key={loadedDetailId ?? 'pending'}
                className={loadedDetailId ? motion.enter : undefined}
              >
                <ReservationDetailPanel viewer="owner" cancelPath={cancelPath} keepWhileLoading />
              </div>
            )}
          </aside>
        )}
      </div>
      {isCardLayout && isDetailDialogOpen && (
        <Dialog
          title="예약 상세"
          className={styles.detailDialog}
          onClose={() => setIsDetailDialogOpen(false)}
        >
          <ReservationDetailPanel viewer="owner" cancelPath={cancelPath} hideHeading />
        </Dialog>
      )}
    </div>
  );
}
