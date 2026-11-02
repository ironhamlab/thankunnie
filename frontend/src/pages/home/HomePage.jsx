import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useExploreStore } from '@/store/useExploreStore';
import { datePart } from '@/utils/scheduleDate';
import { shopPagePath } from '@/utils/exploreVacancy';
import VacancySearch from '@/pages/home/components/VacancySearch';
import HomeBeautyMap from '@/pages/home/components/HomeBeautyMap';
import HomeIntro from '@/pages/home/components/HomeIntro';
import ImminentCarousel from '@/pages/home/components/ImminentCarousel';
import ShopCard from '@/pages/home/components/ShopCard';
import '@/assets/styles/home-redesign.css';
import styles from './HomePage.module.css';

// 홈 분야 탭에는 가장 빨리 시작하는 매장을 4개씩 3줄까지만 보여주고 나머지는 전체보기(검색 화면)로 넘긴다
const HOME_SHOP_COUNT = 12;

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
      <HomeIntro />
      <section className={styles.discover} aria-label="오늘 임박 빈자리와 주변 빈자리 지도">
        <ImminentCarousel />
        <HomeBeautyMap />
      </section>
      <section id="home-deals" className="home-deals">
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
              {category.name} 전체보기 ›
            </Link>
          )}
        </div>
        <div className={styles.categoryTabs} role="tablist" aria-label="시술 분야">
          {categories.map((item) => (
            <button
              key={item.categoryId}
              type="button"
              role="tab"
              aria-selected={item.categoryId === category?.categoryId}
              onClick={() => setSelectedCategoryId(item.categoryId)}
            >
              {item.name}
            </button>
          ))}
        </div>
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
      <p className={styles.demoNote}>
      </p>
    </div>
  );
}
