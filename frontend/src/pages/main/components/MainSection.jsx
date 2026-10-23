import { Link } from 'react-router-dom';
import { buildMainParams, MAIN_PRESETS } from '../../../constants/mainPresets.js';
import { getExploreHref } from '../../../utils/mainNavigation.js';
import MainItemCard from './MainItemCard.jsx';

export default function MainSection({
  preset,
  useMainStore,
  exploreEnabled = false,
  detailEnabled = false,
}) {
  const section = useMainStore((state) => state.sections[preset]);
  const fetchSection = useMainStore((state) => state.fetchSection);
  const definition = MAIN_PRESETS[preset];
  return (
    <section
      className="main-api-section"
      aria-labelledby={`main-${preset}`}
      aria-busy={section.status === 'loading'}
    >
      <header className="b-row b-results-heading">
        <div>
          <h2 id={`main-${preset}`}>
            {definition.title}
            {section.status === 'succeeded' && (
              <span className="b-badge">{section.data.content.length}개</span>
            )}
          </h2>
          <p className="main-api-section-description">
            {preset === 'TODAY'
              ? '오늘 시작하는 예약 가능한 빈자리예요.'
              : '24시간 이내 예약이 마감되는 빈자리예요.'}
          </p>
        </div>
        {exploreEnabled && (
          <Link className="b-button b-small b-soft" to={getExploreHref(buildMainParams(preset))}>
            더보기
          </Link>
        )}
      </header>
      {(section.status === 'idle' || section.status === 'loading') && (
        <>
          <p className="main-api-loading-text" role="status">
            불러오는 중입니다.
          </p>
          <div className="b-card-grid" aria-hidden="true">
            {[1, 2, 3].map((id) => (
              <div key={id} className="b-skeleton" />
            ))}
          </div>
        </>
      )}
      {section.status === 'failed' && (
        <div className="b-empty b-panel" role="alert">
          <h3>빈자리를 불러오지 못했어요</h3>
          <p>{section.error.message}</p>
          <button className="b-button b-primary" type="button" onClick={() => fetchSection(preset)}>
            다시 시도
          </button>
        </div>
      )}
      {section.status === 'succeeded' && section.data.content.length === 0 && (
        <div className="b-empty b-panel">
          <h3>아직 빈자리가 없어요</h3>
          <p>{definition.emptyMessage}</p>
        </div>
      )}
      {section.status === 'succeeded' && section.data.content.length > 0 && (
        <ul className="b-card-grid main-api-item-list">
          {section.data.content.map((item) => (
            <li key={item.id}>
              <MainItemCard item={item} detailEnabled={detailEnabled} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
