import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { getKoreaDate, MAIN_PRESETS } from '../../constants/mainPresets.js';
import { getExploreHref } from '../../utils/mainNavigation.js';
import MainSection from './components/MainSection.jsx';
import MainSearchForm from './components/MainSearchForm.jsx';
import './MainPage.css';

export default function MainPage({ useMainStore }) {
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
    <div className="main-page">
      <h1>지금 예약할 수 있는 빈자리</h1>
      <MainSearchForm onExplore={(params) => navigate(getExploreHref(params))} />
      {Object.keys(MAIN_PRESETS).map((preset) => (
        <MainSection key={preset} preset={preset} useMainStore={useMainStore} />
      ))}
    </div>
  );
}
