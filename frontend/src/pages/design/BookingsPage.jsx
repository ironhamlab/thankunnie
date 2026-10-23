import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { DESIGN_ROUTES as R } from '@/constants/designRoutes';
import { SERVICES, STATUS, finish, money } from './data';
import { useDesignStore } from './useDesignStore';
import Asset from './Asset';
import Dialog from './Dialog';
import getServiceShop from './getServiceShop';

export default function BookingsPage() {
  const bookings = useDesignStore((s) => s.bookings);
  const cancel = useDesignStore((s) => s.cancelBooking);
  const shop = useDesignStore((s) => s.shop);
  const location = useLocation();
  const [tab, setTab] = useState('upcoming');
  const [selectedId, setSelectedId] = useState('');
  const [showCancel, setShowCancel] = useState(false);
  const list = bookings.filter(
    (b) =>
      b.isCustomer && (tab === 'upcoming' ? b.status === 'confirmed' : b.status !== 'confirmed'),
  );
  const selected = list.find((b) => b.id === selectedId) ?? list[0];
  const service = selected && SERVICES.find((s) => s.id === selected.serviceId);
  const selectedShop = getServiceShop(service, shop);
  const [notice, setNotice] = useState('');
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
            {bookings.filter((b) => b.isCustomer && b.status === 'confirmed').length}건 진행 중
          </span>
        </h1>
        <span className="b-badge b-neutral">시연 기준일 2025.10.24</span>
      </div>
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
          지난 이용 내역
        </button>
      </div>
      <div className="b-bookings-grid">
        <div className="b-stack">
          {list.map((b) => {
            const bookingService = SERVICES.find((s) => s.id === b.serviceId);
            const bookingShop = getServiceShop(bookingService, shop);
            return (
              <button
                key={b.id}
                className={'b-panel b-booking-card ' + (selected?.id === b.id ? 'is-active' : '')}
                onClick={() => setSelectedId(b.id)}
              >
                <span className="b-badge">{STATUS[b.status]}</span>
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
                    모의 예약금: <strong className="b-pink">{money(b.deposit)}</strong>
                  </span>
                  <span>현장 잔금: {money(b.price - b.deposit)}</span>
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
              <Link to={R.HOME} className="b-button b-primary">
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
            <h3>모의 결제 및 현장 결제 잔금 내역</h3>
            <dl className="b-details">
              <div>
                <dt>총 시술 금액</dt>
                <dd>{money(selected.price)}</dd>
              </div>
              <div className="b-highlight">
                <dt>모의 결제된 예약금 (20%)</dt>
                <dd className="b-pink">{money(selected.deposit)}</dd>
              </div>
              <div>
                <dt>매장 방문 시 현장 지불 잔금</dt>
                <dd>{money(selected.price - selected.deposit)}</dd>
              </div>
            </dl>
            <h3>취소 정책 및 예상 모의 환불액 계산</h3>
            <div className="b-notice">
              <p>방문 2시간 전까지 100%, 2시간 미만 임박 취소 시 50% 모의 환불이 적용됩니다.</p>
              <p className="b-help">이 시연은 시술 40분 전 긴급 취소 구간입니다.</p>
              <div className="b-row b-highlight">
                <span>
                  {selected.status === 'cancelled' ? '모의 환불 금액' : '예상 모의 환불 금액'}
                </span>
                <strong className="b-pink">
                  {money(selected.refund ?? Math.round(selected.deposit * 0.5))}
                </strong>
              </div>
              <small>모의 환불이며 실제 금융 거래가 발생하지 않습니다.</small>
            </div>
            {selected.status === 'confirmed' && (
              <button className="b-button b-danger" onClick={() => setShowCancel(true)}>
                예약 취소 신청
              </button>
            )}
          </section>
        )}
      </div>
      {showCancel && selected && (
        <Dialog title="예약을 취소하시겠어요?" onClose={() => setShowCancel(false)}>
          <p>취소할 예약: {selected.id}</p>
          <p>
            예상 모의 환불 금액:{' '}
            <strong className="b-pink">{money(Math.round(selected.deposit * 0.5))}</strong>
          </p>
          <div className="b-flex">
            <button className="b-button" onClick={() => setShowCancel(false)}>
              예약 유지
            </button>
            <button
              className="b-button b-primary"
              onClick={() => {
                cancel(selected.id);
                setShowCancel(false);
                setTab('history');
              }}
            >
              예약 취소 확정
            </button>
          </div>
        </Dialog>
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
