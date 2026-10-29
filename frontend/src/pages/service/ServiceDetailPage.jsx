import { useState } from 'react';
import { generatePath, Link, useParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { SERVICES, finish, money } from '@/utils/demoData';
import { addDays, todayKst } from '@/utils/scheduleDate';
import { useDesignStore } from '@/store/useDesignStore';
import useReserve from '@/hooks/useReserve';
import Asset from '@/components/common/Asset';
import Dialog from '@/components/common/Dialog';
import getServiceShop from '@/utils/getServiceShop';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import Notice from '@/components/common/Notice';
import EmptyState from '@/components/common/EmptyState';
import Alert from '@/components/common/Alert';
import styles from './ServiceDetailPage.module.css';

export default function ServiceDetailPage() {
  const { id } = useParams();
  const service = SERVICES.find((item) => item.id === id);
  const shop = useDesignStore((s) => s.shop);
  const favorites = useDesignStore((s) => s.favorites);
  const toggleFavorite = useDesignStore((s) => s.toggleFavorite);
  const { reserve, loading, error } = useReserve();
  const [address, setAddress] = useState(false);
  if (!service)
    return (
      <EmptyState>
        <h1>시술을 찾을 수 없습니다</h1>
        <Link to={ROUTES.HOME}>빈자리 목록으로</Link>
      </EmptyState>
    );
  // 시연용 빈자리(오늘·내일·모레)를 실제 날짜로 바꿔 서버에 예약한다
  const date = addDays(todayKst(), service.day);
  const ownShop = service.id === 'nail';
  const { name, address: shopAddress } = getServiceShop(service, shop);
  return (
    <div className={`b-container b-page ${styles.root}`}>
      <Notice className="b-demo-disclaimer">
        본 화면은 MVP 모의 결제 환경이며 실제 금융 결제가 발생하지 않습니다.
      </Notice>
      <nav aria-label="현재 위치" className={styles.breadcrumb}>
        <Link to={ROUTES.HOME}>⌂ 홈</Link>
        <span>›</span>
        <span>{service.category}</span>
        <span>›</span>
        <strong>
          {name} · {service.name}
        </strong>
      </nav>
      <div className="b-columns">
        <div className={`b-stack ${styles.content}`}>
          <section className={styles.photo}>
            <Asset
              name={ownShop ? '12-2061-0.png' : service.image}
              alt={name + ' 매장 대표 사진'}
            />
            <span className="b-urgency b-sale">
              <i />
              {service.urgency}
            </span>
            <span className={styles.location}>{service.location}</span>
            <button
              className={styles.favorite}
              aria-label={favorites.includes(id) ? '찜 해제' : '매장 찜하기'}
              aria-pressed={favorites.includes(id)}
              onClick={() => toggleFavorite(id)}
            >
              {favorites.includes(id) ? '♥' : '♡'}
            </button>
          </section>
          <Panel as="section" padded>
            <div className="b-row b-panel-heading">
              <div>
                <h1>
                  {name} <Badge tone="success">영업중</Badge>
                </h1>
                <p className="b-pink b-help">{shopAddress}</p>
              </div>
              <Button size="small" aria-label="매장 주소 보기" onClick={() => setAddress(true)}>
                지도
              </Button>
            </div>
            <small className="b-muted">매장 소개</small>
            <Notice className={styles.intro}>
              {ownShop
                ? shop.description
                : service.shop +
                  '에서 편안한 분위기의 맞춤 뷰티 케어를 경험하세요. 예약 시간에 맞추어 방문해 주세요.'}
            </Notice>
          </Panel>
          <Panel as="section" padded>
            <div className="b-row b-panel-heading">
              <h2>♧ 시술 정보</h2>
              <Badge>소요 시간 {service.duration}분</Badge>
            </div>
            <Panel padded>
              <h3>{service.name}</h3>
              <p>
                {service.description ||
                  '맞춤 케어 및 상담을 포함한 시술입니다. 자세한 시술 범위는 방문 전 매장에 확인해 주세요.'}
              </p>
              <div className={`b-flex ${styles.benefits}`}>
                <span>✓ 오프라인 결제</span>
                <span>✓ 맞춤 상담 제공</span>
                <span>✓ 프리미엄 케어</span>
              </div>
            </Panel>
          </Panel>
          <Panel as="section" padded className={styles.slot}>
            <div className="b-row b-panel-heading">
              <h3>선택한 빈자리 슬롯</h3>
              <Badge>노쇼 방지 예약금 20%</Badge>
            </div>
            <Notice className="b-row">
              <div>
                <h2>
                  {date}{' '}
                  <em>
                    {service.time} ~ {finish(service.time, service.duration)}
                  </em>
                </h2>
                <small>방문 2시간 전까지 전액 모의 환불 · 시술 시작 후 취소 불가</small>
              </div>
              <Badge>잔여 수량: 1자리</Badge>
            </Notice>
          </Panel>
          <Notice as="section" className={styles.policies}>
            <h3>취소·환불 규정 및 예약 유의사항</h3>
            <p>① 할인율, 예약금 비율, 임시 확보 시간은 MVP 시연 정책입니다.</p>
            <p>② 시술 시작 2시간 전까지 취소 시 전액, 임박 취소 시 50% 모의 환불됩니다.</p>
            <p>③ 실제 카드 청구·환불은 발생하지 않습니다. 예약 시간보다 5분 먼저 도착해 주세요.</p>
          </Notice>
        </div>
        <aside className={`b-stack ${styles.aside}`}>
          <div className="b-hold-bar b-row">
            <strong>빈자리 실시간 선점 대기</strong>
            <span>◷ 05:00</span>
          </div>
          <Panel as="section" padded className="b-summary">
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
            <Notice>
              <div className="b-row">
                <strong>지금 모의 결제할 예약금 (20%)</strong>
                <strong className="b-pink">{money(service.price * 0.2)}</strong>
              </div>
              <div className="b-row">
                <small>매장 방문 현장 잔금</small>
                <strong>{money(service.price * 0.8)}</strong>
              </div>
              <p className="b-help">예약금은 노쇼 방지용이며 방문 시 총 시술금액에서 차감됩니다.</p>
            </Notice>
            {error && (
              <Alert tone="error" role="alert">
                {error}
                <p>아래 날짜·시간 선택에서 다른 일정을 선택해 주세요.</p>
              </Alert>
            )}
            {!service.treatmentId && (
              <Notice>시연용 시술이라 실제 예약을 요청할 수 없습니다.</Notice>
            )}
            <Button
              variant="primary"
              wide
              disabled={loading || !service.treatmentId}
              onClick={() => reserve(service.treatmentId, date, service.time)}
            >
              {loading ? '예약 확인 중…' : `⚡ 이 빈자리 예약하기 (${money(service.price)})`}
            </Button>
            <Button as={Link} wide to={generatePath(ROUTES.RESERVE, { id })}>
              원하는 날짜·시간 선택
            </Button>
            <Link className="b-help b-pink b-center" to={ROUTES.MY_RESERVATIONS}>
              내 예약 상세 확인 →
            </Link>
          </Panel>
          <Notice>
            <strong>땡잡은 언니 안심 예약 케어</strong>
            <p>매장 사정 취소 시 100% 모의 환불 및 안내를 제공합니다.</p>
          </Notice>
          <p className="b-help">5분 임시 확보 시간은 예약 버튼을 누른 뒤 시작됩니다.</p>
        </aside>
      </div>
      {address && (
        <Dialog title={name + ' 위치'} onClose={() => setAddress(false)}>
          <p>{shopAddress}</p>
          <Button as={Link} variant="primary" to={`${ROUTES.HOME}?view=map`}>
            예시 지도에서 보기
          </Button>
        </Dialog>
      )}
    </div>
  );
}
