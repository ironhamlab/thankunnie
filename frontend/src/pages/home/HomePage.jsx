import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useExploreStore } from '@/store/useExploreStore';
import { datePart } from '@/utils/scheduleDate';
import { shopPagePath } from '@/utils/exploreVacancy';
import VacancySearch from '@/pages/home/components/VacancySearch';
import HomeBeautyMap from '@/pages/home/components/HomeBeautyMap';
import ImminentCarousel from '@/pages/home/components/ImminentCarousel';
import ShopCard from '@/pages/home/components/ShopCard';
import '@/assets/styles/home-redesign.css';
import styles from './HomePage.module.css';

const SLIDES = [
  {
    tag: '실시간 타임세일',
    eyebrow: 'REAL-TIME BEAUTY FLASH SALE',
    lead: '지금 예약 가능한 ',
    accent: '뷰티 빈자리',
    end: '를 찾아보세요',
    description:
      '원하는 날짜와 지역의 빈자리를 찾고, 오늘의 할인 자리는 타임세일로 최대 40% 모아보세요.',
    badge: '오늘의 빈자리 32개 오픈',
  },
  {
    tag: '신규 혜택',
    eyebrow: 'NEW MODEL MATCHING',
    lead: '부담 없는 ',
    accent: '무료 & 재료비 실습 모델',
    end: ' 모집!',
    description: '헤어 드라이, 젤네일, 속눈썹 펌 등 뷰티샵의 포트폴리오 모델을 만나보세요.',
    badge: '재료비 지원 혜택 모아보기',
  },
  {
    tag: '긴급 캔슬석',
    eyebrow: 'FLASH CANCEL CATCH',
    lead: '놓치기 아까운 ',
    accent: '당일 취소석 즉시 알림',
    end: '',
    description: '자주 가는 강남·성수·홍대 샵의 갑작스러운 취소 빈자리를 빠르게 찾아보세요!',
    badge: '취소 빈자리 모아보기',
  },
];
// 홈 분야 탭에는 가장 빨리 시작하는 매장을 4개씩 3줄까지만 보여주고 나머지는 전체보기(검색 화면)로 넘긴다
const HOME_SHOP_COUNT = 12;
const MODEL_LIST_PATH = `${ROUTES.HOME}?view=list&type=MODEL`;

// 검색 화면은 하루씩 보므로, 홈에서 보던 매장의 가장 빠른 빈자리 날짜로 넘겨야 같은 빈자리가 이어서 보인다
function categoryListPath(categoryId, shop) {
  const query = new URLSearchParams({ view: 'list', category: categoryId });
  if (shop) query.set('date', datePart(shop.earliestStartsAt));
  return `${ROUTES.HOME}?${query}`;
}

export default function HomePage() {
  const [params] = useSearchParams();
  const [slideIndex, setSlideIndex] = useState(0);
  const navigate = useNavigate();
  const categories = useExploreStore((s) => s.categories);
  const homeShops = useExploreStore((s) => s.homeShops);
  const homeShopsStatus = useExploreStore((s) => s.homeShopsStatus);
  const loadCategories = useExploreStore((s) => s.loadCategories);
  const loadHomeShops = useExploreStore((s) => s.loadHomeShops);
  const [selectedCategoryId, setSelectedCategoryId] = useState(null);
  const searchMode = params.has('view') || params.has('sale') || params.has('search');
  const slide = SLIDES[slideIndex];
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
  const showImminent = () =>
    document.getElementById('home-imminent').scrollIntoView({ behavior: 'smooth', block: 'start' });
  return (
    <div className="b-container home-redesign">
      <section className={styles.banner} aria-label="메인 혜택 배너" aria-roledescription="캐러셀">
        <div className={styles.bannerContent} aria-live="polite">
          <div>
            <div className={styles.eyebrow}>
              <span>ϟ {slide.tag}</span>
              <small>{slide.eyebrow}</small>
            </div>
            <h1>
              {slide.lead}
              <em>{slide.accent}</em>
              {slide.end}
            </h1>
            <p>{slide.description}</p>
            <button
              className={styles.liveBadge}
              onClick={() => (slideIndex === 1 ? navigate(MODEL_LIST_PATH) : showImminent())}
            >
              ● {slide.badge}
            </button>
          </div>
          <aside className={styles.demo}>
            <b>ⓘ</b>
            <p>
              <strong>※ 테스트 결제 안내:</strong> 결제는 실제로 승인되며, 결제한 금액은 이후
              자동으로 취소됩니다.
            </p>
          </aside>
        </div>
        <div className={styles.sliderControls}>
          <div className={styles.sliderDots}>
            {SLIDES.map((item, index) => (
              <button
                key={item.tag}
                aria-label={`슬라이드 ${index + 1}`}
                aria-pressed={index === slideIndex}
                onClick={() => setSlideIndex(index)}
              />
            ))}
          </div>
          <div>
            <span>
              <b>{slideIndex + 1}</b> / 3
            </span>
            <button aria-label="이전 슬라이드" onClick={() => setSlideIndex((slideIndex + 2) % 3)}>
              ‹
            </button>
            <button aria-label="다음 슬라이드" onClick={() => setSlideIndex((slideIndex + 1) % 3)}>
              ›
            </button>
          </div>
        </div>
      </section>
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
    </div>
  );
}
