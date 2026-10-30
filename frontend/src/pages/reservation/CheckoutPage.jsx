import { useEffect, useState } from 'react';
import { generatePath, Link, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { money } from '@/utils/demoData';
import { datePart, timePart } from '@/utils/scheduleDate';
import { useReservationStore } from '@/store/useReservationStore';
import Asset from '@/components/common/Asset';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import Panel from '@/components/common/Panel';
import Notice from '@/components/common/Notice';
import EmptyState from '@/components/common/EmptyState';
import Alert from '@/components/common/Alert';
import styles from './CheckoutPage.module.css';

const EXPIRED_MESSAGE = '임시 확보 시간이 만료되었습니다. 시간을 다시 선택해 주세요.';
// 남은 시간이 이 값(초) 이하면 띠를 경고색으로 바꾼다
const WARNING_SECONDS = 60;

const formatRemaining = (seconds) =>
  `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;

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
  const clockOffset = useReservationStore((s) => s.detailClockOffset);
  const [now, setNow] = useState(Date.now);
  const [policy, setPolicy] = useState(false);
  const [demo, setDemo] = useState(false);
  const [request, setRequest] = useState('');
  const [error, setError] = useState('');
  const [headerHeight, setHeaderHeight] = useState(0);
  const [showExpiredDialog, setShowExpiredDialog] = useState(false);

  // 기기 시계가 서버와 달라도 서버가 만료로 보는 순간에 맞춰 세도록, 응답 때 잰 차이만큼 보정한 서버 기준 현재 시각
  const serverNow = now + clockOffset;
  // 화면을 보는 중에 점유(HOLD) 시간이 끝난 순간. 처음부터 만료 상태로 열린 예약은 해당하지 않는다
  const holdEndedWhileOpen =
    detail?.summary?.status === 'HOLD' && serverNow >= new Date(detail.holdExpiresAt).getTime();

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

  // 만료 순간 한 번 안내 팝업을 띄우고, 서버 예약 상태와 서버 시각을 다시 받는다.
  // 페이지를 자동으로 옮기지 않는다 (결제창 진행 중일 수 있고, 입력한 요청사항이 사라진다)
  useEffect(() => {
    if (holdEndedWhileOpen) {
      setShowExpiredDialog(true);
      loadReservation('my', reservationId);
    }
  }, [holdEndedWhileOpen, loadReservation, reservationId]);

  // 남은 시간 띠를 고정 헤더 바로 아래에 붙인다. 헤더 높이가 화면 폭에 따라 바뀌므로(모바일은 검색창이 줄바꿈) 실제 높이를 잰다
  useEffect(() => {
    const header = document.querySelector('header');
    if (!header) {
      return undefined;
    }
    const update = () => setHeaderHeight(header.offsetHeight);
    update();
    const observer = new ResizeObserver(update);
    observer.observe(header);
    return () => observer.disconnect();
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
  // 만료 확인처럼 다시 불러오는 동안에는 기존 화면(팝업 포함)을 유지한다
  if (status === 'idle' || (status === 'loading' && !detail)) {
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
  // 시간은 매장 상세에서 다시 고른다
  const shopLink = generatePath(ROUTES.SHOP_DETAIL, { shopId: summary.shopId });

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
      ? Math.max(0, Math.ceil((new Date(holdExpiresAt).getTime() - serverNow) / 1000))
      : 0;
  const expired = remaining === 0;
  const warning = !expired && remaining <= WARNING_SECONDS;
  let holdBarTone = '';
  if (expired) {
    holdBarTone = styles.holdBarExpired;
  } else if (warning) {
    holdBarTone = styles.holdBarWarning;
  }

  /* 결제 진행하기 버튼 클릭시 실행하는 함수입니다. 결제 담당은 여기를 채워주세요. */
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
      <div className={`b-hold-bar ${styles.holdBar} ${holdBarTone}`} style={{ top: headerHeight }}>
        <div className="b-container b-row">
          {expired ? (
            <>
              <span>
                <strong>임시 확보 시간이 끝났어요.</strong> 같은 시간이 아직 비어 있으면 다시 선택할
                수 있어요.
              </span>
              <Link className={styles.holdBarAction} to={shopLink}>
                시간 다시 선택 →
              </Link>
            </>
          ) : (
            <>
              <span>
                {warning ? (
                  <>
                    <strong>곧 확보가 풀려요!</strong> — 1분 안에 결제를 완료해 주세요.
                  </>
                ) : (
                  <>
                    <strong>빈자리 임시 확보 중!</strong> — 제한 시간 내에 결제를 완료해 주세요.
                  </>
                )}
              </span>
              <span>
                남은 시간 <strong>{formatRemaining(remaining)}</strong>
              </span>
            </>
          )}
        </div>
      </div>

      <div className="b-container b-page">
        <Link className="b-back" to={shopLink}>
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
                  <small>
                    가입할 때 저장한 번호입니다. 예약이 확정되면 이 번호로 문자가 갑니다.
                  </small>
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
              <Button as={Link} variant="primary" wide to={shopLink}>
                시간 다시 선택하기
              </Button>
            ) : (
              <Button variant="primary" wide onClick={handlePay}>
                {money(summary.deposit)} 결제 진행하기
              </Button>
            )}
            <p className="b-help">결제하지 않으면 잡아 둔 시간은 5분 뒤 자동으로 풀립니다.</p>
          </Panel>
        </div>
      </div>

      {/* 다시 받은 서버 시각으로 아직 점유 중이면(남은 시간 재계산) 만료 팝업을 닫은 것으로 본다 */}
      {showExpiredDialog && expired && (
        <Dialog title="빈자리 확보가 풀렸어요" onClose={() => setShowExpiredDialog(false)}>
          <p>
            결제 제한 시간 5분이 지나 잡아 둔 시간이 풀렸어요. 결제는 진행되지 않았어요. 같은 시간이
            아직 비어 있으면 다시 선택할 수 있어요.
          </p>
          <div className={`b-flex ${styles.dialogActions}`}>
            <Button as={Link} variant="primary" to={shopLink}>
              시간 다시 선택
            </Button>
            <Button as={Link} to={ROUTES.HOME}>
              다른 빈자리 보기
            </Button>
          </div>
        </Dialog>
      )}
    </>
  );
}
