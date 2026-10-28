import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { GRACE_CANCEL_MESSAGE, REFUND_RATE_TIERS } from '@/constants/cancelPolicy';
import { useReservationStore } from '@/store/useReservationStore';
import { useCancelStore } from '@/store/useCancelStore';
import { formatDateTime, formatMoney } from '@/utils/reservationFormat';
import CancelTargetSummary from '@/components/cancel/CancelTargetSummary';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import Notice from '@/components/common/Notice';
import Panel from '@/components/common/Panel';
import styles from './CustomerCancelPage.module.css';

// 실행 시점 재계산 금액이 화면 견적과 달라 재확인이 필요한 오류 (자정 경계·10분 경과, 다른 취소가 무료 취소를 먼저 사용)
const QUOTE_CHANGED_CODES = ['CANCEL_007', 'CANCEL_005'];

/**
 * 고객 취소 확인 (E-F1). 서버 환불 견적(예상 환불액·무료 취소 적용 여부·남은 횟수)을 보여주고 취소를 실행한다.
 * 실행 시 금액이 바뀌었으면 바뀐 금액으로 다시 확인받는다.
 */
export default function CustomerCancelPage() {
  const { reservationId } = useParams();
  const loadReservation = useReservationStore((state) => state.loadReservation);
  const detail = useReservationStore((state) => state.detail);
  const detailStatus = useReservationStore((state) => state.detailStatus);
  const detailError = useReservationStore((state) => state.detailError);
  const loadQuote = useCancelStore((state) => state.loadQuote);
  const clearQuote = useCancelStore((state) => state.clearQuote);
  const cancelByCustomer = useCancelStore((state) => state.cancelByCustomer);
  const quote = useCancelStore((state) => state.quote);
  const quoteStatus = useCancelStore((state) => state.quoteStatus);
  const quoteError = useCancelStore((state) => state.quoteError);
  const submitting = useCancelStore((state) => state.submitting);
  const [isAgreed, setIsAgreed] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [changedQuote, setChangedQuote] = useState(null);
  const [result, setResult] = useState(null);

  useEffect(() => {
    loadReservation('my', reservationId);
    loadQuote(reservationId);
    return clearQuote;
  }, [loadReservation, loadQuote, clearQuote, reservationId]);

  // 목록 화면에서 보던 다른 예약 상세가 남아 있을 수 있으므로 주소의 예약과 같은지 확인한다
  const isDetailReady =
    detailStatus === 'succeeded' && String(detail?.summary.reservationId) === reservationId;

  const submit = async (expectedRefundAmount) => {
    setSubmitError(null);
    try {
      const cancelResult = await cancelByCustomer(reservationId, expectedRefundAmount);
      setChangedQuote(null);
      setResult(cancelResult);
    } catch (error) {
      if (QUOTE_CHANGED_CODES.includes(error.code)) {
        const latest = await loadQuote(reservationId);
        if (latest) {
          setChangedQuote({ before: expectedRefundAmount, after: latest.refundAmount });
          return;
        }
      }
      setChangedQuote(null);
      setSubmitError(error);
      // 이미 취소 접수·시작 시각 경과 등은 예약 상태가 바뀐 것이므로 다시 조회해 버튼을 숨긴다
      loadReservation('my', reservationId);
    }
  };

  if (result) {
    const isCompleted = result.cancelStatus === 'COMPLETED';
    return (
      <div className="b-container b-page">
        <Panel padded className={`b-stack ${styles.panel}`}>
          <h1>{isCompleted ? '예약이 취소되었어요' : '환불 결과를 확인하고 있어요'}</h1>
          <Alert as="p" tone={isCompleted ? 'success' : undefined} role="status">
            {isCompleted
              ? `${formatMoney(result.refundAmount)} 환불이 완료되었습니다.`
              : '확인될 때까지 예약은 취소 처리 중으로 남습니다.'}
          </Alert>
          <Button as={Link} variant="primary" wide to={ROUTES.MY_RESERVATIONS}>
            내 예약으로
          </Button>
        </Panel>
      </div>
    );
  }

  const isLoading = detailStatus === 'loading' || quoteStatus === 'loading';
  const summary = isDetailReady ? detail.summary : null;
  const canCancel = summary?.cancellable && quoteStatus === 'succeeded';

  return (
    <div className="b-container b-page">
      <div className="b-row b-title-row">
        <div>
          <h1>예약 취소</h1>
          <p className="b-muted">환불 예정 금액을 확인한 뒤 취소해 주세요.</p>
        </div>
      </div>

      <Panel padded className={`b-stack ${styles.panel}`}>
        {isLoading && <p role="status">취소 정보를 불러오는 중입니다.</p>}
        {detailStatus === 'failed' && (
          <Alert as="p" tone="error" role="alert">
            {detailError.message}
          </Alert>
        )}
        {summary && <CancelTargetSummary summary={summary} />}

        {summary && !summary.cancellable && (
          <Notice>
            <p>확정된 예약만 시술 시작 전까지 취소할 수 있어요.</p>
          </Notice>
        )}
        {summary?.cancellable && quoteStatus === 'failed' && (
          <Alert as="p" tone="error" role="alert">
            {quoteError.message}
          </Alert>
        )}

        {canCancel && (
          <>
            <div className={`${styles.amount} ${quote.refundAmount === 0 ? styles.isZero : ''}`}>
              <span>환불 예정 금액</span>
              <strong>{formatMoney(quote.refundAmount)}</strong>
              <small>
                납부 예약금 {formatMoney(quote.paidAmount)} 중{' '}
                {formatMoney(quote.paidAmount - quote.refundAmount)} 차감
              </small>
            </div>

            <ul className={styles.tiers} aria-label="환불 정책">
              {REFUND_RATE_TIERS.map((tier) => (
                <li key={tier.rate} className={tier.rate === quote.refundRate ? 'is-current' : ''}>
                  <span>{tier.label}</span>
                  <strong>{tier.rate}% 환불</strong>
                </li>
              ))}
            </ul>

            <Notice className="b-stack">
              <p>{GRACE_CANCEL_MESSAGE[quote.graceCancelResult]}</p>
              <small>
                기본 정책 환불액 {formatMoney(quote.policyRefundAmount)} · 오늘 남은 무료 취소{' '}
                {quote.remainingGraceCount}회
              </small>
            </Notice>

            {quote.refundAmount === 0 && (
              <Alert as="p" tone="error">
                이용 당일 취소는 예약금이 환불되지 않습니다.
              </Alert>
            )}
            {submitError && (
              <Alert as="p" tone="error" role="alert">
                {submitError.message}
              </Alert>
            )}

            <label className={styles.agree}>
              <input
                type="checkbox"
                checked={isAgreed}
                onChange={(event) => setIsAgreed(event.target.checked)}
              />
              환불 정책과 환불 예정 금액을 확인했습니다.
            </label>
            <small className="b-muted">견적 기준 {formatDateTime(quote.quotedAt)}</small>

            <div className={styles.actions}>
              <Button as={Link} to={ROUTES.MY_RESERVATIONS}>
                예약 유지
              </Button>
              <Button
                variant="primary"
                type="button"
                disabled={!isAgreed || submitting}
                onClick={() => submit(quote.refundAmount)}
              >
                {submitting ? '취소하는 중…' : `${formatMoney(quote.refundAmount)} 환불받고 취소`}
              </Button>
            </div>
          </>
        )}

        {summary && !canCancel && !isLoading && (
          <Button as={Link} to={ROUTES.MY_RESERVATIONS}>
            내 예약으로
          </Button>
        )}
      </Panel>

      {changedQuote && (
        <Dialog title="환불 금액이 바뀌었어요" onClose={() => setChangedQuote(null)}>
          <div className="b-stack">
            <p>견적을 확인한 뒤 시간이 지나 환불 예정 금액이 바뀌었습니다.</p>
            <dl className="b-details">
              <div>
                <dt>이전 금액</dt>
                <dd>{formatMoney(changedQuote.before)}</dd>
              </div>
              <div>
                <dt>바뀐 금액</dt>
                <dd className="b-pink">{formatMoney(changedQuote.after)}</dd>
              </div>
            </dl>
            <div className={styles.actions}>
              <Button type="button" onClick={() => setChangedQuote(null)}>
                예약 유지
              </Button>
              <Button
                variant="primary"
                type="button"
                disabled={submitting}
                onClick={() => submit(changedQuote.after)}
              >
                {formatMoney(changedQuote.after)} 환불받고 취소
              </Button>
            </div>
          </div>
        </Dialog>
      )}
    </div>
  );
}
