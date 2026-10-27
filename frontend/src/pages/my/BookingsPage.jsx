import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { PAYMENT_STATUS, SERVICES, STATUS, finish, getPayment, money } from '@/utils/demoData';
import { useDesignStore } from '@/store/useDesignStore';
import Asset from '@/components/common/Asset';
import CancelRefundDialog from '@/pages/my/components/CancelRefundDialog';
import Dialog from '@/components/common/Dialog';
import getServiceShop from '@/utils/getServiceShop';
import PaymentStatusCard from '@/pages/my/components/PaymentStatusCard';
import RefundPolicyCard from '@/pages/my/components/RefundPolicyCard';
import './payment-cancel.css';

function BookingsSummary({ bookings }) {
  const payments = bookings.map(getPayment);
  const stats = [
    ['이용 예정', `${bookings.filter((b) => b.status === 'confirmed').length}건`],
    ['결제한 예약금', money(payments.reduce((sum, p) => sum + p.amount, 0))],
    ['환불받은 금액', money(payments.reduce((sum, p) => sum + p.refundedAmount, 0))],
    ['환불 처리 중', `${payments.filter((p) => p.status === 'REFUND_PENDING').length}건`],
  ];
  return (
    <div className="b-pay-summary">
      {stats.map(([label, value]) => (
        <div key={label} className="b-panel">
          <span>{label}</span>
          <strong>{value}</strong>
        </div>
      ))}
    </div>
  );
}

export default function BookingsPage() {
  const bookings = useDesignStore((s) => s.bookings);
  const cancel = useDesignStore((s) => s.cancelBooking);
  const shop = useDesignStore((s) => s.shop);
  const location = useLocation();
  const [tab, setTab] = useState('upcoming');
  const [selectedId, setSelectedId] = useState('');
  const [showCancel, setShowCancel] = useState(false);
  const [notice, setNotice] = useState('');
  const customerBookings = bookings.filter((b) => b.isCustomer);
  const list = customerBookings.filter((b) =>
    tab === 'upcoming' ? b.status === 'confirmed' : b.status !== 'confirmed',
  );
  const selected = list.find((b) => b.id === selectedId) ?? list[0];
  const service = selected && SERVICES.find((s) => s.id === selected.serviceId);
  const selectedShop = getServiceShop(service, shop);

  const handleCancelConfirm = (reason) => {
    cancel(selected.id, reason);
    setShowCancel(false);
    setSelectedId(selected.id);
    setTab('history');
  };

  return (
    <div className="b-container b-page">
      <div className="b-notice b-demo-disclaimer">
        본 화면은 MVP 모의 결제/모의 환불 테스트 환경이며 실제 금융 결제 및 송금이 발생하지
        않습니다.
      </div>
      {location.state?.confirmed && (
        <div className="b-success" role="status">
          예약이 확정되었습니다. 모의 결제가 완료되었습니다.
        </div>
      )}
      <div className="b-row b-title-row">
        <h1>
          내 예약{' '}
          <span className="b-badge">
            {customerBookings.filter((b) => b.status === 'confirmed').length}건 진행 중
          </span>
        </h1>
        <span className="b-badge b-neutral">시연 기준일 2025.10.24 14:50</span>
      </div>
      <BookingsSummary bookings={customerBookings} />
      <div className="b-options b-tabs">
        <button
          className={tab === 'upcoming' ? 'is-selected' : ''}
          onClick={() => setTab('upcoming')}
        >
          이용 예정 예약
        </button>
        <button
          className={tab === 'history' ? 'is-selected' : ''}
          onClick={() => setTab('history')}
        >
          취소·지난 이용 내역
        </button>
      </div>
      <div className="b-bookings-grid">
        <div className="b-stack">
          {list.map((b) => {
            const bookingService = SERVICES.find((s) => s.id === b.serviceId);
            const bookingShop = getServiceShop(bookingService, shop);
            const payment = getPayment(b);
            const paymentStatus = PAYMENT_STATUS[payment.status] ?? PAYMENT_STATUS.PAID;
            return (
              <button
                key={b.id}
                className={'b-panel b-booking-card ' + (selected?.id === b.id ? 'is-active' : '')}
                onClick={() => setSelectedId(b.id)}
              >
                <div className="b-flex">
                  <span className="b-badge">{STATUS[b.status]}</span>
                  <span className={`b-pay-status is-${paymentStatus.tone} is-compact`}>
                    {paymentStatus.label}
                  </span>
                </div>
                <div className="b-booking-service">
                  <Asset
                    name={b.serviceId === 'nail' ? '8-8-0.png' : bookingService?.image}
                    alt={bookingShop.name + ' 매장'}
                    className="b-thumb"
                  />
                  <div>
                    <small>{bookingShop.name}</small>
                    <h3>{bookingService?.name}</h3>
                    <p>
                      {b.date} {b.time} ~ {finish(b.time, b.duration)}
                    </p>
                  </div>
                </div>
                <div className="b-notice b-row">
                  <span>
                    예약금: <strong className="b-pink">{money(payment.amount)}</strong>
                  </span>
                  {b.status === 'cancelled' ? (
                    <span>환불: {money(payment.refundedAmount)}</span>
                  ) : (
                    <span>현장 잔금: {money(b.price - b.deposit)}</span>
                  )}
                </div>
                <small className="b-muted">예약번호: {b.id}</small>
                <span className="b-pink">예약 상세 보기 →</span>
              </button>
            );
          })}
          {!list.length && (
            <div className="b-panel b-empty">
              <h2>예약 내역이 없어요</h2>
              <p>실시간 빈자리 타임세일을 찾아보세요.</p>
              <Link to={ROUTES.HOME} className="b-button b-primary">
                실시간 빈자리 탐색하기
              </Link>
            </div>
          )}
        </div>
        {selected && (
          <section className="b-panel b-pad">
            <div className="b-row b-panel-heading">
              <div>
                <small className="b-muted">예약번호 {selected.id}</small>
                <h2>예약 상세 내역</h2>
              </div>
              <span className="b-badge b-green">{STATUS[selected.status]}</span>
            </div>
            <h3>매장 및 담당 디자이너 안내</h3>
            <div className="b-notice">
              <strong>{selectedShop.name}</strong>
              <p>{selectedShop.address}</p>
              <small>담당 디자이너: 유나 수석 디자이너 · {service?.name}</small>
              <button className="b-button b-small" onClick={() => setNotice(selectedShop.address)}>
                주소 보기
              </button>
            </div>
            <h3>예약금 결제·환불 현황</h3>
            <PaymentStatusCard booking={selected} />
            <h3>
              {selected.status === 'cancelled' ? '적용된 환불 정책' : '취소 정책 및 예상 환불액'}
            </h3>
            <RefundPolicyCard booking={selected} />
            {selected.status === 'confirmed' && (
              <button className="b-button b-danger b-wide" onClick={() => setShowCancel(true)}>
                예약 취소 신청
              </button>
            )}
          </section>
        )}
      </div>
      {showCancel && selected && (
        <CancelRefundDialog
          booking={selected}
          onClose={() => setShowCancel(false)}
          onConfirm={handleCancelConfirm}
        />
      )}
      {notice && (
        <Dialog title="매장 주소" onClose={() => setNotice('')}>
          <p>{notice}</p>
          <p className="b-help">시연 화면에서는 외부 지도 연결을 제공하지 않습니다.</p>
        </Dialog>
      )}
    </div>
  );
}
