import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { MAIN_ROUTE_AVAILABILITY } from '../../constants/routes.js';
import { getKoreaDate, MAIN_PRESETS } from '../../constants/mainPresets.js';
import { getExploreHref } from '../../utils/mainNavigation.js';
import sparkleIcon from '../../assets/figma/8-3-6.svg';
import MainSection from './components/MainSection.jsx';
import MainSearchForm from './components/MainSearchForm.jsx';
import '../design/design.css';
import './MainPage.css';

export default function MainPage({ useMainStore, navigationReady = MAIN_ROUTE_AVAILABILITY }) {
  const navigate = useNavigate();
  const fetchAll = useMainStore((state) => state.fetchAll);
  useEffect(() => {
    let observedDate = getKoreaDate();
    fetchAll();
    const refreshDate = () => {
      const currentDate = getKoreaDate();
      if (currentDate !== observedDate) {
        observedDate = currentDate;
        fetchAll();
      }
    };
    const timer = window.setInterval(refreshDate, 1000);
    document.addEventListener('visibilitychange', refreshDate);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', refreshDate);
    };
  }, [fetchAll]);

  return (
    <div className="beauty-app main-api-page">
      <div className="b-container b-home">
        <section className="b-hero" aria-labelledby="main-title">
          <div>
            <div className="b-eyebrow">
              <img src={sparkleIcon} alt="" aria-hidden="true" />
              REAL-TIME BEAUTY FLASH SALE
            </div>
            <h1 id="main-title">
              지금 예약 가능한 <em>뷰티 빈자리</em>를 찾아보세요
            </h1>
            <p>오늘의 빈자리부터 곧 마감되는 예약까지, 한눈에 살펴보세요.</p>
          </div>
        </section>
        <MainSearchForm
          enabled={navigationReady.EXPLORE}
          onExplore={(params) => navigate(getExploreHref(params))}
        />
        {Object.keys(MAIN_PRESETS).map((preset) => (
          <MainSection
            key={preset}
            preset={preset}
            useMainStore={useMainStore}
            exploreEnabled={navigationReady.EXPLORE}
            detailEnabled={navigationReady.ITEM_DETAIL}
          />
        ))}
      </div>
    </div>
  );
}
