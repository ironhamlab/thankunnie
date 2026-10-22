import { useState } from 'react';
import { generatePath, Link } from 'react-router-dom';
import { DESIGN_ROUTES as R } from '@/constants/designRoutes';
import { SERVICES, money } from './data';
import Asset from './Asset';
import MapArtwork from './MapArtwork';

const classicServices = [
  {
    ...SERVICES[0],
    shop: '아뜰리에 네일',
    name: '젤네일 아트 (2컬러)',
    location: '홍대 · 230m',
    price: 25000,
    discount: 50,
    time: '16:00',
    image: '17-2-1.png',
  },
  {
    ...SERVICES[2],
    shop: '뷰티룸 속눈썹',
    name: '속눈썹 펌',
    location: '연남동 · 520m',
    original: 60000,
    price: 30000,
    discount: 50,
    time: '17:30',
    image: '17-2-2.png',
  },
  {
    ...SERVICES[1],
    shop: '마리 헤어살롱',
    name: '여성 커트 + 클리닉',
    location: '합정동 · 1.2km',
    original: 70000,
    price: 42000,
    discount: 40,
    time: '18:00',
    image: '17-2-3.png',
  },
  {
    ...SERVICES[3],
    shop: '제이 메이크업',
    category: '메이크업',
    name: '데일리 메이크업',
    location: '합정동 · 1.2km',
    original: 80000,
    price: 48000,
    discount: 40,
    time: '19:00',
    image: '17-2-4.png',
  },
];
const themes = ['승무원 메이크업', '웨딩·본식', '면접·증명사진', '주니어 모델'];
export default function ExplorePage({ classic = false }) {
  const [category, setCategory] = useState('전체');
  const [selected, setSelected] = useState(classic ? 'lash' : 'nail');
  const [view, setView] = useState('split');
  const [zoom, setZoom] = useState(1);
  const [sort, setSort] = useState('임박순');
  const [notice, setNotice] = useState('');
  const [theme, setTheme] = useState('');
  const source = classic
    ? classicServices
    : SERVICES.slice(0, 4).map((s, i) => ({ ...s, image: '18-3548-' + (i + 4) + '.png' }));
  let list = source.filter((s) => category === '전체' || s.category === category);
  if (sort === '할인율순') list = [...list].sort((a, b) => b.discount - a.discount);
  if (sort === '최저가순') list = [...list].sort((a, b) => a.price - b.price);
  const current = list.find((s) => s.id === selected) || list[0];
  let popupImage = current?.image;
  if (classic && current?.id === 'lash') popupImage = '17-2-9.png';
  if (!classic && current?.id === 'nail') popupImage = '18-3548-8.png';
  const reservation = (service) => generatePath(R.RESERVE, { id: service.id });
  return (
    <div className={'b-container b-page b-exploration ' + (classic ? 'b-classic' : '')}>
      <div className="b-promo-grid">
        <section className="b-explore-hero">
          <div>
            <span className="b-badge">실시간 빈자리 매칭 활성화</span>
            <h1>
              {classic ? '지금, 비어있는' : '오늘 놓치면 아쉬운'}
              <br />
              <em>{classic ? '예쁜 시간' : '뷰티 타임세일'}</em>
              {classic && '을 땡잡아보세요'}
            </h1>
            <p>취소·임박 예약으로 더 합리적인 뷰티 케어</p>
            <Link className="b-button b-primary" to={R.HOME}>
              지금 빈자리 찾기
            </Link>
          </div>
          {classic && <Asset name="17-2-0.png" alt="뷰티 모델" />}
        </section>
        <div className="b-theme-cards">
          {themes.map((label, index) => (
            <button
              key={label}
              className="b-panel"
              onClick={() => {
                setTheme(label);
                setNotice(label + ' 테마의 상세 데이터는 API 연결 후 제공됩니다.');
              }}
            >
              <Asset
                name={classic ? '17-2-' + (index + 5) + '.png' : '18-3548-' + index + '.png'}
                alt={label}
              />
              <strong>{label}</strong>
              <small>
                {
                  [
                    '면접·승무원 퀵 터치 스타일링',
                    '본식·스냅 긴급 빈자리',
                    '취업 사진 & 퍼스널 컬러 맞춤',
                    '재료비만 내고 받는 프리미엄',
                  ][index]
                }
              </small>
              <span>더보기 →</span>
            </button>
          ))}
        </div>
      </div>
      {theme && (
        <p className="b-notice" role="status">
          {notice}{' '}
          <button
            className="b-button b-small"
            onClick={() => {
              setTheme('');
              setNotice('');
            }}
          >
            닫기
          </button>
        </p>
      )}
      <div className="b-options b-category-tabs">
        {['전체', '네일', '헤어', '속눈썹', '왁싱', '메이크업', '피부관리'].map((c) => (
          <button
            key={c}
            aria-pressed={category === c}
            className={category === c ? 'is-selected' : ''}
            onClick={() => setCategory(c)}
          >
            {c}
          </button>
        ))}
      </div>
      <div className="b-row b-panel b-pad">
        <div>
          <strong>{classic ? '서울 마포구' : '강남/서초'}</strong> · 오늘 10.24(목) · 예시{' '}
          {list.length}건
        </div>
        <div className="b-options">
          {[
            ['split', '분할뷰'],
            ['map', '지도전용'],
            ['list', '목록전용'],
          ].map(([v, label]) => (
            <button key={v} className={view === v ? 'is-selected' : ''} onClick={() => setView(v)}>
              {label}
            </button>
          ))}
          <Link className="b-button b-small" to={classic ? R.EXPLORE : R.EXPLORE_CLASSIC}>
            다른 탐색 시안 보기
          </Link>
        </div>
      </div>
      <div className={'b-explore-grid b-explore-' + view}>
        {view !== 'map' && (
          <section className="b-explore-results">
            <div className="b-row b-panel-heading">
              <h2>
                지금 바로 가능한 임박 예약 <span className="b-badge">{list.length}건</span>
              </h2>
              <select aria-label="정렬" value={sort} onChange={(e) => setSort(e.target.value)}>
                {['임박순', '할인율순', '최저가순'].map((s) => (
                  <option key={s}>{s}</option>
                ))}
              </select>
            </div>
            <div className={'b-explore-cards ' + (classic ? 'b-classic-cards' : '')}>
              {list.map((s) => (
                <article
                  key={s.id}
                  className={'b-panel ' + (current?.id === s.id ? 'is-active' : '')}
                >
                  <button className="b-explore-select" onClick={() => setSelected(s.id)}>
                    <Asset name={s.image} alt={s.shop + ' 시술'} />
                    <span>
                      <small className="b-pink">{s.urgency}</small>
                      <h3>{s.shop}</h3>
                      <p>{s.name}</p>
                      <small>{s.location}</small>
                    </span>
                  </button>
                  <div className="b-row b-explore-price">
                    <div>
                      <del>{money(s.original)}</del>
                      <strong>
                        <em>{s.discount}% </em>
                        {money(s.price)}
                      </strong>
                    </div>
                    <Link className="b-button b-primary b-small" to={reservation(s)}>
                      예약하기
                    </Link>
                  </div>
                </article>
              ))}
            </div>
            {!list.length && <p className="b-empty">해당 시술의 빈자리가 없습니다.</p>}
            <div className="b-highlight b-help">
              실시간 타임세일 자동 확정 시스템 · 이 화면은 MVP 시연 데이터입니다.
            </div>
          </section>
        )}
        {view !== 'list' && (
          <section className="b-map" aria-label="Figma 예시 지도">
            <div className="b-map-canvas" style={{ transform: 'scale(' + zoom + ')' }}>
              <MapArtwork classic={classic} />
              {list.map((s, i) => (
                <button
                  key={s.id}
                  className={'b-map-pin b-pin-' + i + (current?.id === s.id ? ' is-selected' : '')}
                  onClick={() => setSelected(s.id)}
                >
                  {money(s.price)}
                </button>
              ))}
            </div>
            <div className="b-map-tools">
              <button aria-label="지도 확대" onClick={() => setZoom(Math.min(1.8, zoom + 0.2))}>
                +
              </button>
              <button aria-label="지도 축소" onClick={() => setZoom(Math.max(1, zoom - 0.2))}>
                −
              </button>
            </div>
            <button
              className="b-button b-map-refresh"
              onClick={() =>
                setNotice(
                  '고정 시뮬레이션 지도입니다. 실제 위치 검색은 지도 API 연결 후 제공됩니다.',
                )
              }
            >
              이 지역에서 재검색
            </button>
            {current && (
              <div className="b-panel b-map-detail">
                <div className="b-flex">
                  <Asset className="b-map-thumb" name={popupImage} alt="지도 선택 매장 이미지" />
                  <div>
                    <small className="b-pink">예시 1석 · {current.urgency}</small>
                    <h2>{current.shop}</h2>
                    <p>
                      {current.location} · 오늘 {current.time}
                    </p>
                  </div>
                </div>
                <div className="b-row">
                  <strong className="b-pink">{money(current.price)}</strong>
                  <Link className="b-button b-primary b-small" to={reservation(current)}>
                    상세보기
                  </Link>
                </div>
              </div>
            )}
          </section>
        )}
      </div>
      <p className="b-help">
        실제 예약을 진행할 때는 연결된 원본 매장과 시술 정보가 표시됩니다. 지도 및 대체 시안은 Figma
        예시입니다.
      </p>
      {notice && !theme && (
        <p className="b-notice" role="status">
          {notice}
        </p>
      )}
    </div>
  );
}
