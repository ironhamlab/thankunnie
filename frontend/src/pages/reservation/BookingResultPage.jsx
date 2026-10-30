import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { PAYMENT_STATUS, reservationStatusOf } from '@/constants/reservationStatus';
import { datePart, timePart } from '@/utils/scheduleDate';
import { takePendingRequest, verifyErrorMessage } from '@/utils/payment';
import { useReservationStore } from '@/store/useReservationStore';
import { usePaymentStore } from '@/store/usePaymentStore';
import BookingBreakdown from '@/pages/reservation/components/BookingBreakdown';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Notice from '@/components/common/Notice';
import EmptyState from '@/components/common/EmptyState';
import Alert from '@/components/common/Alert';
import styles from './BookingResultPage.module.css';

// 확정되지 않은 예약을 열었을 때 보여줄 안내. 결제 화면으로 돌아갈 수 있는 건 결제 대기(HOLD)뿐이다
const NOT_CONFIRMED = {
  HOLD: {
    title: '결제가 아직 완료되지 않았습니다',
    text: '잡아 둔 시간이 남아 있으면 결제 화면에서 다시 결제할 수 있어요.',
  },
  EXPIRED: {
    title: '빈자리 임시 확보 시간이 만료되었습니다',
    text: '결제가 완료되지 않아 잡아 둔 시간이 풀렸어요. 다른 빈자리나 시간을 확인해 주세요.',
  },
  CANCEL_PENDING: {
    title: '취소 처리 중인 예약입니다',
    text: '취소·환불 진행 상황은 내 예약에서 확인할 수 있어요.',
  },
  CANCELLED: {
    title: '취소된 예약입니다',
    text: '취소·환불 진행 상황은 내 예약에서 확인할 수 있어요.',
  },
};

function ResultProblem({ title, children, actions }) {
  return (
    <div className={`b-container b-page ${styles.root}`}>
      <section className={`b-panel ${styles.card} ${styles.problem}`}>
        <header className={styles.header}>
          <div className={styles.icon}>!</div>
          <h1>{title}</h1>
          {children}
        </header>
        <div className={`b-flex ${styles.actions}`}>{actions}</div>
      </section>
    </div>
  );
}

/**
 * 예약금 결제 결과 화면. 주소의 reservationId 로 서버에서 예약을 불러와 보여주므로 새로고침해도 같은 결과가 나온다.
 * 모바일 결제창은 결제 후 이 화면으로 페이지를 옮기며(imp_uid, imp_success), 여기서 결제 확인을 이어서 한다.
 */
export default function BookingResultPage() {
  const [params, setParams] = useSearchParams();
  const reservationId = params.get('reservationId');
  const impUid = params.get('imp_uid');
  const impFailed = params.get('imp_success') === 'false';
  const impErrorMessage = params.get('error_msg');
  const loadReservation = useReservationStore((s) => s.loadReservation);
  const clearDetail = useReservationStore((s) => s.clearDetail);
  const detail = useReservationStore((s) => s.detail);
  const status = useReservationStore((s) => s.detailStatus);
  const loadError = useReservationStore((s) => s.detailError);
  const verifyPayment = usePaymentStore((s) => s.verifyPayment);
  const [verifyError, setVerifyError] = useState('');
  // 개발 모드(StrictMode)에서 effect 가 두 번 돌아도 같은 결제를 한 번만 확인한다. 서버도 중복 확인을 같은 결과로 돌려준다
  const verifiedImpUid = useRef(null);

  const checkoutPath = `${ROUTES.CHECKOUT}?reservationId=${reservationId}`;
  const pendingVerify = Boolean(reservationId && impUid && !impFailed);

  // 모바일 결제창 복귀: 결제 확인 후 주소에서 결제 정보를 지워, 새로고침하면 예약 조회만 하게 한다
  useEffect(() => {
    if (!pendingVerify || verifiedImpUid.current === impUid) {
      return;
    }
    verifiedImpUid.current = impUid;
    verifyPayment({
      impUid,
      reservationId: Number(reservationId),
      customerRequest: takePendingRequest(reservationId),
    })
      .then(() => setParams({ reservationId }, { replace: true }))
      .catch((err) => setVerifyError(verifyErrorMessage(err)));
  }, [pendingVerify, impUid, reservationId, verifyPayment, setParams]);

  useEffect(() => {
    if (reservationId && !impUid) {
      loadReservation('my', reservationId);
    }
    return clearDetail;
  }, [reservationId, impUid, loadReservation, clearDetail]);

  if (!reservationId) {
    return (
      <EmptyState className="b-container">
        <h1>확인할 예약이 없습니다</h1>
        <Button as={Link} variant="primary" to={ROUTES.HOME}>
          빈자리 탐색
        </Button>
      </EmptyState>
    );
  }
  if (impUid && impFailed) {
    return (
      <ResultProblem
        title="결제가 완료되지 않았습니다"
        actions={
          <>
            <Button as={Link} variant="primary" to={checkoutPath}>
              결제 화면으로 돌아가기
            </Button>
            <Button as={Link} to={ROUTES.HOME}>
              다른 빈자리 보기
            </Button>
          </>
        }
      >
        <p>{impErrorMessage || '결제창이 닫혔거나 카드 승인이 거절되었어요.'}</p>
      </ResultProblem>
    );
  }
  if (verifyError) {
    return (
      <ResultProblem
        title="예약을 확정하지 못했습니다"
        actions={
          <>
            <Button as={Link} variant="primary" to={ROUTES.MY_RESERVATIONS}>
              내 예약 보기
            </Button>
            <Button as={Link} to={ROUTES.HOME}>
              다른 빈자리 보기
            </Button>
          </>
        }
      >
        <Alert as="p" tone="error" role="alert">
          {verifyError}
        </Alert>
      </ResultProblem>
    );
  }
  if (pendingVerify) {
    return (
      <EmptyState className="b-container" role="status">
        결제를 확인하고 있습니다. 화면을 닫지 말아 주세요.
      </EmptyState>
    );
  }
  if (status === 'idle' || status === 'loading') {
    return (
      <EmptyState className="b-container" role="status">
        예약 정보를 불러오는 중입니다.
      </EmptyState>
    );
  }
  if (status === 'failed') {
    return (
      <ResultProblem
        title="예약 정보를 불러오지 못했습니다"
        actions={
          <>
            <Button variant="primary" onClick={() => loadReservation('my', reservationId)}>
              다시 불러오기
            </Button>
            <Button as={Link} to={ROUTES.MY_RESERVATIONS}>
              내 예약 보기
            </Button>
          </>
        }
      >
        <p>{loadError.message}</p>
      </ResultProblem>
    );
  }

  const { summary, payment, customerPhone, customerRequest } = detail;

  if (summary.status !== 'CONFIRMED') {
    const notice = NOT_CONFIRMED[summary.status] ?? {
      title: reservationStatusOf(summary.status).label,
      text: '예약 상태는 내 예약에서 확인할 수 있어요.',
    };
    return (
      <ResultProblem
        title={notice.title}
        actions={
          summary.status === 'HOLD' ? (
            <>
              <Button as={Link} variant="primary" to={checkoutPath}>
                결제 화면으로 돌아가기
              </Button>
              <Button as={Link} to={ROUTES.MY_RESERVATIONS}>
                내 예약 보기
              </Button>
            </>
          ) : (
            <>
              <Button as={Link} variant="primary" to={ROUTES.MY_RESERVATIONS}>
                내 예약 보기
              </Button>
              <Button as={Link} to={ROUTES.HOME}>
                다른 빈자리 보기
              </Button>
            </>
          )
        }
      >
        <p>{notice.text}</p>
      </ResultProblem>
    );
  }

  return (
    <div className={`b-container b-page ${styles.root}`}>
      <Notice className="b-demo-disclaimer">
        본 화면은 MVP 모의 결제 환경입니다. 실제 신용카드 과금 및 금융 승인이 발생하지 않습니다.
      </Notice>
      <section className={`b-panel ${styles.card}`}>
        <header className={styles.header}>
          <div className={styles.icon}>✓</div>
          <h1>예약 및 모의 결제가 완료되었습니다!</h1>
          <p>매장 방문 시 확정된 예약 시간 5분 전까지 도착해 주세요.</p>
          <div className={`b-flex ${styles.badges}`}>
            <Badge>예약 상태: {reservationStatusOf(summary.status).label}</Badge>
            {payment && (
              <Badge tone="neutral">
                결제 상태: {PAYMENT_STATUS[payment.status] ?? payment.status}
              </Badge>
            )}
          </div>
        </header>
        <Notice className={styles.reservation}>
          <div className="b-row">
            <small>예약 번호</small>
            <strong>{summary.reservationId}</strong>
          </div>
          <div className="b-two-grid">
            <div>
              <small>예약 매장</small>
              <h3>{summary.shopName}</h3>
              <p>시술 전 매장 위치와 예약 정보를 확인해 주세요.</p>
            </div>
            <div>
              <small>시술 상품</small>
              <h3>{summary.treatmentName}</h3>
            </div>
            <div>
              <small>확정 예약 일시</small>
              <strong className="b-pink">
                {datePart(summary.startsAt)} {timePart(summary.startsAt)} ~{' '}
                {timePart(summary.endsAt)}
              </strong>
            </div>
            <div>
              <small>예약자 정보</small>
              <strong>
                {summary.customerName}
                {customerPhone && ` (${customerPhone})`}
              </strong>
            </div>
            {customerRequest && (
              <div className="b-span-two">
                <small>원장님께 남긴 요청사항</small>
                <p className={styles.request}>{customerRequest}</p>
              </div>
            )}
          </div>
        </Notice>
        <BookingBreakdown summary={summary} payment={payment} />
        <Notice>
          <h3>방문 및 취소 유의사항</h3>
          <p>· 타임세일 특가 예약의 원활한 운영을 위해 예약 시간 전까지 도착해 주세요.</p>
          <p>· 매장 사정으로 시술 일정이 변경될 수 있으므로 방문 전 예약 정보를 확인해 주세요.</p>
        </Notice>
        <div className={`b-flex ${styles.actions}`}>
          <Button as={Link} variant="primary" to={ROUTES.MY_RESERVATIONS}>
            내 예약 상세 보기
          </Button>
          <Button as={Link} variant="danger" to={ROUTES.HOME}>
            다른 빈자리 더 찾아보기
          </Button>
        </div>
      </section>
    </div>
  );
}
