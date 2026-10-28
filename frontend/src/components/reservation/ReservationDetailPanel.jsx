import { Link } from 'react-router-dom';
import { useReservationStore } from '@/store/useReservationStore';
import {
  CANCEL_STATUS,
  CANCEL_TYPE,
  PAYMENT_STATUS,
  reservationStatusOf,
} from '@/constants/reservationStatus';
import { formatDateTime, formatMoney, formatReservationTime } from '@/utils/reservationFormat';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import Notice from '@/components/common/Notice';

/**
 * 예약 상세 (사장님 예약 현황·고객 내 예약 공용). 사장님은 예약자 연락처를, 고객은 매장을 본다.
 * 취소 버튼은 서버가 취소 가능(확정 + 시작 전)으로 알려준 예약에만 보이고, 취소 화면(E)으로 이동한다.
 */
export default function ReservationDetailPanel({ viewer, cancelPath }) {
  const detail = useReservationStore((state) => state.detail);
  const detailStatus = useReservationStore((state) => state.detailStatus);
  const detailError = useReservationStore((state) => state.detailError);

  if (detailStatus === 'loading') {
    return (
      <Panel as="section" padded role="status">
        예약 상세를 불러오는 중입니다.
      </Panel>
    );
  }
  if (detailStatus === 'failed') {
    return (
      <Panel as="section" padded role="alert">
        {detailError.message}
      </Panel>
    );
  }
  if (!detail) {
    return null;
  }

  const { summary, payment, cancel } = detail;
  const status = reservationStatusOf(summary.status);

  return (
    <Panel as="section" padded className="b-stack">
      <div className="b-row b-panel-heading">
        <div>
          <small className="b-muted">예약번호 {summary.reservationId}</small>
          <h2>예약 상세</h2>
        </div>
        <Badge className={status.tone}>{status.label}</Badge>
      </div>

      {viewer === 'owner' ? (
        <Notice>
          <h3>예약자</h3>
          <div className="b-row">
            <strong>{summary.customerName}</strong>
            <span>{detail.customerPhone}</span>
          </div>
          <small>요청사항: {detail.customerRequest || '없음'}</small>
        </Notice>
      ) : (
        <Notice>
          <h3>매장</h3>
          <strong>{summary.shopName}</strong>
          <small>요청사항: {detail.customerRequest || '없음'}</small>
        </Notice>
      )}

      <Notice>
        <h3>시술</h3>
        <strong>{summary.treatmentName}</strong>
        <p className="b-pink">{formatReservationTime(summary.startsAt, summary.endsAt)}</p>
        <small>예약일 {formatDateTime(detail.reservedAt)}</small>
      </Notice>

      <dl className="b-details">
        <div>
          <dt>시술 금액</dt>
          <dd className="b-pink">{formatMoney(summary.appliedPrice)}</dd>
        </div>
        <div>
          <dt>예약금</dt>
          <dd>{formatMoney(summary.deposit)}</dd>
        </div>
        <div>
          <dt>현장 결제 잔금</dt>
          <dd>{formatMoney(summary.balance)}</dd>
        </div>
      </dl>

      <h3>결제</h3>
      {payment ? (
        <dl className="b-details">
          <div>
            <dt>상태</dt>
            <dd>{PAYMENT_STATUS[payment.status] ?? payment.status}</dd>
          </div>
          <div>
            <dt>결제 금액</dt>
            <dd>
              {formatMoney(payment.amount)} · {formatDateTime(payment.approvedAt)}
            </dd>
          </div>
          {payment.refundedAmount > 0 && (
            <div>
              <dt>환불 금액</dt>
              <dd>
                {formatMoney(payment.refundedAmount)} · {formatDateTime(payment.refundedAt)}
              </dd>
            </div>
          )}
        </dl>
      ) : (
        <p className="b-muted">결제 내역이 없습니다.</p>
      )}

      {cancel && (
        <>
          <h3>취소</h3>
          <dl className="b-details">
            <div>
              <dt>구분 · 상태</dt>
              <dd>
                {CANCEL_TYPE[cancel.cancelType] ?? cancel.cancelType} ·{' '}
                {CANCEL_STATUS[cancel.status] ?? cancel.status}
              </dd>
            </div>
            <div>
              <dt>환불 예정 금액</dt>
              <dd>{formatMoney(cancel.refundAmount)}</dd>
            </div>
            <div>
              <dt>취소 요청 · 완료</dt>
              <dd>
                {formatDateTime(cancel.requestedAt)} · {formatDateTime(cancel.completedAt)}
              </dd>
            </div>
          </dl>
        </>
      )}

      {summary.cancellable && (
        <Button as={Link} variant="danger" wide to={cancelPath(summary.reservationId)}>
          취소하기
        </Button>
      )}
    </Panel>
  );
}
