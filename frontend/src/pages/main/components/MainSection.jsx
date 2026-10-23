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
      className="main-section"
      aria-labelledby={`main-${preset}`}
      aria-busy={section.status === 'loading'}
    >
      <header className="main-section-header">
        <h2 id={`main-${preset}`}>{definition.title}</h2>
        {exploreEnabled && <Link to={getExploreHref(buildMainParams(preset))}>더보기</Link>}
      </header>
      {(section.status === 'idle' || section.status === 'loading') && (
        <p role="status">불러오는 중입니다.</p>
      )}
      {section.status === 'failed' && (
        <div role="alert">
          <p>{section.error.message}</p>
          <button type="button" onClick={() => fetchSection(preset)}>
            다시 시도
          </button>
        </div>
      )}
      {section.status === 'succeeded' && section.data.content.length === 0 && (
        <p>{definition.emptyMessage}</p>
      )}
      {section.status === 'succeeded' && section.data.content.length > 0 && (
        <ul className="main-item-list">
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
