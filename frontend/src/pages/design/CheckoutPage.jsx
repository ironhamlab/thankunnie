import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { DESIGN_ROUTES as R } from '@/constants/designRoutes';
import { SERVICES, finish, money } from './data';
import { useDesignStore } from './useDesignStore';
import Asset from './Asset';

export default function CheckoutPage() {
  const hold = useDesignStore((s) => s.hold);
  const release = useDesignStore((s) => s.releaseHold);
  const expire = useDesignStore((s) => s.expireHold);
  const confirm = useDesignStore((s) => s.confirmBooking);
  const navigate = useNavigate();
  const [now, setNow] = useState(Date.now);
  const [request, setRequest] = useState('원장님 손톱 쉐입은 라운드로 부탁드려요.');
  const [policy, setPolicy] = useState(false);
  const [demo, setDemo] = useState(false);
  const [scenario, setScenario] = useState('success');
  const [error, setError] = useState('');
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  if (!hold)
    return (
      <div className="b-container b-empty">
        <h1>선택한 예약이 없습니다</h1>
        <p>빈자리와 시간을 먼저 선택해 주세요.</p>
        <Link to={R.HOME} className="b-button b-primary">
          빈자리 탐색
        </Link>
      </div>
    );
  const service = SERVICES.find((s) => s.id === hold.serviceId);
  const remaining = Math.max(0, Math.ceil((hold.expiresAt - now) / 1000));
  const handlePay = () => {
    setError('');
    if (!policy || !demo) {
      setError('필수 약관과 모의 결제 확인에 동의해 주세요.');
      return;
    }
    if (scenario === 'failure') {
      setError('모의 결제가 실패했습니다. 잠시 후 다시 시도해 주세요.');
      return;
    }
    if (scenario === 'expired') {
      expire();
      setError('임시 확보 시간이 만료되었습니다. 시간을 다시 선택해 주세요.');
      return;
    }
    try {
      confirm(request);
      navigate(R.BOOKINGS, { state: { confirmed: true } });
    } catch (err) {
      setError(err.message);
    }
  };
  return (
    <>
      <div className="b-hold-bar">
        <div className="b-container b-row">
          <span>
            <strong>빈자리 임시 확보 중!</strong> — 제한 시간 내에 모의 결제를 완료해 주세요.
          </span>
          <span>
            남은 시간{' '}
            <strong>
              {String(Math.floor(remaining / 60)).padStart(2, '0')}:
              {String(remaining % 60).padStart(2, '0')}
            </strong>{' '}
            <button onClick={expire}>시간 만료 테스트</button>
          </span>
        </div>
      </div>
      <div className="b-container b-page">
        <Link className="b-back" to={R.HOME} onClick={release}>
          ← 빈자리 목록으로 돌아가기
        </Link>
        <div className="b-columns">
          <div className="b-stack">
            <section className="b-panel b-pad">
              <h2 className="b-panel-heading">1. 예약 정보 확인</h2>
              <div className="b-booking-service">
                <Asset name="8-4-0.png" className="b-thumb" alt="살롱 드 르네 매장 내부" />
                <div>
                  <small className="b-pink">{service.category} & 뷰티 케어</small>
                  <h2>{service.shop}</h2>
                  <div className="b-notice">
                    <strong>{service.name}</strong>
                    <p className="b-pink">
                      {hold.date} {hold.time} ~ {finish(hold.time, hold.duration)}
                    </p>
                    <small>소요 시간 약 {hold.duration}분 · 담당 디자이너 자동 배정</small>
                  </div>
                </div>
              </div>
            </section>
            <section className="b-panel b-pad">
              <h2 className="b-panel-heading">
                2. 예약자 정보 <span className="b-badge b-neutral">테스트 계정 기본 표시</span>
              </h2>
              <div className="b-form-grid">
                <label>
                  예약자명
                  <input value="김언니 (테스트 고객)" readOnly />
                </label>
                <label>
                  휴대폰 번호
                  <input value="010-12**-56**" readOnly />
                  <small>예약 확정 및 매장 안내 알림톡 수신 번호</small>
                </label>
                <label className="b-span-two">
                  원장님께 남길 요청사항 (선택)
                  <textarea
                    rows="3"
                    maxLength={500}
                    value={request}
                    onChange={(e) => setRequest(e.target.value)}
                  />
                </label>
              </div>
            </section>
            <section className="b-panel b-pad">
              <h2 className="b-panel-heading">3. 필수 약관 및 취소 정책 동의</h2>
              <label className="b-check">
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
              <label className="b-check">
                <input type="checkbox" checked={demo} onChange={(e) => setDemo(e.target.checked)} />
                <span>
                  <strong>[필수] MVP 모의 결제 확인</strong>
                  <small>
                    본 화면은 모의 결제 환경이며 실제 금융 결제 및 대금 청구가 발생하지 않음을
                    확인합니다.
                  </small>
                </span>
              </label>
            </section>
            <section className="b-panel b-pad">
              <h2 className="b-panel-heading">4. 모의 결제 시연 패널</h2>
              <p className="b-help">다양한 테스트 시나리오를 실행해볼 수 있습니다.</p>
              <div className="b-options b-scenarios">
                {[
                  ['success', '성공 케이스 진행'],
                  ['failure', '결제 실패 오류 시연'],
                  ['expired', '임시 확보 만료 시연'],
                ].map(([value, label]) => (
                  <button
                    key={value}
                    aria-pressed={scenario === value}
                    className={scenario === value ? 'is-selected' : ''}
                    onClick={() => setScenario(value)}
                  >
                    {label}
                  </button>
                ))}
              </div>
              <div className="b-notice">
                <strong>선택된 결제 수단 · 모의 안전 결제</strong>
                <div className="b-payment-method">
                  <span>언니</span>
                  <div>
                    <strong>테스트 결제용 가상 수단 (모의 간편결제)</strong>
                    <p>실제 카드 번호, CVC, 비밀번호 입력이 필요 없습니다.</p>
                  </div>
                </div>
              </div>
            </section>
          </div>
          <aside className="b-panel b-pad b-summary">
            <div className="b-notice">
              모의 결제 진행 시 {money(hold.price)}의 예약 권리가 확정됩니다.
            </div>
            <h2>결제 금액 내역</h2>
            <dl className="b-details">
              <div>
                <dt>정상 금액</dt>
                <dd>
                  <del>{money(service.original)}</del>
                </dd>
              </div>
              <div className="b-pink">
                <dt>타임세일 할인</dt>
                <dd>-{money(service.original - hold.price)}</dd>
              </div>
              <div>
                <dt>
                  <strong>총 시술 금액</strong>
                </dt>
                <dd>{money(hold.price)}</dd>
              </div>
            </dl>
            <div className="b-highlight">
              <div className="b-row">
                <strong>지금 모의 결제할 예약금</strong>
                <h2 className="b-pink">{money(hold.deposit)}</h2>
              </div>
              <small>노쇼 방지를 위한 20% 최소 예약금입니다.</small>
            </div>
            <dl className="b-details">
              <div>
                <dt>매장 방문 시 지불할 잔금</dt>
                <dd>{money(hold.price - hold.deposit)}</dd>
              </div>
            </dl>
            {(error || remaining === 0) && (
              <p role="alert" className="b-error">
                {remaining === 0
                  ? '임시 확보 시간이 만료되었습니다. 시간을 다시 선택해 주세요.'
                  : error}
              </p>
            )}
            <button
              className="b-button b-primary b-wide"
              disabled={remaining === 0}
              onClick={handlePay}
            >
              {money(hold.deposit)} 모의 결제로 진행하기
            </button>
            <Link className="b-back b-center" to={R.HOME} onClick={release}>
              예약 취소하고 목록으로 돌아가기
            </Link>
            <p className="b-help">MVP 모의 결제 환경으로 실제 금융 결제가 발생하지 않습니다.</p>
          </aside>
        </div>
      </div>
    </>
  );
}
