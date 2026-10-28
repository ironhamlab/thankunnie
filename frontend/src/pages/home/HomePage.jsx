import { useState } from 'react';
import { generatePath, Link, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { SERVICES, money } from '@/utils/demoData';
import Asset from '@/components/common/Asset';
import Dialog from '@/components/common/Dialog';
import VacancySearch from '@/pages/home/components/VacancySearch';
import HomeBeautyMap from '@/pages/home/components/HomeBeautyMap';
import Button from '@/components/common/Button';
import '@/assets/styles/home-redesign.css';
import styles from './HomePage.module.css';

const CATEGORIES = [
  { title: '오늘 임박', text: '당일 마감 특가 할인', icon: 'bolt', badge: '최대 40%', sale: true },
  {
    title: '시술 모델 모집',
    text: '무료 & 재료비 실습 모델',
    icon: 'auto_awesome',
    badge: '재료비 지원',
    model: true,
  },
  { title: '네일', text: '젤원컬러, 이달의아트, 패디', icon: 'brush', badge: '18개 샵 오픈' },
  { title: '헤어', text: '커트, 두피스케일링, 염색', icon: 'content_cut', badge: '12개 샵 오픈' },
  { title: '속눈썹', text: '속눈썹 펌, 블랙틴팅, 연장', icon: 'visibility', badge: '9개 샵 오픈' },
  { title: '피부관리', text: '수분 진정 퀵 페이셜, 윤곽', icon: 'spa', badge: '15개 샵 오픈' },
];
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
const RECOMMENDATIONS = ['nail', 'facial', 'hair'].map((id) => SERVICES.find((s) => s.id === id));
const DEMO_REMAINING = { nail: '00:39:42', facial: '01:09:18', hair: '02:08:45' };

export default function HomePage() {
  const [params] = useSearchParams();
  const [slideIndex, setSlideIndex] = useState(0);
  const [modelOpen, setModelOpen] = useState(false);
  const [category, setCategory] = useState('전체');
  const slide = SLIDES[slideIndex];
  if (params.has('view') || params.has('sale') || params.has('search')) return <VacancySearch />;
  const results =
    category === '전체' || category === '오늘 임박'
      ? RECOMMENDATIONS
      : SERVICES.filter((s) => s.category === category);
  const chooseCategory = (item) => {
    if (item.model) {
      setModelOpen(true);
      return;
    }
    setCategory(item.title);
    document.getElementById('home-deals').scrollIntoView({ behavior: 'smooth', block: 'start' });
  };
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
              onClick={() =>
                slideIndex === 1 ? setModelOpen(true) : chooseCategory(CATEGORIES[0])
              }
            >
              ● {slide.badge}
            </button>
          </div>
          <aside className={styles.demo}>
            <b>ⓘ</b>
            <p>
              <strong>※ MVP 모의 데모:</strong> 본 화면의 데이터는 실시간 결제 없는
              시뮬레이션입니다.
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
      <section className={styles.discover} aria-label="시술 분야와 주변 빈자리">
        <div className={styles.categories}>
          <h2>어떤 시술을 찾고 계신가요?</h2>
          <div className={styles.categoryGrid}>
            {CATEGORIES.map((item) => (
              <button
                key={item.title}
                className={styles.category}
                aria-pressed={item.model ? undefined : category === item.title}
                onClick={() => chooseCategory(item)}
              >
                <span className={styles.categoryTop}>
                  <span
                    className={`${styles.categoryIcon} ${styles.symbol} ${item.sale || item.model ? styles.isFilled : ''}`}
                    aria-hidden="true"
                  >
                    {item.icon}
                  </span>
                  <small>{item.badge}</small>
                </span>
                <strong>{item.title}</strong>
                <span className={styles.categoryDescription}>{item.text}</span>
              </button>
            ))}
          </div>
        </div>
        <HomeBeautyMap services={RECOMMENDATIONS} />
      </section>
      <section id="home-deals" className="home-deals">
        <div className={styles.sectionHeading}>
          <div>
            <h2>
              {category === '전체' || category === '오늘 임박'
                ? '오늘의 실시간 타임세일 & 빈자리'
                : `${category} 예약 가능한 빈자리`}{' '}
              <em>◷</em>
            </h2>
            <p>예약 취소 및 공실 자리를 실시간 특가로 잡으세요. (결제 시 5분 예약금 임시 확보)</p>
          </div>
          <Link to={`${ROUTES.HOME}?view=list`}>전체보기 ›</Link>
        </div>
        <div className={styles.dealGrid}>
          {results.map((service) => (
            <article className={styles.dealCard} key={service.id}>
              <div className={styles.dealPhoto}>
                <Asset name={service.image} alt={`${service.shop} ${service.category} 시술`} />
                <span
                  className={`${styles.urgency} ${service.urgency !== '초임박 특가' ? styles.isSoon : ''}`}
                >
                  ● {service.urgency}
                </span>
                {DEMO_REMAINING[service.id] && (
                  <span className={styles.countdown}>
                    <span>◷ 마감 임박</span>
                    <b>{DEMO_REMAINING[service.id]}</b>
                  </span>
                )}
              </div>
              <div className={styles.dealBody}>
                <div className={styles.dealMeta}>
                  <strong>{service.shop}</strong>
                  <span>⌖ {service.location}</span>
                </div>
                <h3>{service.name}</h3>
                <p className={styles.dealTime}>
                  ⌛ {service.duration}분 소요 <span>·</span>{' '}
                  <strong>
                    {['오늘', '내일', '모레'][service.day]} {service.time}
                  </strong>
                </p>
                <div className={styles.dealPrice}>
                  <div>
                    {service.discount > 0 && <del>{money(service.original)}</del>}
                    <div>
                      {service.discount > 0 && <em>{service.discount}%</em>}{' '}
                      <b>{money(service.price)}</b>
                    </div>
                  </div>
                  <Button
                    as={Link}
                    variant="primary"
                    to={generatePath(ROUTES.SERVICE_DETAIL, { id: service.id })}
                  >
                    빈자리 확인
                  </Button>
                </div>
              </div>
            </article>
          ))}
        </div>
      </section>
      {modelOpen && (
        <Dialog title="시술 모델 모집" onClose={() => setModelOpen(false)}>
          <p>시술 모델 모집 화면은 준비 중입니다. 현재는 메인페이지 UI 시연 환경입니다.</p>
          <Button variant="primary" onClick={() => setModelOpen(false)}>
            확인
          </Button>
        </Dialog>
      )}
    </div>
  );
}
