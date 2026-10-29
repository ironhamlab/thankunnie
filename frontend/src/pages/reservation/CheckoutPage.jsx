import { useEffect, useState } from 'react';
import { generatePath, Link, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { SERVICES, money } from '@/utils/demoData';
import { datePart, timePart } from '@/utils/scheduleDate';
import { useReservationStore } from '@/store/useReservationStore';
import Asset from '@/components/common/Asset';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import Notice from '@/components/common/Notice';
import EmptyState from '@/components/common/EmptyState';
import Alert from '@/components/common/Alert';
import styles from './CheckoutPage.module.css';

const EXPIRED_MESSAGE = '임시 확보 시간이 만료되었습니다. 시간을 다시 선택해 주세요.';

/**
 * 예약을 불러오지 못했을 때 안내. 다시 불러와도 결과가 같은 경우(없는 예약·남의 예약, 로그인 필요)는
 * 다시 불러오기 대신 갈 곳을 보여준다.
 */
function LoadFailed({ error, onRetry }) {
  if (error.code === 'COMMON_404' || error.code === 'COMMON_403') {
    return (
      <EmptyState className="b-container">
        <h1>예약을 찾을 수 없습니다</h1>
        <p>이미 만료됐거나 잘못된 주소일 수 있어요.</p>
        <div className="b-flex b-center">
          <Button as={Link} variant="primary" to={ROUTES.HOME}>
            빈자리 탐색
          </Button>
          <Button as={Link} to={ROUTES.MY_RESERVATIONS}>
            내 예약 보기
          </Button>
        </div>
      </EmptyState>
    );
  }
  if (error.code === 'COMMON_401') {
    return (
      <EmptyState className="b-container">
        <h1>로그인이 필요합니다</h1>
        <p>예약 정보는 예약한 고객 계정으로 로그인해야 볼 수 있어요.</p>
        <Button as={Link} variant="primary" to={ROUTES.LOGIN}>
          로그인
        </Button>
      </EmptyState>
    );
  }
  return (
    <EmptyState className="b-container">
      <h1>예약 정보를 불러오지 못했습니다</h1>
      <Alert as="p" tone="error" role="alert">
        {error.message}
      </Alert>
      <Button onClick={onRetry}>다시 불러오기</Button>
    </EmptyState>
  );
}

/**
 * 예약금 결제 화면. 주소의 reservationId 로 서버에서 점유(HOLD) 중인 예약을 불러온다.
 * 남은 시간·금액은 서버 값(holdExpiresAt, appliedPrice, deposit)으로 보여준다.
 */
export default function CheckoutPage() {
  const [params] = useSearchParams();
  const reservationId = params.get('reservationId');
  const loadReservation = useReservationStore((s) => s.loadReservation);
  const clearDetail = useReservationStore((s) => s.clearDetail);
  const detail = useReservationStore((s) => s.detail);
  const status = useReservationStore((s) => s.detailStatus);
  const loadError = useReservationStore((s) => s.detailError);
  const [now, setNow] = useState(Date.now);
  const [policy, setPolicy] = useState(false);
  const [demo, setDemo] = useState(false);
  const [request, setRequest] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (reservationId) {
      loadReservation('my', reservationId);
    }
    return clearDetail;
  }, [reservationId, loadReservation, clearDetail]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  if (!reservationId) {
    return (
      <EmptyState className="b-container">
        <h1>선택한 예약이 없습니다</h1>
        <p>빈자리와 시간을 먼저 선택해 주세요.</p>
        <Button as={Link} variant="primary" to={ROUTES.HOME}>
          빈자리 탐색
        </Button>
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
    return <LoadFailed error={loadError} onRetry={() => loadReservation('my', reservationId)} />;
  }

  const { summary, holdExpiresAt, customerPhone } = detail;
  // 시간 선택 화면은 아직 시연용 시술 주소(/reserve/nail 등)를 쓰므로 서버 시술 번호로 되돌아갈 주소를 찾는다
  const service = SERVICES.find((s) => s.treatmentId === summary.treatmentId);
  const reserveLink = service ? generatePath(ROUTES.RESERVE, { id: service.id }) : ROUTES.HOME;

  if (summary.status === 'CONFIRMED') {
    return (
      <EmptyState className="b-container">
        <h1>이미 결제가 끝난 예약입니다</h1>
        <Button as={Link} variant="primary" to={ROUTES.MY_RESERVATIONS}>
          내 예약 보기
        </Button>
      </EmptyState>
    );
  }
  // 취소된 예약도 본인 상세 조회로 열리므로, 점유 만료 안내와 구분해 보여준다
  if (summary.status === 'CANCEL_PENDING' || summary.status === 'CANCELLED') {
    return (
      <EmptyState className="b-container">
        <h1>
          {summary.status === 'CANCEL_PENDING'
            ? '취소 처리 중인 예약입니다'
            : '취소된 예약이라 결제할 수 없습니다'}
        </h1>
        <p>취소·환불 진행 상황은 내 예약에서 확인할 수 있어요.</p>
        <div className="b-flex b-center">
          <Button as={Link} variant="primary" to={ROUTES.MY_RESERVATIONS}>
            내 예약 보기
          </Button>
          <Button as={Link} to={ROUTES.HOME}>
            빈자리 탐색
          </Button>
        </div>
      </EmptyState>
    );
  }

  const remaining =
    summary.status === 'HOLD'
      ? Math.max(0, Math.ceil((new Date(holdExpiresAt).getTime() - now) / 1000))
      : 0;
  const expired = remaining === 0;

  const handlePay = () => {
    setError('');
    if (!policy || !demo) {
      setError('필수 약관과 모의 결제 확인에 동의해 주세요.');
      return;
    }
    // 결제 요청(아임포트)과 결제 확인(POST /api/v1/payments/verify)은 결제 담당 작업에서 연결한다.
    // 요청사항은 결제 확인 요청에 customerRequest 로 함께 보낸다 (docs/api/reservations.md)
    setError('결제 연결은 준비 중입니다.');
  };

  return (
    <>
      <div className="b-hold-bar">
        <div className="b-container b-row">
          <span>
            <strong>빈자리 임시 확보 중!</strong> — 제한 시간 내에 결제를 완료해 주세요.
          </span>
          <span>
            남은 시간{' '}
            <strong>
              {String(Math.floor(remaining / 60)).padStart(2, '0')}:
              {String(remaining % 60).padStart(2, '0')}
            </strong>
          </span>
        </div>
      </div>
      <div className="b-container b-page">
        <Link className="b-back" to={reserveLink}>
          ← 시간 다시 선택하기
        </Link>
        <div className="b-columns">
          <div className="b-stack">
            <Panel as="section" padded>
              <h2 className="b-panel-heading">1. 예약 정보 확인</h2>
              <div className="b-booking-service">
                <Asset name="8-4-0.png" className="b-thumb" alt={`${summary.shopName} 매장 내부`} />
                <div>
                  <h2>{summary.shopName}</h2>
                  <Notice>
                    <strong>{summary.treatmentName}</strong>
                    <p className="b-pink">
                      {datePart(summary.startsAt)} {timePart(summary.startsAt)} ~{' '}
                      {timePart(summary.endsAt)}
                    </p>
                  </Notice>
                </div>
              </div>
            </Panel>
            <Panel as="section" padded>
              <h2 className="b-panel-heading">2. 예약자 정보</h2>
              <div className="b-form-grid">
                <label>
                  예약자명
                  <input value={summary.customerName} readOnly />
                </label>
                <label>
                  휴대폰 번호
                  <input value={customerPhone ?? ''} readOnly />
                  <small>예약 확정 및 매장 안내 알림톡 수신 번호</small>
                </label>
                <label className="b-span-two">
                  원장님께 남길 요청사항 (선택)
                  <textarea
                    rows="3"
                    maxLength={1000}
                    value={request}
                    onChange={(e) => setRequest(e.target.value)}
                  />
                  <small>결제가 완료되면 예약과 함께 저장됩니다. ({request.length}/1000)</small>
                </label>
              </div>
            </Panel>
            <Panel as="section" padded>
              <h2 className="b-panel-heading">3. 필수 약관 및 취소 정책 동의</h2>
              <label className={styles.check}>
                <input
                  type="checkbox"
                  checked={policy}
                  onChange={(e) => setPolicy(e.target.checked)}
                />
                <span>
                  <strong>[필수] 취소 및 모의 환불 정책에 동의합니다</strong>
                  <small>
                    방문 2시간 전까지 전액 모의 환불, 2시간 미만 임박 취소 시 예약금의 50% 모의
                    환불이 적용됩니다. 시술 시작 후에는 취소할 수 없습니다.
                  </small>
                </span>
              </label>
              <label className={styles.check}>
                <input type="checkbox" checked={demo} onChange={(e) => setDemo(e.target.checked)} />
                <span>
                  <strong>[필수] MVP 모의 결제 확인</strong>
                  <small>
                    본 화면은 모의 결제 환경이며 실제 금융 결제 및 대금 청구가 발생하지 않음을
                    확인합니다.
                  </small>
                </span>
              </label>
            </Panel>
            <Panel as="section" padded>
              <h2 className="b-panel-heading">4. 결제 수단</h2>
              <Notice>
                <strong>선택된 결제 수단 · 모의 안전 결제</strong>
                <div className={styles.paymentMethod}>
                  <span>언니</span>
                  <div>
                    <strong>테스트 결제용 가상 수단 (모의 간편결제)</strong>
                    <p>실제 카드 번호, CVC, 비밀번호 입력이 필요 없습니다.</p>
                  </div>
                </div>
              </Notice>
            </Panel>
          </div>
          <Panel as="aside" padded className="b-summary">
            <h2>결제 금액 내역</h2>
            <dl className="b-details">
              <div>
                <dt>
                  <strong>총 시술 금액</strong>
                </dt>
                <dd>{money(summary.appliedPrice)}</dd>
              </div>
            </dl>
            <div className="b-highlight">
              <div className="b-row">
                <strong>지금 결제할 예약금</strong>
                <h2 className="b-pink">{money(summary.deposit)}</h2>
              </div>
              <small>노쇼 방지를 위한 예약금입니다.</small>
            </div>
            <dl className="b-details">
              <div>
                <dt>매장 방문 시 지불할 잔금</dt>
                <dd>{money(summary.balance)}</dd>
              </div>
            </dl>
            {(error || expired) && (
              <Alert as="p" tone="error" role="alert">
                {expired ? EXPIRED_MESSAGE : error}
              </Alert>
            )}
            {expired ? (
              <Button as={Link} variant="primary" wide to={reserveLink}>
                시간 다시 선택하기
              </Button>
            ) : (
              <Button variant="primary" wide onClick={handlePay}>
                {money(summary.deposit)} 결제 진행하기
              </Button>
            )}
            <p className="b-help">
              결제하지 않으면 잡아 둔 시간은 5분 뒤 자동으로 풀립니다. MVP 모의 결제 환경으로 실제
              금융 결제가 발생하지 않습니다.
            </p>
          </Panel>
        </div>
      </div>
    </>
  );
}
