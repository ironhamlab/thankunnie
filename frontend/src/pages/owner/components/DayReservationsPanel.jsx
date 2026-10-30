import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { reservationStatusOf } from '@/constants/reservationStatus';
import { useReservationStore } from '@/store/useReservationStore';
import { timePart } from '@/utils/scheduleDate';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';

// 시간을 차지하는 예약만 보인다. 취소 완료·점유 만료는 시간을 비우므로 뺀다
const OCCUPYING_STATUSES = ['HOLD', 'CONFIRMED', 'CANCEL_PENDING'];
// 30분 칸 기준 하루 최대 48건이라 한 페이지로 충분하다
const DAY_PAGE_SIZE = 50;

/**
 * 선택한 날짜의 예약 (B-F3). 닫기 전에 그 시간에 예약이 걸려 있는지 보게 한다.
 */
export default function DayReservationsPanel({ date }) {
  const loadReservations = useReservationStore((state) => state.loadReservations);
  const reservations = useReservationStore((state) => state.reservations);
  const listStatus = useReservationStore((state) => state.listStatus);

  useEffect(() => {
    loadReservations('owner', {
      from: date,
      to: date,
      statuses: OCCUPYING_STATUSES,
      size: DAY_PAGE_SIZE,
    });
  }, [loadReservations, date]);

  return (
    <Panel as="section" padded>
      <div className="b-row b-panel-heading">
        <h2>
          {date} 예약{' '}
          {listStatus === 'succeeded' && (
            <small className="b-muted">({reservations.length}건)</small>
          )}
        </h2>
        <Button as={Link} size="small" to={ROUTES.OWNER_BOOKINGS}>
          예약 현황
        </Button>
      </div>
      {listStatus === 'loading' && <p role="status">예약을 불러오는 중입니다.</p>}
      {listStatus === 'failed' && <p role="alert">예약을 불러오지 못했습니다.</p>}
      {listStatus === 'succeeded' && !reservations.length && (
        // 공통 빈 상태(EmptyState)는 높이가 커서, 옆 패널에서는 한 줄로 알린다. 예약이 있으면 한 건에 한 줄씩 늘어난다
        <p className="b-muted">이 날은 예약이 없습니다.</p>
      )}
      {listStatus === 'succeeded' && reservations.length > 0 && (
        <div className="b-stack">
          {reservations.map((item) => {
            const status = reservationStatusOf(item.status);
            return (
              <div key={item.reservationId} className="b-row">
                <span>
                  <strong>
                    {timePart(item.startsAt)} ~ {timePart(item.endsAt)}
                  </strong>{' '}
                  {item.customerName} · {item.treatmentName}
                </span>
                <Badge className={status.tone}>{status.label}</Badge>
              </div>
            );
          })}
        </div>
      )}
    </Panel>
  );
}
