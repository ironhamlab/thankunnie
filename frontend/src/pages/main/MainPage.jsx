import { useEffect } from 'react';
import { getKoreaDate, MAIN_PRESETS } from '@/constants/mainPresets';
import MainSection from './components/MainSection';
import styles from './MainPage.module.css';

/** C API 연결용 메인. 호스트가 useMainStore를 주입해 시안 홈과 구분한다. */
export default function MainPage({ useMainStore }) {
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
    <div className={styles.root}>
      <h1>지금 예약 가능한 뷰티 빈자리</h1>
      {Object.keys(MAIN_PRESETS).map((preset) => (
        <MainSection key={preset} preset={preset} useMainStore={useMainStore} />
      ))}
    </div>
  );
}
