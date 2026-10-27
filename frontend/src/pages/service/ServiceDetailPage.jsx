import { useEffect, useState } from 'react';
import { generatePath, Link, useNavigate, useParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { SERVICES, finish, money, serviceDate } from '@/utils/demoData';
import { useDesignStore } from '@/store/useDesignStore';
import Asset from '@/components/common/Asset';
import Dialog from '@/components/common/Dialog';
import getServiceShop from '@/utils/getServiceShop';

export default function ServiceDetailPage() {
  const { id } = useParams();
  const service = SERVICES.find((item) => item.id === id);
  const shop = useDesignStore((s) => s.shop);
  const hold = useDesignStore((s) => s.hold);
  const createHold = useDesignStore((s) => s.createHold);
  const favorites = useDesignStore((s) => s.favorites);
  const toggleFavorite = useDesignStore((s) => s.toggleFavorite);
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [address, setAddress] = useState(false);
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  if (!service)
    return (
      <div className="b-empty">
        <h1>시술을 찾을 수 없습니다</h1>
        <Link to={ROUTES.HOME}>빈자리 목록으로</Link>
      </div>
    );
  const date = serviceDate(service);
  const ownShop = service.id === 'nail';
  const { name, address: shopAddress } = getServiceShop(service, shop);
  const remaining =
    hold?.serviceId === id ? Math.max(0, Math.ceil((hold.expiresAt - now) / 1000)) : 300;
  const reserve = () => {
    setError('');
    try {
      const reusable =
        hold?.serviceId === id &&
        hold.date === date &&
        hold.time === service.time &&
        !hold.general &&
        hold.expiresAt > Date.now();
      if (!reusable) createHold(id, date, service.time);
      navigate(ROUTES.CHECKOUT);
    } catch (err) {
      setError(err.message);
    }
  };
  return (
    <div className="b-container b-page b-detail-page">
      <div className="b-demo-disclaimer b-notice">
        본 화면은 MVP 모의 결제 환경이며 실제 금융 결제가 발생하지 않습니다.
      </div>
      <nav aria-label="현재 위치" className="b-detail-breadcrumb">
        <Link to={ROUTES.HOME}>⌂ 홈</Link>
        <span>›</span>
        <span>{service.category}</span>
        <span>›</span>
        <strong>
          {name} · {service.name}
        </strong>
      </nav>
      <div className="b-columns">
        <div className="b-stack b-detail-content">
          <section className="b-detail-photo">
            <Asset
              name={ownShop ? '12-2061-0.png' : service.image}
              alt={name + ' 매장 대표 사진'}
            />
            <span className="b-urgency b-sale">
              <i />
              {service.urgency}
            </span>
            <span className="b-detail-location">{service.location}</span>
            <button
              className="b-favorite"
              aria-label={favorites.includes(id) ? '찜 해제' : '매장 찜하기'}
              aria-pressed={favorites.includes(id)}
              onClick={() => toggleFavorite(id)}
            >
              {favorites.includes(id) ? '♥' : '♡'}
            </button>
          </section>
          <section className="b-panel b-pad">
            <div className="b-row b-panel-heading">
              <div>
                <h1>
                  {name} <span className="b-badge b-green">영업중</span>
                </h1>
                <p className="b-pink b-help">{shopAddress}</p>
              </div>
              <button
                className="b-button b-small"
                aria-label="매장 주소 보기"
                onClick={() => setAddress(true)}
              >
                지도
              </button>
            </div>
            <small className="b-muted">매장 소개</small>
            <div className="b-notice b-detail-intro">
              {ownShop
                ? shop.description
                : service.shop +
                  '에서 편안한 분위기의 맞춤 뷰티 케어를 경험하세요. 예약 시간에 맞추어 방문해 주세요.'}
            </div>
          </section>
          <section className="b-panel b-pad">
            <div className="b-row b-panel-heading">
              <h2>♧ 시술 정보</h2>
              <span className="b-badge">소요 시간 {service.duration}분</span>
            </div>
            <div className="b-panel b-pad">
              <h3>{service.name}</h3>
              <p>
                {service.description ||
                  '맞춤 케어 및 상담을 포함한 시술입니다. 자세한 시술 범위는 방문 전 매장에 확인해 주세요.'}
              </p>
              <div className="b-flex b-service-benefits">
                <span>✓ 오프라인 결제</span>
                <span>✓ 맞춤 상담 제공</span>
                <span>✓ 프리미엄 케어</span>
              </div>
            </div>
          </section>
          <section className="b-panel b-pad b-detail-slot">
            <div className="b-row b-panel-heading">
              <h3>선택한 빈자리 슬롯</h3>
              <span className="b-badge">노쇼 방지 예약금 20%</span>
            </div>
            <div className="b-row b-notice">
              <div>
                <h2>
                  {date}{' '}
                  <em>
                    {service.time} ~ {finish(service.time, service.duration)}
                  </em>
                </h2>
                <small>방문 2시간 전까지 전액 모의 환불 · 시술 시작 후 취소 불가</small>
              </div>
              <span className="b-badge">잔여 수량: 1자리</span>
            </div>
          </section>
          <section className="b-notice b-detail-policies">
            <h3>취소·환불 규정 및 예약 유의사항</h3>
            <p>① 할인율, 예약금 비율, 임시 확보 시간은 MVP 시연 정책입니다.</p>
            <p>② 시술 시작 2시간 전까지 취소 시 전액, 임박 취소 시 50% 모의 환불됩니다.</p>
            <p>③ 실제 카드 청구·환불은 발생하지 않습니다. 예약 시간보다 5분 먼저 도착해 주세요.</p>
          </section>
        </div>
        <aside className="b-stack b-detail-aside">
          <div className="b-hold-bar b-row">
            <strong>빈자리 실시간 선점 대기</strong>
            <span>
              ◷ {String(Math.floor(remaining / 60)).padStart(2, '0')}:
              {String(remaining % 60).padStart(2, '0')}
            </span>
          </div>
          <section className="b-panel b-pad b-summary">
            <div>
              <div className="b-eyebrow">FAST-PASS CHECKOUT</div>
              <h2>빈자리 예약 요약</h2>
            </div>
            <dl className="b-details">
              <div>
                <dt>정상 가격</dt>
                <dd>
                  <del>{money(service.original)}</del>
                </dd>
              </div>
              <div className="b-pink">
                <dt>타임세일 할인 ({service.discount}%)</dt>
                <dd>-{money(service.original - service.price)}</dd>
              </div>
              <div>
                <dt>최종 예약 금액</dt>
                <dd className="b-final-price">{money(service.price)}</dd>
              </div>
            </dl>
            <div className="b-notice">
              <div className="b-row">
                <strong>지금 모의 결제할 예약금 (20%)</strong>
                <strong className="b-pink">{money(service.price * 0.2)}</strong>
              </div>
              <div className="b-row">
                <small>매장 방문 현장 잔금</small>
                <strong>{money(service.price * 0.8)}</strong>
              </div>
              <p className="b-help">예약금은 노쇼 방지용이며 방문 시 총 시술금액에서 차감됩니다.</p>
            </div>
            {error && (
              <div className="b-error" role="alert">
                {error}
                <p>이미 예약된 시연 슬롯은 아래 날짜·시간 선택에서 다른 일정을 선택해 주세요.</p>
              </div>
            )}
            <button className="b-button b-primary b-wide" onClick={reserve}>
              ⚡ 이 빈자리 예약하기 ({money(service.price)})
            </button>
            <Link className="b-button b-wide" to={generatePath(ROUTES.RESERVE, { id })}>
              원하는 날짜·시간 선택
            </Link>
            <Link className="b-help b-pink b-center" to={ROUTES.BOOKINGS}>
              내 예약 상세 확인 →
            </Link>
          </section>
          <div className="b-notice">
            <strong>땡잡은 언니 안심 예약 케어</strong>
            <p>매장 사정 취소 시 100% 모의 환불 및 안내를 제공합니다.</p>
          </div>
          <p className="b-help">5분 임시 확보 시간은 예약 버튼을 누른 뒤 시작됩니다.</p>
        </aside>
      </div>
      {address && (
        <Dialog title={name + ' 위치'} onClose={() => setAddress(false)}>
          <p>{shopAddress}</p>
          <Link className="b-button b-primary" to={`${ROUTES.HOME}?view=map`}>
            예시 지도에서 보기
          </Link>
        </Dialog>
      )}
    </div>
  );
}
