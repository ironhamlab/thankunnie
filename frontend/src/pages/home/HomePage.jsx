import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useExploreStore } from '@/store/useExploreStore';
import { datePart } from '@/utils/scheduleDate';
import { shopPagePath } from '@/utils/exploreVacancy';
import CategoryTabs from '@/pages/home/components/CategoryTabs';
import VacancySearch from '@/pages/home/components/VacancySearch';
import HomeBeautyMap from '@/pages/home/components/HomeBeautyMap';
import HomeIntro from '@/pages/home/components/HomeIntro';
import ImminentCarousel from '@/pages/home/components/ImminentCarousel';
import ShopCard from '@/pages/home/components/ShopCard';
import '@/assets/styles/home-redesign.css';
import styles from './HomePage.module.css';

// 홈 분야 탭에는 가장 빨리 시작하는 매장을 4개씩 3줄까지만 보여주고 나머지는 전체보기(검색 화면)로 넘긴다
const HOME_SHOP_COUNT = 12;
// 지도를 이만큼(ms) 만지지 않으면 멈췄던 캐러셀을 다시 넘긴다
const MAP_IDLE_MS = 3000;
// 휴대폰은 지도를 손가락으로 끌고 확대하느라 손을 뗐다 대는 틈이 길어 더 기다린다
const MOBILE_MAP_IDLE_MS = 5000;
const mapIdleDelay = () =>
  window.matchMedia?.('(max-width: 767px)').matches ? MOBILE_MAP_IDLE_MS : MAP_IDLE_MS;

// 검색 화면은 하루씩 보므로, 홈에서 보던 매장의 가장 빠른 빈자리 날짜로 넘겨야 같은 빈자리가 이어서 보인다
function categoryListPath(categoryId, shop) {
  const query = new URLSearchParams({ view: 'list', category: categoryId });
  if (shop) query.set('date', datePart(shop.earliestStartsAt));
  return `${ROUTES.HOME}?${query}`;
}

export default function HomePage() {
  const [params] = useSearchParams();
  const categories = useExploreStore((s) => s.categories);
  const homeShops = useExploreStore((s) => s.homeShops);
  const homeShopsStatus = useExploreStore((s) => s.homeShopsStatus);
  const loadCategories = useExploreStore((s) => s.loadCategories);
  const loadHomeShops = useExploreStore((s) => s.loadHomeShops);
  const [selectedCategoryId, setSelectedCategoryId] = useState(null);
  // 캐러셀에 지금 보이는 임박 빈자리. 지도가 그 매장으로 옮겨 간다
  const [imminentFocus, setImminentFocus] = useState(null);
  // 지도에서 누른 핀의 매장. 새 객체로 넣어 같은 핀을 다시 눌러도 캐러셀이 그 카드로 간다
  const [carouselRequest, setCarouselRequest] = useState(null);
  // 지도에 마우스를 올렸거나 지도를 직접 만졌으면 캐러셀이 넘어가 지도를 옮기지 않게 멈춘다.
  // 만진 뒤 MAP_IDLE_MS(휴대폰은 MOBILE_MAP_IDLE_MS) 동안 더 만지지 않으면 바로 다음 장으로 넘기고, 캐러셀을 직접 넘겨도 다시 넘긴다
  const [mapHovered, setMapHovered] = useState(false);
  const [mapExplored, setMapExplored] = useState(false);
  const [advanceSignal, setAdvanceSignal] = useState(0);
  // 내 위치로 옮긴 지도. 캐러셀을 직접 넘기기 전까지 저절로 넘어가는 카드가 지도를 옮기지 않는다
  const [mapAtMyLocation, setMapAtMyLocation] = useState(false);
  const exploreTimerRef = useRef(null);
  useEffect(() => () => window.clearTimeout(exploreTimerRef.current), []);
  // 끄는 동안 계속 불리므로, 이미 멈춘 상태면 다시 그리지 않고 타이머만 새로 건다
  const exploreMap = () => {
    setMapExplored(true);
    window.clearTimeout(exploreTimerRef.current);
    exploreTimerRef.current = window.setTimeout(() => {
      setMapExplored(false);
      setAdvanceSignal((value) => value + 1);
    }, mapIdleDelay());
  };
  const showImminent = (card, { manual = false } = {}) => {
    if (manual || !mapAtMyLocation) setImminentFocus(card);
    if (manual) {
      window.clearTimeout(exploreTimerRef.current);
      setMapExplored(false);
      setMapAtMyLocation(false);
    }
  };
  const searchMode = params.has('view') || params.has('sale') || params.has('search');
  // 고르기 전에는 첫 번째 분야를 보여준다
  const category =
    categories.find((item) => item.categoryId === selectedCategoryId) ?? categories[0];

  useEffect(() => {
    if (!searchMode) loadCategories();
  }, [searchMode, loadCategories]);

  useEffect(() => {
    if (!searchMode && category) {
      loadHomeShops({ categoryId: category.categoryId });
    }
  }, [searchMode, category, loadHomeShops]);

  if (searchMode) return <VacancySearch />;
  return (
    <div className="b-container home-redesign">
      <div className={styles.riseIn}>
        <HomeIntro />
      </div>
      <section
        className={`${styles.discover} ${styles.fadeIn}`}
        aria-label="오늘 임박 빈자리와 주변 빈자리 지도"
      >
        <ImminentCarousel
          onShowShop={showImminent}
          paused={mapHovered || mapExplored}
          advanceSignal={advanceSignal}
          showRequest={carouselRequest}
        />
        <HomeBeautyMap
          focusCard={imminentFocus}
          onHover={setMapHovered}
          onExplore={exploreMap}
          onPickShop={(shopId) => setCarouselRequest({ shopId })}
          onLocated={() => setMapAtMyLocation(true)}
        />
      </section>
      <section id="home-deals" className={`home-deals ${styles.riseIn} ${styles.later}`}>
        <div className={styles.sectionHeading}>
          <div>
            <h2>
              {category ? `${category.name} 빈자리 있는 매장` : '분야별 빈자리 있는 매장'}{' '}
              <em>◷</em>
            </h2>
            <p>오늘부터 7일 안에 빈자리가 있는 매장을 빨리 시작하는 순으로 보여드려요.</p>
          </div>
          {category && (
            <Link to={categoryListPath(category.categoryId, homeShops[0])}>
              {category.name} 전체보기 <span className={styles.arrow}>›</span>
            </Link>
          )}
        </div>
        <CategoryTabs
          categories={categories}
          selectedId={category?.categoryId}
          onSelect={setSelectedCategoryId}
        />
        {homeShopsStatus === 'failed' && (
          <p className="b-muted" role="alert">
            빈자리를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
          </p>
        )}
        {homeShopsStatus === 'succeeded' && homeShops.length === 0 && (
          <p className="b-muted">지금 빈자리가 있는 매장이 없어요. 다른 분야를 골라 보세요.</p>
        )}
        <div className={styles.shopGrid}>
          {homeShops.slice(0, HOME_SHOP_COUNT).map((shop) => (
            <ShopCard
              key={shop.shopId}
              shop={shop}
              to={shopPagePath(shop.shopId, { date: datePart(shop.earliestStartsAt) })}
            />
          ))}
        </div>
      </section>
      <p className={styles.demoNote}></p>
    </div>
  );
}
