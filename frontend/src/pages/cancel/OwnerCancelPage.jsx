import { useEffect, useState } from 'react';
import { generatePath, Link, useNavigate, useParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { CANCEL_TYPE } from '@/constants/reservationStatus';
import { useReservationStore } from '@/store/useReservationStore';
import { useCancelStore } from '@/store/useCancelStore';
import { formatMoney } from '@/utils/reservationFormat';
import CancelTargetSummary from '@/components/cancel/CancelTargetSummary';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import Notice from '@/components/common/Notice';
import Panel from '@/components/common/Panel';
import styles from './OwnerCancelPage.module.css';

const CANCEL_TYPE_OPTIONS = [
  {
    value: 'SHOP_FAULT',
    description: '매장 사정으로 취소해요. 납부 예약금을 전액 환불합니다.',
  },
  {
    value: 'CUSTOMER_REQUEST',
    description: '고객이 전화 등으로 취소를 요청했어요. 환불액을 직접 정합니다.',
  },
];

const AFTER_CANCEL_OPTIONS = [
  {
    value: 'open',
    label: '시간 다시 열기',
    description: '다른 고객이 이 시간을 예약할 수 있어요.',
  },
  {
    value: 'close',
    label: '시간 닫기',
    description: '이 시간에는 예약을 받지 않아요.',
  },
];

const REASON_MAX_LENGTH = 500;
const CANCEL_STARTED_STATUSES = ['CANCEL_PENDING', 'CANCELLED'];

const resultPath = (reservationId) =>
  generatePath(ROUTES.OWNER_RESERVATION_CANCEL_RESULT, { reservationId });

/** 대리 취소 환불액 입력 검사. 최종 검증은 서버가 한다 (CANCEL_006) */
function validateRefund(value, quote) {
  if (value === '' || !/^\d+$/.test(value)) {
    return '환불액을 숫자로 입력해 주세요.';
  }
  const amount = Number(value);
  if (amount < quote.policyRefundAmount || amount > quote.paidAmount) {
    return `${formatMoney(quote.policyRefundAmount)} ~ ${formatMoney(quote.paidAmount)} 사이로 입력해 주세요.`;
  }
  return null;
}

/**
 * 사장님 취소 (E-F2). 매장 사유·고객 요청 대리 취소 중 고르고, 대리 취소면 환불액을 입력한다.
 * 취소한 시간은 다시 열지 닫을지 매번 고른다 (REO-02).
 */
export default function OwnerCancelPage() {
  const { reservationId } = useParams();
  const navigate = useNavigate();
  const loadReservation = useReservationStore((state) => state.loadReservation);
  const detail = useReservationStore((state) => state.detail);
  const detailStatus = useReservationStore((state) => state.detailStatus);
  const detailError = useReservationStore((state) => state.detailError);
  const loadQuote = useCancelStore((state) => state.loadQuote);
  const clearQuote = useCancelStore((state) => state.clearQuote);
  const cancelByOwner = useCancelStore((state) => state.cancelByOwner);
  const quote = useCancelStore((state) => state.quote);
  const quoteStatus = useCancelStore((state) => state.quoteStatus);
  const quoteError = useCancelStore((state) => state.quoteError);
  const submitting = useCancelStore((state) => state.submitting);
  const [cancelType, setCancelType] = useState('');
  // 입력 전에는 정책 환불액(최소값)을 기본으로 보여준다
  const [refundInput, setRefundInput] = useState(null);
  const [afterCancel, setAfterCancel] = useState('');
  const [reason, setReason] = useState('');
  const [isConfirmOpen, setIsConfirmOpen] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  useEffect(() => {
    loadReservation('owner', reservationId);
    loadQuote(reservationId);
    return clearQuote;
  }, [loadReservation, loadQuote, clearQuote, reservationId]);

  const isDetailReady =
    detailStatus === 'succeeded' && String(detail?.summary.reservationId) === reservationId;
  const summary = isDetailReady ? detail.summary : null;
  const isLoading = detailStatus === 'loading' || quoteStatus === 'loading';
  const canCancel = summary?.cancellable && quoteStatus === 'succeeded';

  const isCustomerRequest = cancelType === 'CUSTOMER_REQUEST';
  const refundValue = refundInput ?? String(quote?.policyRefundAmount ?? '');
  const refundError = canCancel && isCustomerRequest ? validateRefund(refundValue, quote) : null;
  // 화면 표시용 환불액. 매장 사유는 서버가 납부액 전액을 환불한다
  const refundAmount = isCustomerRequest ? Number(refundValue) : quote?.paidAmount;
  const isFormValid = Boolean(cancelType) && Boolean(afterCancel) && !refundError;

  const submit = async () => {
    setSubmitError(null);
    try {
      await cancelByOwner(reservationId, {
        cancelType,
        refundAmount: isCustomerRequest ? Number(refundValue) : null,
        closeAfterCancel: afterCancel === 'close',
        reason: reason.trim() || null,
      });
      navigate(resultPath(reservationId), { replace: true });
    } catch (error) {
      setIsConfirmOpen(false);
      setSubmitError(error);
      // 환불 범위 오류가 아니면 예약 상태가 바뀌었을 수 있으므로 다시 조회한다
      if (error.code !== 'CANCEL_006') {
        loadReservation('owner', reservationId);
        loadQuote(reservationId);
      }
    }
  };

  return (
    <div className="b-stack">
      <div className="b-row b-title-row">
        <div>
          <h1>예약 취소</h1>
          <p className="b-muted">취소 구분과 취소 후 시간 처리를 골라 주세요.</p>
        </div>
      </div>

      <Panel padded className={`b-stack ${styles.panel}`}>
        {isLoading && <p role="status">취소 정보를 불러오는 중입니다.</p>}
        {detailStatus === 'failed' && (
          <Alert as="p" tone="error" role="alert">
            {detailError.message}
          </Alert>
        )}

        {summary && (
          <>
            <CancelTargetSummary summary={summary} />
            <Notice>
              <h3>예약자</h3>
              <div className="b-row">
                <strong>{summary.customerName}</strong>
                <span>{detail.customerPhone}</span>
              </div>
            </Notice>
          </>
        )}

        {summary && !summary.cancellable && (
          <Notice>
            <p>
              {CANCEL_STARTED_STATUSES.includes(summary.status)
                ? '이미 취소가 접수된 예약이에요.'
                : '확정된 예약만 시술 시작 전까지 취소할 수 있어요.'}
            </p>
          </Notice>
        )}
        {summary && CANCEL_STARTED_STATUSES.includes(summary.status) && (
          <Button as={Link} variant="primary" to={resultPath(reservationId)}>
            취소 진행 상황 보기
          </Button>
        )}
        {summary?.cancellable && quoteStatus === 'failed' && (
          <Alert as="p" tone="error" role="alert">
            {quoteError.message}
          </Alert>
        )}

        {canCancel && (
          <form
            className="b-stack"
            onSubmit={(event) => {
              event.preventDefault();
              if (isFormValid) {
                setIsConfirmOpen(true);
              }
            }}
          >
            <fieldset className={styles.choices}>
              <legend>취소 구분</legend>
              {CANCEL_TYPE_OPTIONS.map((option) => (
                <label
                  key={option.value}
                  className={cancelType === option.value ? 'is-selected' : ''}
                >
                  <input
                    type="radio"
                    name="cancel-type"
                    value={option.value}
                    checked={cancelType === option.value}
                    onChange={() => setCancelType(option.value)}
                  />
                  <span>
                    <strong>{CANCEL_TYPE[option.value]}</strong>
                    <small>{option.description}</small>
                  </span>
                </label>
              ))}
            </fieldset>

            {cancelType === 'SHOP_FAULT' && (
              <Notice>
                <p>
                  납부 예약금 {formatMoney(quote.paidAmount)}을 전액 환불합니다. 고객에게는 문자가
                  가지 않으니 취소 후 직접 연락해 주세요.
                </p>
              </Notice>
            )}

            {isCustomerRequest && (
              <label className={styles.refund}>
                환불액
                <div className={styles.refundInput}>
                  <input
                    type="number"
                    inputMode="numeric"
                    min={quote.policyRefundAmount}
                    max={quote.paidAmount}
                    step={1}
                    value={refundValue}
                    aria-invalid={Boolean(refundError)}
                    onChange={(event) => setRefundInput(event.target.value)}
                  />
                  <span>원</span>
                </div>
                <div className="b-flex">
                  <Button
                    size="small"
                    type="button"
                    onClick={() => setRefundInput(String(quote.policyRefundAmount))}
                  >
                    최소 {formatMoney(quote.policyRefundAmount)}
                  </Button>
                  <Button
                    size="small"
                    type="button"
                    onClick={() => setRefundInput(String(quote.paidAmount))}
                  >
                    전액 {formatMoney(quote.paidAmount)}
                  </Button>
                </div>
                <small>
                  고객 정책 환불액({quote.refundRate}%) {formatMoney(quote.policyRefundAmount)}{' '}
                  이상, 납부 예약금 {formatMoney(quote.paidAmount)} 이하
                </small>
                {refundError && (
                  <Alert as="small" tone="error" role="alert">
                    {refundError}
                  </Alert>
                )}
              </label>
            )}

            <fieldset className={styles.choices}>
              <legend>취소 후 시간</legend>
              {AFTER_CANCEL_OPTIONS.map((option) => (
                <label
                  key={option.value}
                  className={afterCancel === option.value ? 'is-selected' : ''}
                >
                  <input
                    type="radio"
                    name="after-cancel"
                    value={option.value}
                    checked={afterCancel === option.value}
                    onChange={() => setAfterCancel(option.value)}
                  />
                  <span>
                    <strong>{option.label}</strong>
                    <small>{option.description}</small>
                  </span>
                </label>
              ))}
            </fieldset>

            <label className={styles.refund}>
              취소 사유 (선택)
              <textarea
                rows={3}
                maxLength={REASON_MAX_LENGTH}
                value={reason}
                placeholder="예: 원장님 개인 사정, 고객 전화 요청"
                onChange={(event) => setReason(event.target.value)}
              />
              <small>
                {reason.length}/{REASON_MAX_LENGTH}
              </small>
            </label>

            {submitError && (
              <Alert as="p" tone="error" role="alert">
                {submitError.message}
              </Alert>
            )}

            <div className={styles.actions}>
              <Button as={Link} to={ROUTES.OWNER_BOOKINGS}>
                돌아가기
              </Button>
              <Button variant="danger" type="submit" disabled={!isFormValid || submitting}>
                예약 취소하기
              </Button>
            </div>
          </form>
        )}

        {summary && !canCancel && !isLoading && (
          <Button as={Link} to={ROUTES.OWNER_BOOKINGS}>
            예약 현황으로
          </Button>
        )}
      </Panel>

      {isConfirmOpen && (
        <Dialog title="예약을 취소할까요?" onClose={() => setIsConfirmOpen(false)}>
          <div className="b-stack">
            <dl className="b-details">
              <div>
                <dt>취소 구분</dt>
                <dd>{CANCEL_TYPE[cancelType]}</dd>
              </div>
              <div>
                <dt>환불액</dt>
                <dd className="b-pink">{formatMoney(refundAmount)}</dd>
              </div>
              <div>
                <dt>취소 후 시간</dt>
                <dd>{afterCancel === 'close' ? '닫기' : '다시 열기'}</dd>
              </div>
            </dl>
            <p className="b-muted">취소 후에는 되돌릴 수 없습니다.</p>
            <div className={styles.actions}>
              <Button type="button" onClick={() => setIsConfirmOpen(false)}>
                아니요
              </Button>
              <Button variant="danger" type="button" disabled={submitting} onClick={submit}>
                {submitting ? '취소하는 중…' : '취소 확정'}
              </Button>
            </div>
          </div>
        </Dialog>
      )}
    </div>
  );
}
