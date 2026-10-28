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
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import Notice from '@/components/common/Notice';
import EmptyState from '@/components/common/EmptyState';
import Alert from '@/components/common/Alert';
import './BookingsPage.css';
import styles from './BookingsPage.module.css';

function BookingsSummary({ bookings }) {
  const payments = bookings.map(getPayment);
  const stats = [
    ['이용 예정', `${bookings.filter((b) => b.status === 'confirmed').length}건`],
    ['결제한 예약금', money(payments.reduce((sum, p) => sum + p.amount, 0))],
    ['환불받은 금액', money(payments.reduce((sum, p) => sum + p.refundedAmount, 0))],
    ['환불 처리 중', `${payments.filter((p) => p.status === 'REFUND_PENDING').length}건`],
  ];
  return (
    <div className={styles.paySummary}>
      {stats.map(([label, value]) => (
        <Panel key={label}>
          <span>{label}</span>
          <strong>{value}</strong>
        </Panel>
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
      <Notice className="b-demo-disclaimer">
        본 화면은 MVP 모의 결제/모의 환불 테스트 환경이며 실제 금융 결제 및 송금이 발생하지
        않습니다.
      </Notice>
      {location.state?.confirmed && (
        <Alert tone="success" role="status">
          예약이 확정되었습니다. 모의 결제가 완료되었습니다.
        </Alert>
      )}
      <div className="b-row b-title-row">
        <h1>
          내 예약{' '}
          <Badge>{customerBookings.filter((b) => b.status === 'confirmed').length}건 진행 중</Badge>
        </h1>
        <Badge tone="neutral">시연 기준일 2025.10.24 14:50</Badge>
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
                  <Badge>{STATUS[b.status]}</Badge>
                  <span className={`b-pay-status is-${paymentStatus.tone} ${styles.isCompact}`}>
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
                <Notice className="b-row">
                  <span>
                    예약금: <strong className="b-pink">{money(payment.amount)}</strong>
                  </span>
                  {b.status === 'cancelled' ? (
                    <span>환불: {money(payment.refundedAmount)}</span>
                  ) : (
                    <span>현장 잔금: {money(b.price - b.deposit)}</span>
                  )}
                </Notice>
                <small className="b-muted">예약번호: {b.id}</small>
                <span className="b-pink">예약 상세 보기 →</span>
              </button>
            );
          })}
          {!list.length && (
            <EmptyState className="b-panel">
              <h2>예약 내역이 없어요</h2>
              <p>실시간 빈자리 타임세일을 찾아보세요.</p>
              <Button as={Link} variant="primary" to={ROUTES.HOME}>
                실시간 빈자리 탐색하기
              </Button>
            </EmptyState>
          )}
        </div>
        {selected && (
          <Panel as="section" padded>
            <div className="b-row b-panel-heading">
              <div>
                <small className="b-muted">예약번호 {selected.id}</small>
                <h2>예약 상세 내역</h2>
              </div>
              <Badge tone="success">{STATUS[selected.status]}</Badge>
            </div>
            <h3>매장 및 담당 디자이너 안내</h3>
            <Notice>
              <strong>{selectedShop.name}</strong>
              <p>{selectedShop.address}</p>
              <small>담당 디자이너: 유나 수석 디자이너 · {service?.name}</small>
              <Button size="small" onClick={() => setNotice(selectedShop.address)}>
                주소 보기
              </Button>
            </Notice>
            <h3>예약금 결제·환불 현황</h3>
            <PaymentStatusCard booking={selected} />
            <h3>
              {selected.status === 'cancelled' ? '적용된 환불 정책' : '취소 정책 및 예상 환불액'}
            </h3>
            <RefundPolicyCard booking={selected} />
            {selected.status === 'confirmed' && (
              <Button variant="danger" wide onClick={() => setShowCancel(true)}>
                예약 취소 신청
              </Button>
            )}
          </Panel>
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
