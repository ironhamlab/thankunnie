import { useEffect, useRef, useState } from 'react';
import { generatePath, Link, useBlocker, useNavigate, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { formatMoney } from '@/utils/reservationFormat';
import { datePart, timePart } from '@/utils/scheduleDate';
import { savePendingRequest, takePendingRequest, verifyErrorMessage } from '@/utils/payment';
import { loadPortOne, requestPay } from '@/utils/portoneLoader';
import { useReservationStore } from '@/store/useReservationStore';
import { usePaymentStore } from '@/store/usePaymentStore';
import { useShopDetailStore } from '@/store/useShopDetailStore';
import RepresentativeImage from '@/pages/owner/components/RepresentativeImage';
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
  const storedDetail = useReservationStore((s) => s.detail);
  // 내 예약·취소 화면에서 보던 다른 예약 상세가 남아 있을 수 있으므로 주소의 예약과 같을 때만 쓴다
  const detail =
    String(storedDetail?.summary?.reservationId) === reservationId ? storedDetail : null;
  const status = useReservationStore((s) => s.detailStatus);
  const loadError = useReservationStore((s) => s.detailError);
  const clockOffset = useReservationStore((s) => s.detailClockOffset);
  const releaseHold = useReservationStore((s) => s.releaseHold);
  const verifyPayment = usePaymentStore((s) => s.verifyPayment);
  const shop = useShopDetailStore((s) => s.shop);
  const loadShop = useShopDetailStore((s) => s.loadShop);
  const clearShop = useShopDetailStore((s) => s.clearShop);
  const navigate = useNavigate();
  const [now, setNow] = useState(Date.now);
  const [paying, setPaying] = useState(false);
  const [policy, setPolicy] = useState(false);
  const [demo, setDemo] = useState(false);
  const [request, setRequest] = useState('');
  const [error, setError] = useState('');
  const [headerHeight, setHeaderHeight] = useState(0);
  const [showExpiredDialog, setShowExpiredDialog] = useState(false);
  const [leaving, setLeaving] = useState(false);
  // 결제 담당: 결제창을 열 때 true 로 바꿔, 결제 중 이동(결제 완료 화면 등)은 막지도 풀지도 않게 한다 (R5)
  const payingRef = useRef(false);

  // 기기 시계가 서버와 달라도 서버가 만료로 보는 순간에 맞춰 세도록, 응답 때 잰 차이만큼 보정한 서버 기준 현재 시각
  const serverNow = now + clockOffset;
  const shopId = detail?.summary?.shopId;
  const holdOpen = detail?.summary?.status === 'HOLD';
  const holdExpiresAtMs = holdOpen ? new Date(detail.holdExpiresAt).getTime() : 0;
  // 화면을 보는 중에 점유(HOLD) 시간이 끝난 순간. 처음부터 만료 상태로 열린 예약은 해당하지 않는다
  const holdEndedWhileOpen = holdOpen && serverNow >= holdExpiresAtMs;
  const holdActive = holdOpen && serverNow < holdExpiresAtMs;

  // 앱 안에서 다른 화면으로 가면(버튼·뒤로가기·헤더 링크) 확인창을 띄우고, 나가기로 하면 점유를 푼다 (R1·R2).
  // 새로고침·탭 닫기는 잡지 않고 5분 만료에 맡긴다 (R3)
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      holdActive && !payingRef.current && currentLocation.pathname !== nextLocation.pathname,
  );

  const leaveCheckout = async () => {
    setLeaving(true);
    // 풀기에 실패해도 이동은 막지 않는다. 남은 점유는 5분 뒤 자동으로 풀린다
    await releaseHold(reservationId);
    setLeaving(false);
    blocker.proceed();
  };

  useEffect(() => {
    if (reservationId) {
      loadReservation('my', reservationId);
    }
    return clearDetail;
  }, [reservationId, loadReservation, clearDetail]);

  // 예약 응답에는 매장 이미지가 없어 매장 상세에서 받는다. 실패하면 기본 그림을 보여준다
  useEffect(() => {
    if (!shopId) {
      return undefined;
    }
    loadShop(shopId);
    return clearShop;
  }, [shopId, loadShop, clearShop]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  // 다른 탭에서 점유를 풀거나 다른 시간으로 바꿨을 수 있으므로, 이 탭이 다시 보이면 서버 상태를 다시 받는다 (R7)
  useEffect(() => {
    if (!reservationId) {
      return undefined;
    }
    const handleVisible = () => {
      if (document.visibilityState === 'visible') {
        loadReservation('my', reservationId);
      }
    };
    document.addEventListener('visibilitychange', handleVisible);
    return () => document.removeEventListener('visibilitychange', handleVisible);
  }, [reservationId, loadReservation]);

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
  if (!detail && status !== 'failed') {
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

  const resultPath = `${ROUTES.BOOKING_RESULT}?reservationId=${reservationId}`;

  /**
   * 결제창(포트원 V1, KG이니시스)을 열고, 결제가 끝나면 서버에 결제 확인을 요청해 예약을 확정한다.
   * 금액·결제 성공 여부는 서버가 포트원에 다시 조회해 판단하므로 결제창 응답은 결제 고유번호만 쓴다.
   */
  const handlePay = async () => {
    setError('');
    if (!policy || !demo) {
      setError('필수 약관과 테스트 결제 확인에 동의해 주세요.');
      return;
    }
    setPaying(true);
    // 결제 중 이동(결제 완료 화면, 모바일 결제창 페이지 이동)은 막지도 풀지도 않는다 (R5)
    payingRef.current = true;
    let moved = false;
    try {
      // 다른 탭에서 풀린 예약에 결제하지 않도록 결제 직전에 서버 상태를 다시 받는다 (R7).
      // 점유가 아니면 화면이 만료 안내로 바뀐다
      await loadReservation('my', reservationId);
      const { detail: current, detailClockOffset } = useReservationStore.getState();
      // 서버는 만료 처리 전까지 시간이 지난 점유도 HOLD 로 돌려줄 수 있으므로, 다시 받은 서버 시각 기준으로 만료도 확인한다.
      // 만료면 결제창을 열지 않는다 (열면 승인 후 서버가 자동 취소한다)
      if (
        current?.summary?.status !== 'HOLD' ||
        Date.now() + detailClockOffset >= new Date(current.holdExpiresAt).getTime()
      ) {
        return;
      }
      const IMP = await loadPortOne();
      const { summary: hold } = current;
      savePendingRequest(reservationId, request);
      const rsp = await requestPay(IMP, {
        pg: 'html5_inicis',
        pay_method: 'card',
        // 서버가 앞부분(reservation-{예약 ID}-)으로 결제와 예약의 짝을 확인한다
        merchant_uid: `reservation-${reservationId}-${Date.now()}`,
        name: `${hold.shopName} ${hold.treatmentName} 예약금`,
        amount: hold.deposit,
        buyer_name: hold.customerName,
        buyer_tel: current.customerPhone ?? '',
        // 모바일은 결제창이 페이지를 옮기므로 결과 화면에서 결제 확인을 이어서 한다
        m_redirect_url: `${window.location.origin}${resultPath}`,
      });
      // 실패는 error_code 로 판단한다. success 는 V1 문서에서 빠진 옛 값이라 false 일 때만 실패로 본다.
      // 그 밖의 응답은 서버가 포트원에 결제 상태를 다시 조회해 판단한다
      if (rsp.error_code || rsp.success === false || !rsp.imp_uid) {
        takePendingRequest(reservationId);
        setError(
          rsp.error_msg
            ? `결제가 완료되지 않았습니다. (${rsp.error_msg})`
            : '결제가 완료되지 않았습니다.',
        );
        return;
      }
      await verifyPayment({
        impUid: rsp.imp_uid,
        reservationId: Number(reservationId),
        customerRequest: takePendingRequest(reservationId),
      });
      moved = true;
      navigate(resultPath, { replace: true });
    } catch (err) {
      setError(err.code ? verifyErrorMessage(err) : err.message);
      // 자동 취소·만료처럼 예약 상태가 바뀌었을 수 있으므로 화면을 서버 상태에 맞춘다
      loadReservation('my', reservationId);
    } finally {
      if (!moved) {
        payingRef.current = false;
        setPaying(false);
      }
    }
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
              <div className={`b-booking-service ${styles.service}`}>
                <RepresentativeImage
                  imageUrl={shop?.imageUrl}
                  kind="shop"
                  name={summary.shopName}
                  className="b-thumb"
                />
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
                    {customerPhone?.trim()
                      ? '가입할 때 저장한 번호입니다. 예약이 확정되면 이 번호로 문자가 갑니다.'
                      : '저장된 휴대폰 번호가 없어 예약 확정 문자를 보내지 않습니다.'}
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
                  <strong>[필수] 취소 및 환불 정책에 동의합니다</strong>
                  <small>
                    이용일 이틀 전까지 취소하면 예약금 전액, 하루 전이면 50%가 환불되고, 당일 취소는
                    환불되지 않습니다.
                  </small>
                  <small>날짜는 자정을 기준으로 나뉘며, 시술 시작 후에는 취소할 수 없습니다.</small>
                  <small>
                    결제 후 10분 안에 취소하면 하루 1회 전액 환불되지만, 시작까지 3시간 이하일 때
                    잡은 예약은 제외됩니다.
                  </small>
                </span>
              </label>
              <label className={styles.check}>
                <input type="checkbox" checked={demo} onChange={(e) => setDemo(e.target.checked)} />
                <span>
                  <strong>[필수] 테스트 결제 확인</strong>
                  <small>
                    본 서비스는 테스트 결제 환경입니다. 카드 승인과 출금이 실제로 진행되며, 결제한
                    금액은 이후 자동으로 취소됨을 확인합니다.
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
                <dd>{formatMoney(summary.appliedPrice)}</dd>
              </div>
            </dl>
            <div className="b-highlight">
              <div className="b-row">
                <strong>지금 결제할 예약금</strong>
                <h2 className="b-pink">{formatMoney(summary.deposit)}</h2>
              </div>
              <small>노쇼 방지를 위한 예약금입니다.</small>
            </div>
            <dl className="b-details">
              <div>
                <dt>매장 방문 시 지불할 잔금</dt>
                <dd>{formatMoney(summary.balance)}</dd>
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
              <Button variant="primary" wide disabled={paying} onClick={handlePay}>
                {paying ? '결제 진행 중...' : `${formatMoney(summary.deposit)} 결제 진행하기`}
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

      {blocker.state === 'blocked' && (
        // 푸는 중에 닫으면 서버는 풀렸는데 결제 화면에 남게 되므로, 나가는 중에는 ✕·Esc 로 닫지 않는다
        <Dialog title="결제 화면을 나갈까요?" onClose={() => !leaving && blocker.reset()}>
          <p>
            나가면 잡아 둔 시간을 풀어요. 바로 풀리지 않으면 5분 뒤 자동으로 풀려요. 다른 고객이
            먼저 예약하면 같은 시간을 다시 잡지 못할 수 있어요.
          </p>
          <div className={`b-flex ${styles.dialogActions}`}>
            <Button variant="primary" disabled={leaving} onClick={() => blocker.reset()}>
              계속 결제하기
            </Button>
            <Button disabled={leaving} onClick={leaveCheckout}>
              {leaving ? '나가는 중...' : '나가기'}
            </Button>
          </div>
        </Dialog>
      )}
    </>
  );
}
