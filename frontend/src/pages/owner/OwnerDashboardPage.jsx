import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useDesignStore } from '@/store/useDesignStore';
import Asset from '@/components/common/Asset';
import Dialog from '@/components/common/Dialog';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import Notice from '@/components/common/Notice';
import StoreIcon from '@/components/icons/StoreIcon';
import ScissorsIcon from '@/components/icons/ScissorsIcon';
import BoltIcon from '@/components/icons/BoltIcon';
import CalendarIcon from '@/components/icons/CalendarIcon';
import styles from './OwnerDashboardPage.module.css';

const cards = [
  [
    ROUTES.OWNER_SHOP,
    '매장 정보 관리',
    '대표 사진, 영업시간, 오시는 길 주소 및 주차 안내 수정',
    StoreIcon,
  ],
  [
    ROUTES.OWNER_MENUS,
    '시술 메뉴 관리',
    '단일 시술 등록, 소요시간 설정 및 정상가 실시간 편집',
    ScissorsIcon,
  ],
  [
    ROUTES.OWNER_SLOTS,
    '빈자리 관리 & 즉시 등록',
    '당일 캔슬 즉시 타임세일 슬롯 배정 및 노출 개시',
    BoltIcon,
  ],
  [
    ROUTES.OWNER_BOOKINGS,
    '예약 관리 & 상세 내역',
    '고객 예약 확인 및 결제 상세 내역 조회',
    CalendarIcon,
  ],
];
export default function OwnerDashboardPage() {
  const shop = useDesignStore((s) => s.shop);
  const slots = useDesignStore((s) => s.slots);
  const bookings = useDesignStore((s) => s.bookings);
  const [notifications, setNotifications] = useState(true);
  const [restore, setRestore] = useState(true);
  const [newShop, setNewShop] = useState(false);
  const [notice, setNotice] = useState('');
  return (
    <div className="b-stack">
      <Notice>
        본 화면은 땡잡은 언니 MVP 사장님 통합 운영 센터입니다. 예약 데이터 및 빈자리 등록은 시연
        상태로 연결되어 있습니다.
      </Notice>
      <Panel as="section" padded className={`b-row ${styles.profile}`}>
        <div className="b-flex">
          <Asset name="32-767-0.png" alt="살롱 드 르네 대표 사진" className="b-thumb" />
          <div>
            <h2>
              {shop.name} <Badge>인증 파트너스</Badge>
            </h2>
            <p>
              {shop.address} {shop.detail}
            </p>
            <Badge>파트너스 등급: 강남권 우수 살롱</Badge>
          </div>
        </div>
        <Button as={Link} variant="primary" to={ROUTES.OWNER_SHOP}>
          매장 정보 관리로 이동 →
        </Button>
      </Panel>
      <h2>오늘의 빠른 운영 현황</h2>
      <div className="b-stat-grid">
        {[
          [
            '오늘 빈자리 등록',
            slots.filter((s) => s.date === '2025-10-24').length,
            '석',
            '15:00 / 17:00 / 19:30 잔여',
          ],
          [
            '오늘 방문 확정 예약',
            bookings.filter((b) => b.status === 'confirmed').length,
            '건',
            '예약 관리에서 상세 확인',
          ],
          ['노쇼/취소 발생률', 0, '%', '청정 매장 뱃지 유지 중'],
          ['미확인 운영 알림', 2, '건', '신규 문의 및 정산 안내'],
        ].map(([label, value, unit, note]) => (
          <Panel as="article" padded key={label}>
            <small>{label}</small>
            <h2 className="b-pink">
              {value}
              <small>{unit}</small>
            </h2>
            <small className="b-green-text">{note}</small>
          </Panel>
        ))}
      </div>
      <h2>핵심 운영 기능 바로가기</h2>
      <div className={`b-three-grid ${styles.cards}`}>
        {cards.map(([to, title, description, Icon]) => (
          <Link key={to} to={to} className="b-panel b-pad">
            <Icon size={24} />
            <span className={styles.cardArrow}>→</span>
            <h2>{title}</h2>
            <p>{description}</p>
          </Link>
        ))}
        <Panel
          as="button"
          padded
          onClick={() =>
            setNotice('주니어 모델 모집 공고는 상세 디자인 및 API 연결 후 제공됩니다.')
          }
        >
          <h2>주니어 모델 모집 공고 관리</h2>
          <p>실습생 포트폴리오 모집 등록 및 공고 관리</p>
        </Panel>
      </div>
      <Panel as="section" padded>
        <div className="b-row b-panel-heading">
          <h2>알림 수신 설정 및 자동화 운영 정책</h2>
          <Button
            size="small"
            onClick={() =>
              setNotice('데모 설정이 저장되었습니다. 실제 알림톡은 발송되지 않습니다.')
            }
          >
            설정 저장
          </Button>
        </div>
        <label className="b-toggle-row">
          <div>
            <strong>고객 신규 빈자리 예약 시 알림톡 즉시 수신</strong>
            <p>예약 완료 시 사장님께 실시간 확인 메시지를 전달합니다.</p>
          </div>
          <input
            type="checkbox"
            role="switch"
            checked={notifications}
            onChange={(e) => setNotifications(e.target.checked)}
          />
        </label>
        <label className="b-toggle-row">
          <div>
            <strong>고객 당일 긴급 취소 발생 시 즉시 빈자리 자동 복구</strong>
            <p>기존 예약이 취소된 경우 동일 시술 슬롯을 다시 공개합니다.</p>
          </div>
          <input
            type="checkbox"
            role="switch"
            checked={restore}
            onChange={(e) => setRestore(e.target.checked)}
          />
        </label>
        <div className="b-toggle-row">
          <div>
            <strong>타임세일 자동 할인 적용 구간 설정</strong>
            <p>권장 할인율을 자동 산정합니다. 시연에서는 예시만 표시됩니다.</p>
          </div>
          <Badge>적용 범위: 30% ~ 50%</Badge>
        </div>
      </Panel>
      <Panel as="section" padded>
        <div className="b-row b-panel-heading">
          <h2>입점 상태별 마이페이지 뷰 시뮬레이터</h2>
          <div className="b-options">
            <button className={!newShop ? 'is-selected' : ''} onClick={() => setNewShop(false)}>
              정상 운영 중 (현재)
            </button>
            <button className={newShop ? 'is-selected' : ''} onClick={() => setNewShop(true)}>
              신규 입점 초기 상태
            </button>
          </div>
        </div>
        <Notice>
          <strong>
            {newShop
              ? '첫 매장을 등록하고 빈자리 판매를 시작해 보세요.'
              : shop.name + '은 정상 영업 중입니다.'}
          </strong>
          <p>
            {newShop
              ? '매장 기본 정보와 시술 메뉴를 먼저 등록해 주세요.'
              : '모든 입점 서류 및 결제 정산 계좌가 승인 완료된 예시 상태입니다.'}
          </p>
          {newShop && (
            <Button as={Link} variant="primary" to={ROUTES.OWNER_SHOP}>
              매장 등록하기
            </Button>
          )}
        </Notice>
      </Panel>
      {notice && (
        <Dialog title="안내" onClose={() => setNotice('')}>
          <p>{notice}</p>
          <Button variant="primary" onClick={() => setNotice('')}>
            확인
          </Button>
        </Dialog>
      )}
    </div>
  );
}
