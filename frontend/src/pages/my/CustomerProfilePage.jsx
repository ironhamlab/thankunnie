import { useState } from 'react';
import { generatePath, Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { DEMO_DATE, SERVICES, money } from '@/utils/demoData';
import { useDesignStore } from '@/store/useDesignStore';
import Asset from '@/components/common/Asset';
import Dialog from '@/components/common/Dialog';
import styles from './CustomerProfilePage.module.css';

const tabs = [
  '내 예약 내역',
  '주니어 모델 신청 내역',
  '찜한 매장',
  '빈자리 알림 조건 설정',
  '전체 알림함',
];
export default function CustomerProfilePage() {
  const profile = useDesignStore((s) => s.profile);
  const saveProfile = useDesignStore((s) => s.saveProfile);
  const bookings = useDesignStore((s) => s.bookings);
  const favorites = useDesignStore((s) => s.favorites);
  const toggleFavorite = useDesignStore((s) => s.toggleFavorite);
  const cancelBooking = useDesignStore((s) => s.cancelBooking);
  const [tab, setTab] = useState(tabs[0]);
  const [showBookings, setShowBookings] = useState(true);
  const [showFavorites, setShowFavorites] = useState(true);
  const [dialog, setDialog] = useState('');
  const [name, setName] = useState(profile.name);
  const [notice, setNotice] = useState('');
  const [cancelId, setCancelId] = useState('');
  const [condition, setCondition] = useState('네일 · 강남/서초 · 오후');
  const customerBookings = bookings.filter((b) => b.isCustomer);
  const upcoming = customerBookings
    .filter((b) => b.status === 'confirmed')
    .sort((a, b) => (a.date + a.time).localeCompare(b.date + b.time));
  const nearest = upcoming[0];
  const nearestService = nearest && SERVICES.find((s) => s.id === nearest.serviceId);
  const share = async () => {
    try {
      await navigator.clipboard.writeText(window.location.origin + ROUTES.HOME + '?ref=UNNIE23');
      setNotice('초대 링크를 복사했습니다.');
    } catch {
      setDialog('share');
    }
  };
  return (
    <div className={`b-container b-page ${styles.profilePage}`}>
      <div className={`b-notice ${styles.profileRibbon}`}>
        <strong>MVP 모의 결제 데모:</strong> 본 마이페이지에서 표시되는 내역은 가상 시뮬레이션
        데이터입니다.
      </div>
      <section className={`b-panel b-pad ${styles.customerSummary}`}>
        <div className="b-row">
          <div className="b-flex">
            <div className={styles.customerAvatar}>
              언니<span>★</span>
            </div>
            <div>
              <div className="b-flex">
                <h1>{profile.name}</h1>
                <span className="b-badge b-neutral">테스트 고객 계정</span>
                <span className="b-badge">VIP 퍼스트패스</span>
              </div>
              <p>
                가입일: 2025.01.15 · 최근 접속: 오늘 12:40 · <em>노쇼 이력 0회 (청정 매너)</em>
              </p>
            </div>
          </div>
          <div className="b-flex">
            <button
              className="b-button"
              onClick={() => {
                setName(profile.name);
                setDialog('profile');
              }}
            >
              프로필 수정
            </button>
            <button className={`b-button ${styles.shareButton}`} onClick={share}>
              초대코드 공유
            </button>
          </div>
        </div>
        <div className={`b-stat-grid ${styles.profileStats}`}>
          {[
            ['방문 예정 예약', upcoming.length, '건'],
            ['주니어 모델 신청', 1, '건'],
            ['찜한 뷰티샵', favorites.length, '곳'],
            ['미확인 알림', profile.alerts ? 2 : 0, '건'],
          ].map(([label, value, unit]) => (
            <div key={label}>
              <small>{label}</small>
              <strong>
                {value}
                <span>{unit}</span>
              </strong>
            </div>
          ))}
        </div>
      </section>
      {nearest && (
        <section className={`b-panel b-pad ${styles.nearest}`}>
          <div>
            <div className="b-flex">
              <span className="b-badge">
                ◷ {nearest.date === DEMO_DATE ? 'D-DAY 오늘' : nearest.date} {nearest.time}
              </span>
              <span className="b-badge b-neutral">예약 확정</span>
              <small className="b-muted">예약번호 {nearest.id}</small>
            </div>
            <h2>{nearestService.shop}</h2>
            <p>{nearestService.name} | 담당 디자이너 자동 배정</p>
            <div className={`b-flex ${styles.nearestPrices}`}>
              <span>
                모의 결제 예약금: <strong>{money(nearest.deposit)} 완료</strong>
              </span>
              <span>
                현장 결제 잔금: <strong>{money(nearest.price - nearest.deposit)} 예정</strong>
              </span>
            </div>
          </div>
          <div className="b-flex">
            <Link className="b-button b-primary" to={ROUTES.BOOKINGS}>
              예약 상세 보기
            </Link>
            <Link
              className="b-button"
              to={generatePath(ROUTES.SERVICE_DETAIL, { id: nearest.serviceId })}
            >
              길찾기
            </Link>
          </div>
        </section>
      )}
      <nav className={`b-panel ${styles.profileTabs}`} aria-label="마이페이지 메뉴">
        {tabs.map((label) => (
          <button
            key={label}
            className={tab === label ? 'is-selected' : ''}
            aria-pressed={tab === label}
            onClick={() => setTab(label)}
          >
            {label}
          </button>
        ))}
      </nav>
      <section aria-label={tab} className={styles.profileTabPanel}>
        <div className="b-row b-panel-heading">
          <h2>{tab === tabs[0] ? '예약 목록' : tab}</h2>
          <small className="b-muted">가상 데이터 시연</small>
        </div>
        {tab === tabs[0] && (
          <div className="b-stack">
            {showBookings ? (
              <>
                {customerBookings.map((b) => {
                  const s = SERVICES.find((item) => item.id === b.serviceId);
                  return (
                    <article className={`b-panel b-pad ${styles.profileBooking} b-row`} key={b.id}>
                      <div className="b-flex">
                        <Asset name="8-8-0.png" alt="예약 매장 대표 이미지" className="b-thumb" />
                        <div>
                          <small className="b-muted">
                            <span className="b-badge">
                              {b.status === 'confirmed' ? '방문 예정' : '고객 취소'}
                            </span>{' '}
                            {b.date} {b.time}
                          </small>
                          <h2>{s.shop}</h2>
                          <p>{s.name}</p>
                          <p>
                            결제 내역: 예약금 {money(b.deposit)} / 현장 결제{' '}
                            {money(b.price - b.deposit)}
                          </p>
                        </div>
                      </div>
                      <div className={`b-stack ${styles.profileBookingActions}`}>
                        <Link className="b-button b-primary" to={ROUTES.BOOKINGS}>
                          상세 영수증
                        </Link>
                        {b.status === 'confirmed' && (
                          <button className="b-button" onClick={() => setCancelId(b.id)}>
                            예약 취소
                          </button>
                        )}
                      </div>
                    </article>
                  );
                })}
                <article className={`b-panel b-pad ${styles.profileBooking} b-row`}>
                  <div className="b-flex">
                    <Asset name="8-3-2.png" alt="속눈썹 케어 시술" className="b-thumb" />
                    <div>
                      <small className="b-muted">시술 완료 · 2025.01.28 17:00</small>
                      <h2>라비에르 래쉬 신사점</h2>
                      <p>노글루 속눈썹 펌 (영양 케어 포함)</p>
                      <p>결제 총액: 24,000원 (타임세일 특가) · 과거 이용 예시</p>
                    </div>
                  </div>
                  <div className={`b-stack ${styles.profileBookingActions}`}>
                    <button className="b-button b-soft" onClick={() => setDialog('review')}>
                      리뷰 작성
                    </button>
                    <Link className="b-button" to={generatePath(ROUTES.RESERVE, { id: 'lash' })}>
                      다시 예약
                    </Link>
                  </div>
                </article>
              </>
            ) : (
              <div className="b-panel b-empty">
                <h2>예약 내역이 없어요</h2>
                <Link className="b-button b-primary" to={ROUTES.HOME}>
                  지금 빈자리 찾아보기
                </Link>
              </div>
            )}
          </div>
        )}
        {tab === tabs[1] && (
          <div className="b-panel b-pad">
            <span className="b-badge">검토 중</span>
            <h2>주니어 네일 모델 신청</h2>
            <p>살롱 드 르네 · 2025.10.25 · 재료비 시술 · 신청 내역 예시</p>
            <p className="b-help">실제 신청과 매장 연락은 API 연결 후 제공됩니다.</p>
          </div>
        )}
        {tab === tabs[2] && (
          <div className="b-stack">
            {showFavorites && favorites.length ? (
              SERVICES.filter((s) => favorites.includes(s.id)).map((s) => (
                <article className="b-panel b-pad b-row" key={s.id}>
                  <div className="b-flex">
                    <Asset name={s.image} alt={s.shop} className="b-thumb" />
                    <div>
                      <h2>{s.shop}</h2>
                      <p>{s.location}</p>
                      <Link
                        className="b-pink"
                        to={generatePath(ROUTES.SERVICE_DETAIL, { id: s.id })}
                      >
                        매장 상세 보기 →
                      </Link>
                    </div>
                  </div>
                  <button className="b-button" onClick={() => toggleFavorite(s.id)}>
                    찜 해제
                  </button>
                </article>
              ))
            ) : (
              <div className="b-panel b-empty">
                <h2>찜한 매장이 없어요</h2>
                <Link to={ROUTES.HOME}>마음에 드는 매장을 찾아보세요</Link>
              </div>
            )}
          </div>
        )}
        {tab === tabs[3] && (
          <form
            className="b-panel b-pad b-stack"
            onSubmit={(e) => {
              e.preventDefault();
              setNotice('데모 알림 조건을 저장했습니다. 실제 알림은 발송되지 않습니다.');
            }}
          >
            <label className="b-toggle-row">
              <span>빈자리 알림 수신</span>
              <input
                type="checkbox"
                role="switch"
                checked={profile.alerts}
                onChange={(e) => saveProfile({ alerts: e.target.checked })}
              />
            </label>
            <label className={styles.alertCondition}>
              원하는 시술·지역·시간
              <input
                maxLength={100}
                value={condition}
                onChange={(e) => setCondition(e.target.value)}
              />
            </label>
            <button className="b-button b-primary">알림 조건 저장</button>
          </form>
        )}
        {tab === tabs[4] && (
          <div className="b-stack">
            <div className="b-notice">
              <h3>예약 안내</h3>
              <p>방문 예정 예약은 내 예약 상세에서 확인할 수 있습니다.</p>
            </div>
            <div className="b-notice">
              <h3>빈자리 타임세일</h3>
              <p>
                찜한 매장의 새 빈자리를 탐색해 보세요. 알림 예시이며 실제 메시지는 발송되지
                않습니다.
              </p>
            </div>
          </div>
        )}
      </section>
      <div className="b-preview b-row">
        <div>
          <strong>MVP 화면 상태 시뮬레이터 컨트롤러</strong>
          <p>예약 및 찜 목록의 빈 화면을 확인할 수 있습니다.</p>
        </div>
        <div className="b-options">
          <button
            aria-pressed={!showBookings}
            onClick={() => {
              setShowBookings(!showBookings);
              setTab(tabs[0]);
            }}
          >
            예약 있음 ↔ 없음 토글
          </button>
          <button
            aria-pressed={!showFavorites}
            onClick={() => {
              setShowFavorites(!showFavorites);
              setTab(tabs[2]);
            }}
          >
            찜 목록 채움 ↔ 비움 토글
          </button>
        </div>
      </div>
      {notice && (
        <p className="b-success" role="status">
          {notice}
        </p>
      )}
      {dialog && (
        <Dialog
          title={{ profile: '프로필 수정', share: '초대코드 공유', review: '리뷰 작성' }[dialog]}
          onClose={() => setDialog('')}
        >
          {dialog === 'profile' && (
            <form
              className="b-stack"
              onSubmit={(e) => {
                e.preventDefault();
                if (!name.trim()) return;
                saveProfile({ name: name.trim() });
                setDialog('');
                setNotice('프로필을 저장했습니다.');
              }}
            >
              <label className={styles.alertCondition}>
                닉네임
                <input
                  autoFocus
                  required
                  maxLength={20}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </label>
              <button className="b-button b-primary">프로필 저장</button>
            </form>
          )}
          {dialog === 'share' && (
            <>
              <p>초대코드: UNNIE23</p>
              <p>{window.location.origin + ROUTES.HOME + '?ref=UNNIE23'}</p>
            </>
          )}
          {dialog === 'review' && (
            <form
              className="b-stack"
              onSubmit={(e) => {
                e.preventDefault();
                setDialog('');
                setNotice('리뷰를 데모로 저장했습니다. 실제 게시되지 않습니다.');
              }}
            >
              <label className={styles.alertCondition}>
                시술은 어떠셨나요?
                <textarea required rows="4" maxLength={500} />
              </label>
              <button className="b-button b-primary">리뷰 저장</button>
            </form>
          )}
        </Dialog>
      )}
      {cancelId && (
        <Dialog title="예약 취소 확인" onClose={() => setCancelId('')}>
          <p>
            이용 2일 전까지 20,000원, 1일 전 10,000원, 당일 0원이 예약금 한도 내에서 모의
            환불됩니다. 취소하시겠어요?
          </p>
          <button
            className="b-button b-primary"
            onClick={() => {
              cancelBooking(cancelId);
              setCancelId('');
              setNotice('예약이 취소되었습니다. 모의 환불 내역은 내 예약에서 확인해 주세요.');
            }}
          >
            예약 취소 확정
          </button>
        </Dialog>
      )}
    </div>
  );
}
