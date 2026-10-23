import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { DESIGN_ROUTES as R } from '@/constants/designRoutes';
import { SERVICES, finish } from './data';
import { useDesignStore } from './useDesignStore';
import BookingBreakdown from './BookingBreakdown';

const scenarios = [
  ['success', '예약 성공 상태 (기본)'],
  ['failure', '결제 실패 오류 상태'],
  ['expired', '임시 확보 시간 만료 상태'],
];
export default function BookingResultPage() {
  const location = useLocation();
  const bookings = useDesignStore((s) => s.bookings);
  const lastId = useDesignStore((s) => s.lastBookingId);
  const booking =
    bookings.find((b) => b.id === (location.state?.bookingId || lastId)) ||
    bookings.find((b) => b.isCustomer);
  const [scenario, setScenario] = useState('success');
  const isActualResult = Boolean(location.state?.confirmed);
  if (!booking)
    return (
      <div className="b-empty">
        <h1>확인할 예약이 없습니다</h1>
        <Link to={R.HOME}>빈자리 탐색</Link>
      </div>
    );
  const service = SERVICES.find((s) => s.id === booking.serviceId);
  const success = scenario === 'success';
  const title = {
    success: '예약 및 모의 결제가 완료되었습니다!',
    failure: '모의 결제를 완료하지 못했습니다',
    expired: '빈자리 임시 확보 시간이 만료되었습니다',
  }[scenario];
  return (
    <div className="b-container b-page b-result-page">
      <div className="b-notice b-demo-disclaimer">
        본 화면은 MVP 모의 결제 환경입니다. 실제 신용카드 과금 및 금융 승인이 발생하지 않습니다.
      </div>
      <section className="b-panel b-pad b-row b-result-controls">
        <strong>시연용 화면 상태 컨트롤:</strong>
        <div className="b-options">
          {scenarios.map(([value, label]) => (
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
      </section>
      <section className={'b-panel b-result-card ' + (!success ? 'b-result-problem' : '')}>
        <header className="b-result-header">
          <div className="b-result-icon">{success ? '✓' : '!'}</div>
          <h1>{title}</h1>
          <p>
            {success
              ? '매장 방문 시 확정된 예약 시간 5분 전까지 도착해 주세요.'
              : '실제 과금은 발생하지 않았습니다. 다른 빈자리나 시간을 확인해 주세요.'}
          </p>
          <div className="b-flex b-result-badges">
            <span className="b-badge">
              {success
                ? '예약 상태: ' + (booking.status === 'cancelled' ? '고객 취소' : '예약 확정')
                : '시연 오류 미리보기'}
            </span>
            <span className="b-badge b-neutral">
              {success ? '결제 상태: 모의 결제 완료' : '새로운 예약은 생성되지 않습니다'}
            </span>
          </div>
          {!isActualResult && <small className="b-help">저장된 예약의 시연 예시입니다.</small>}
        </header>
        {success ? (
          <>
            <div className="b-notice b-result-reservation">
              <div className="b-row">
                <small>예약 번호</small>
                <strong>{booking.id}</strong>
              </div>
              <div className="b-two-grid">
                <div>
                  <small>예약 매장</small>
                  <h3>{service.shop}</h3>
                  <p>시술 전 매장 위치와 예약 정보를 확인해 주세요.</p>
                </div>
                <div>
                  <small>시술 상품</small>
                  <h3>{service.name}</h3>
                  <p>소요 시간 약 {booking.duration}분</p>
                </div>
                <div>
                  <small>확정 예약 일시</small>
                  <strong className="b-pink">
                    {booking.date} {booking.time} ~ {finish(booking.time, booking.duration)}
                  </strong>
                </div>
                <div>
                  <small>예약자 정보</small>
                  <strong>
                    {booking.name} ({booking.phone})
                  </strong>
                </div>
              </div>
            </div>
            <BookingBreakdown service={service} booking={booking} />
            <div className="b-notice">
              <h3>방문 및 취소 유의사항</h3>
              <p>· 타임세일 특가 예약의 원활한 운영을 위해 예약 시간 전까지 도착해 주세요.</p>
              <p>
                · 매장 사정으로 시술 일정이 변경될 수 있으므로 방문 전 예약 정보를 확인해 주세요.
              </p>
            </div>
          </>
        ) : (
          <div className="b-error">
            <h2>{scenario === 'failure' ? '결제 시뮬레이터 오류' : '5분 임시 확보 만료'}</h2>
            <p>이 컨트롤은 결과 화면의 상태만 변경합니다. 기존 확정 예약은 취소되지 않습니다.</p>
          </div>
        )}
        <div className="b-flex b-result-actions">
          <Link className="b-button b-primary" to={R.BOOKINGS}>
            내 예약 상세 보기
          </Link>
          <Link className="b-button b-danger" to={R.HOME}>
            다른 빈자리 더 찾아보기
          </Link>
        </div>
      </section>
      {success && (
        <section className="b-result-notification">
          <div className="b-row">
            <h3>카카오 알림톡 자동 발송 내역</h3>
            <small className="b-pink">● 실제 발송 없는 시연</small>
          </div>
          <div className="b-panel b-pad b-flex">
            <span className="b-talk">TALK</span>
            <div>
              <strong>[땡잡은 언니] 예약 확정 안내</strong>
              <p>
                {booking.name}님, [{service.shop}] {booking.date} {booking.time} 예약이
                확정되었습니다. 예약 번호와 매장 약도를 확인해 주세요.
              </p>
            </div>
          </div>
        </section>
      )}
    </div>
  );
}
