import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useReservationStore } from '@/store/useReservationStore';
import { ROUTES } from '@/constants/routes';
import {
  CANCEL_STATUS,
  CANCEL_TYPE,
  PAYMENT_STATUS,
  ownerReservationStatusOf,
  reservationStatusOf,
} from '@/constants/reservationStatus';
import { formatDateTime, formatMoney, formatReservationTime } from '@/utils/reservationFormat';
import Badge from '@/components/common/Badge';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import Panel from '@/components/common/Panel';
import Notice from '@/components/common/Notice';

/**
 * 예약 상세 (매장 예약 현황·고객 내 예약 공용). 매장은 예약자 연락처를, 고객은 매장을 본다.
 * 취소 버튼은 서버가 취소 가능(확정 + 시작 전)으로 알려준 예약에만 보이고, 취소 화면(E)으로 이동한다.
 * 매장은 끝난 확정 예약을 노쇼로 표시·해제할 수 있다. 노쇼 예약은 상태 배지를 노쇼로 보여준다.
 *
 * @param hideHeading      창 제목이 이미 "예약 상세" 일 때 같은 제목을 또 보여주지 않는다
 * @param keepWhileLoading 다음 상세를 받는 동안 지금 상세를 유지해, 패널이 줄어 화면이 튀지 않게 한다
 */
export default function ReservationDetailPanel({
  viewer,
  cancelPath,
  hideHeading = false,
  keepWhileLoading = false,
}) {
  const detail = useReservationStore((state) => state.detail);
  const detailStatus = useReservationStore((state) => state.detailStatus);
  const detailError = useReservationStore((state) => state.detailError);

  if (detailStatus === 'loading' && !(keepWhileLoading && detail)) {
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
  // 고객은 시술이 끝났는지까지 반영한 표시 상태를, 매장은 노쇼 표시를 반영한 상태를 본다
  const status =
    viewer === 'owner'
      ? ownerReservationStatusOf(summary)
      : reservationStatusOf(summary.displayStatus);
  // 다음 상세를 받는 동안 보이는 것은 이전 예약이므로, 그 예약을 취소하러 가지 않게 막는다
  const isStale = detailStatus === 'loading';

  return (
    <Panel as="section" padded className="b-stack">
      <div className="b-row b-panel-heading">
        <div>
          <small className="b-muted">예약번호 {summary.reservationId}</small>
          {!hideHeading && <h2>예약 상세</h2>}
        </div>
        <Badge className={status.tone}>{status.label}</Badge>
      </div>

      {viewer === 'owner' ? (
        <Notice>
          <h3>예약자</h3>
          <div className="b-row">
            <strong>{summary.customerName}</strong>
            {/* 탈퇴 회원은 서버가 번호를 보내지 않는다 */}
            <span>{detail.customerPhone ?? '-'}</span>
          </div>
          <small>요청사항: {detail.customerRequest || '없음'}</small>
          {summary.customerId && (
            <Link
              className="b-pink"
              to={`${ROUTES.OWNER_CUSTOMERS}?customer=${summary.customerId}`}
            >
              <small>방문 이력·메모 보기 →</small>
            </Link>
          )}
        </Notice>
      ) : (
        <Notice>
          <h3>매장</h3>
          <div>
            <strong>{summary.shopName}</strong>
          </div>
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

      {viewer === 'owner' && (
        <NoShowAction
          key={summary.reservationId}
          summary={summary}
          serverTime={detail.serverTime}
          disabled={isStale}
        />
      )}

      {summary.cancellable && (
        <Button
          as={Link}
          variant="danger"
          wide
          to={cancelPath(summary.reservationId)}
          aria-disabled={isStale}
          onClick={isStale ? (event) => event.preventDefault() : undefined}
        >
          취소하기
        </Button>
      )}
    </Panel>
  );
}

/**
 * 매장의 노쇼 표시·해제. 끝난 확정 예약에만 보인다 (상세 응답의 서버 시각 기준, 최종 판단은 서버가 한다).
 * 표시는 확인 창을 거치고, 해제는 바로 한다. 잘못 표시해도 기한 없이 해제할 수 있다.
 */
function NoShowAction({ summary, serverTime, disabled }) {
  const changeNoShow = useReservationStore((state) => state.changeNoShow);
  const saving = useReservationStore((state) => state.noShowStatus === 'saving');
  const noShowError = useReservationStore((state) => state.noShowError);
  const [confirming, setConfirming] = useState(false);

  // 상세를 받은 시각(서버 기준). 화면을 켜 둔 채 종료 시각이 지나면 상세를 다시 열어야 버튼이 보인다
  const ended = Date.parse(summary.endsAt) <= Date.parse(serverTime);
  if (summary.status !== 'CONFIRMED' || (!summary.noShow && !ended)) {
    return null;
  }
  const error = noShowError?.reservationId === summary.reservationId ? noShowError : null;
  const change = (noShow) => changeNoShow(summary.reservationId, noShow);

  return (
    <Notice>
      <h3>방문 확인</h3>
      <div className="b-row">
        <small>
          {summary.noShow
            ? '고객이 오지 않은 예약으로 표시했어요. 이용 횟수·매출 집계에서 빠져요.'
            : '고객이 오지 않았다면 노쇼로 표시해 주세요.'}
        </small>
        {summary.noShow ? (
          <Button
            size="small"
            type="button"
            disabled={disabled || saving}
            onClick={() => change(false)}
          >
            {saving ? '해제 중' : '노쇼 해제'}
          </Button>
        ) : (
          <Button
            size="small"
            type="button"
            disabled={disabled || saving}
            onClick={() => setConfirming(true)}
          >
            {saving ? '표시 중' : '노쇼로 표시'}
          </Button>
        )}
      </div>
      {error && (
        <Alert tone="error" role="alert">
          {error.message}
        </Alert>
      )}
      {confirming && (
        <Dialog title="노쇼로 표시할까요?" onClose={() => setConfirming(false)}>
          <p>
            예약금은 그대로 두고, 이 예약은 고객 이용 횟수와 매출 집계에서 빠져요. 잘못 표시했으면
            언제든 해제할 수 있어요.
          </p>
          <div className="b-flex">
            <Button type="button" onClick={() => setConfirming(false)}>
              취소
            </Button>
            <Button
              type="button"
              variant="danger"
              onClick={() => {
                setConfirming(false);
                change(true);
              }}
            >
              노쇼로 표시
            </Button>
          </div>
        </Dialog>
      )}
    </Notice>
  );
}
