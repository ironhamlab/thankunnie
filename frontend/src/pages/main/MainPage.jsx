import { useEffect } from 'react';
import { buildMainParams, getKoreaDate, MAIN_PRESETS } from '../../constants/mainPresets.js';
import './MainPage.css';

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit',
});

function MainSection({ preset, useMainStore, onExplore, onSelectItem }) {
  const section = useMainStore((state) => state.sections[preset]);
  const fetchSection = useMainStore((state) => state.fetchSection);
  const definition = MAIN_PRESETS[preset];
  return (
    <section className="main-section" aria-labelledby={`main-${preset}`} aria-busy={section.status === 'loading'}>
      <header className="main-section-header">
        <h2 id={`main-${preset}`}>{definition.title}</h2>
        {onExplore && <button type="button" onClick={() => onExplore(buildMainParams(preset))}>더보기</button>}
      </header>
      {(section.status === 'idle' || section.status === 'loading') && <p role="status">불러오는 중입니다.</p>}
      {section.status === 'failed' && (
        <div role="alert">
          <p>{section.error.message}</p>
          <button type="button" onClick={() => fetchSection(preset)}>다시 시도</button>
        </div>
      )}
      {section.status === 'succeeded' && section.data.content.length === 0 && <p>{definition.emptyMessage}</p>}
      {section.status === 'succeeded' && section.data.content.length > 0 && (
        <ul className="main-item-list">
          {section.data.content.map((item) => (
            <li key={item.id}>
              <article className="main-item-card">
                <h3>{item.title}</h3>
                <p>시작 <time dateTime={item.startsAt}>{dateFormatter.format(new Date(item.startsAt))}</time></p>
                {item.applicationClosesAt && (
                  <p>신청 마감 <time dateTime={item.applicationClosesAt}>
                    {dateFormatter.format(new Date(item.applicationClosesAt))}
                  </time></p>
                )}
                {onSelectItem && <button type="button" onClick={() => onSelectItem(item.id)}>상세 보기</button>}
              </article>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** 라우터 경로와 공통 store 연결은 호스트에서 제공한다. */
export default function MainPage({ useMainStore, onExplore, onSelectItem }) {
  const fetchAll = useMainStore((state) => state.fetchAll);
  useEffect(() => {
    let observedDate = getKoreaDate();
    fetchAll();
    // 긴 비활성 탭과 절전 복귀도 처리하도록 날짜를 다시 확인한다.
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
    <main className="main-page">
      <h1>지금 살펴보기</h1>
      {Object.keys(MAIN_PRESETS).map((preset) => (
        <MainSection key={preset} preset={preset} useMainStore={useMainStore}
          onExplore={onExplore} onSelectItem={onSelectItem} />
      ))}
    </main>
  );
}
