import { useEffect, useState } from 'react';
import { generatePath, Link, useParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { CANCEL_TYPE } from '@/constants/reservationStatus';
import { useReservationStore } from '@/store/useReservationStore';
import { useCancelStore } from '@/store/useCancelStore';
import { formatDateTime, formatMoney } from '@/utils/reservationFormat';
import CancelTargetSummary from '@/components/cancel/CancelTargetSummary';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import Notice from '@/components/common/Notice';
import Panel from '@/components/common/Panel';
import styles from './CancelResultPage.module.css';

// 취소 처리 중이면 5초마다 최대 1분간 예약 상태를 다시 조회한다
const POLL_INTERVAL_MS = 5000;
const MAX_POLL_COUNT = 12;

const VIEWER = {
  customer: {
    scope: 'my',
    listPath: ROUTES.MY_RESERVATIONS,
    listLabel: '예약 목록으로',
    homePath: ROUTES.HOME,
    homeLabel: '메인으로',
    cancelRoute: ROUTES.MY_RESERVATION_CANCEL,
  },
  owner: {
    scope: 'owner',
    listPath: ROUTES.OWNER_BOOKINGS,
    listLabel: '예약 현황으로',
    homePath: ROUTES.OWNER,
    homeLabel: '대시보드로',
    cancelRoute: ROUTES.OWNER_RESERVATION_CANCEL,
  },
};

/**
 * 서버 예약 상태로 결과를 나눈다. 상세를 받기 전에는 직전 실행 결과로 먼저 그린다.
 * - completed: 결제 취소 완료 (CANCELLED)
 * - pending: 결제 취소 진행 중·결과 불명 (CANCEL_PENDING, 시간은 계속 막힘)
 * - failed: 결제 취소 실패로 예약이 확정 상태로 돌아감
 */
function resolvePhase(detail, lastResult) {
  if (detail) {
    const reservationStatus = detail.summary.status;
    if (reservationStatus === 'CANCELLED' || detail.cancel?.status === 'COMPLETED') {
      return 'completed';
    }
    if (reservationStatus === 'CANCEL_PENDING') {
      return 'pending';
    }
    return detail.cancel?.status === 'FAILED' ? 'failed' : 'none';
  }
  if (lastResult) {
    return lastResult.cancelStatus === 'COMPLETED' ? 'completed' : 'pending';
  }
  return null;
}

/**
 * 취소 결과 (E-F3, 고객·사장님 공용). 완료·처리 중·확인 필요·실패를 구분해 안내한다.
 * 결과는 주소의 예약 ID 로 서버 상태를 다시 조회하므로 새로고침해도 복원된다.
 */
export default function CancelResultPage({ viewer }) {
  const { reservationId } = useParams();
  const { scope, listPath, listLabel, homePath, homeLabel, cancelRoute } = VIEWER[viewer];
  const loadReservation = useReservationStore((state) => state.loadReservation);
  const storedDetail = useReservationStore((state) => state.detail);
  const detailStatus = useReservationStore((state) => state.detailStatus);
  const detailError = useReservationStore((state) => state.detailError);
  const storedResult = useCancelStore((state) => state.lastResult);
  const [pollCount, setPollCount] = useState(0);
  // 취소 전 화면에서 불러둔 상세(CONFIRMED)가 남아 있을 수 있으므로, 이 화면에서 새로 조회한 뒤에만 상세로 판정한다.
  // 그 전까지는 직전 실행 결과(lastResult)로 먼저 그린다
  const [isDetailFresh, setIsDetailFresh] = useState(false);

  // 다른 화면에서 보던 예약 상세·취소 결과가 남아 있을 수 있으므로 주소의 예약 것만 쓴다
  const detail =
    isDetailFresh && String(storedDetail?.summary.reservationId) === reservationId
      ? storedDetail
      : null;
  const lastResult = String(storedResult?.reservationId) === reservationId ? storedResult : null;
  const phase = resolvePhase(detail, lastResult);
  const isPolling = phase === 'pending' && pollCount < MAX_POLL_COUNT;

  useEffect(() => {
    let isActive = true;
    setIsDetailFresh(false);
    loadReservation(scope, reservationId).then(() => {
      if (isActive) {
        setIsDetailFresh(true);
      }
    });
    return () => {
      isActive = false;
    };
  }, [loadReservation, scope, reservationId]);

  useEffect(() => {
    if (!isPolling) {
      return undefined;
    }
    const timer = setTimeout(() => {
      setPollCount((count) => count + 1);
      loadReservation(scope, reservationId);
    }, POLL_INTERVAL_MS);
    return () => clearTimeout(timer);
  }, [isPolling, pollCount, loadReservation, scope, reservationId]);

  const recheck = () => {
    setPollCount(0);
    loadReservation(scope, reservationId);
  };

  const cancel = detail?.cancel ?? lastResult;
  const refundAmount = cancel?.refundAmount;
  const cancelType = cancel?.cancelType;
  const completedAt = cancel?.completedAt;
  // 매장 사유 취소도 서버가 문자를 보낸다. 번호가 없으면 생략되므로 연락처는 함께 보여 준다.
  const showCustomerContact = viewer === 'owner' && cancelType === 'SHOP_FAULT';
  const customerName = detail?.summary.customerName ?? lastResult?.customerName;
  const customerPhone = detail?.customerPhone ?? lastResult?.customerPhone;
  const Wrapper = viewer === 'owner' ? 'div' : 'section';

  return (
    <Wrapper className={viewer === 'owner' ? 'b-stack' : 'b-container b-page'}>
      <Panel padded className={`b-stack ${styles.panel}`}>
        {phase === null && detailStatus === 'loading' && (
          <p role="status">취소 결과를 불러오는 중입니다.</p>
        )}
        {phase === null && detailStatus === 'failed' && (
          <Alert as="p" tone="error" role="alert">
            {detailError.message}
          </Alert>
        )}

        {phase === 'completed' && (
          <>
            <div className={styles.done}>
              <span className={styles.check} aria-hidden="true">
                ✓
              </span>
              <h1>취소가 완료되었습니다</h1>
            </div>
            <div className={styles.amount}>
              <span>환불 금액</span>
              <strong>{formatMoney(refundAmount)}</strong>
              <small>
                {CANCEL_TYPE[cancelType] ?? cancelType} · 완료 {formatDateTime(completedAt)}
              </small>
            </div>
            {viewer === 'owner' && lastResult && (
              <Notice>
                <p>
                  {lastResult.closed
                    ? '취소한 시간을 닫았어요. 이 시간에는 예약을 받지 않습니다.'
                    : '취소한 시간을 다시 열었어요. 다른 고객이 예약할 수 있습니다.'}
                </p>
              </Notice>
            )}
          </>
        )}

        {phase === 'pending' && (
          <>
            <h1>{isPolling ? '환불을 처리하고 있어요' : '환불 결과 확인이 필요해요'}</h1>
            <Notice className="b-stack">
              <p>
                {isPolling
                  ? '결제 취소 결과를 확인하는 중입니다. 잠시만 기다려 주세요.'
                  : '결제 대행사의 환불 결과를 아직 받지 못했습니다. 잠시 후 다시 확인해 주세요.'}
              </p>
              <small>
                확인될 때까지 예약은 취소 처리 중으로 남고, 이 시간은 다른 예약을 받지 않습니다.
              </small>
            </Notice>
            {refundAmount !== undefined && (
              <p className="b-muted">환불 예정 금액 {formatMoney(refundAmount)}</p>
            )}
            {!isPolling && (
              <Button type="button" onClick={recheck} disabled={detailStatus === 'loading'}>
                다시 확인하기
              </Button>
            )}
          </>
        )}

        {phase === 'failed' && (
          <>
            <h1>결제 취소에 실패했어요</h1>
            <Alert as="p" tone="error" role="alert">
              예약은 확정 상태로 유지됩니다. 잠시 후 다시 시도해 주세요.
            </Alert>
            <Button as={Link} variant="primary" to={generatePath(cancelRoute, { reservationId })}>
              다시 시도하기
            </Button>
          </>
        )}

        {phase === 'none' && (
          <Notice>
            <p>이 예약의 취소 내역이 없습니다.</p>
          </Notice>
        )}

        {showCustomerContact && (phase === 'completed' || phase === 'pending') && (
          <Notice className="b-stack">
            <h3>고객에게 직접 연락해 주세요</h3>
            <p>
              취소가 완료되면 고객 휴대폰 번호로 문자가 갑니다. 번호가 없으면 문자는 생략됩니다.
            </p>
            <div className="b-row">
              <strong>{customerName}</strong>
              <a href={`tel:${customerPhone}`}>{customerPhone}</a>
            </div>
          </Notice>
        )}

        {detail && <CancelTargetSummary summary={detail.summary} />}

        <div className={styles.actions}>
          <Button as={Link} variant="primary" to={listPath}>
            {listLabel}
          </Button>
          <Button as={Link} to={homePath}>
            {homeLabel}
          </Button>
        </div>
      </Panel>
    </Wrapper>
  );
}
