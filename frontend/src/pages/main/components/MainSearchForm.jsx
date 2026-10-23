import { useState } from 'react';
import { buildSelectedSearchParams } from '../../../utils/mainNavigation.js';

export default function MainSearchForm({ onExplore, enabled = false }) {
  const [date, setDate] = useState('');
  const [sort, setSort] = useState('START_ASC');
  const [error, setError] = useState('');
  const handleSubmit = (event) => {
    event.preventDefault();
    if (!enabled) return;
    try {
      const params = buildSelectedSearchParams({ date, sort });
      setError('');
      onExplore(params);
    } catch (caught) {
      setError(caught.message);
    }
  };
  return (
    <form
      className="b-panel b-filters main-api-search"
      onSubmit={handleSubmit}
      aria-label="빈자리 탐색 조건"
    >
      <h2>원하는 빈자리를 찾아보세요</h2>
      <div className="b-row main-api-search-fields">
        <label className="b-filter-group">
          <span className="b-filter-label">시술 날짜</span>
          <input
            type="date"
            disabled={!enabled}
            value={date}
            onChange={(event) => setDate(event.target.value)}
          />
        </label>
        <label className="b-filter-group">
          <span className="b-filter-label">정렬</span>
          <select
            disabled={!enabled}
            value={sort}
            onChange={(event) => setSort(event.target.value)}
          >
            <option value="START_ASC">시작 시간순</option>
            <option value="CLOSING_ASC">마감 임박순</option>
          </select>
        </label>
        <button className="b-button b-primary" type="submit" disabled={!enabled}>
          조건으로 탐색
        </button>
      </div>
      {!enabled && (
        <p className="main-api-search-notice" role="status">
          탐색 화면 준비 중입니다. 목록은 아래에서 확인해주세요.
        </p>
      )}
      {error && (
        <p className="b-error" role="alert">
          {error}
        </p>
      )}
    </form>
  );
}
