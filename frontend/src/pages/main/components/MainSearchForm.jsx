import { useState } from 'react';
import { buildSelectedSearchParams } from '../../../utils/mainNavigation.js';

export default function MainSearchForm({ onExplore }) {
  const [date, setDate] = useState('');
  const [sort, setSort] = useState('START_ASC');
  const [error, setError] = useState('');
  const handleSubmit = (event) => {
    event.preventDefault();
    try {
      const params = buildSelectedSearchParams({ date, sort });
      setError('');
      onExplore(params);
    } catch (caught) {
      setError(caught.message);
    }
  };
  return (
    <form className="main-search-form" onSubmit={handleSubmit} aria-label="빈자리 탐색 조건">
      <label>
        시술 날짜
        <input type="date" value={date} onChange={(event) => setDate(event.target.value)} />
      </label>
      <label>
        정렬
        <select value={sort} onChange={(event) => setSort(event.target.value)}>
          <option value="START_ASC">시작 시간순</option>
          <option value="CLOSING_ASC">마감 임박순</option>
        </select>
      </label>
      <button type="submit">조건으로 탐색</button>
      {error && <p role="alert">{error}</p>}
    </form>
  );
}
