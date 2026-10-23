import { generatePath } from 'react-router-dom';
import { ROUTES } from '../constants/routes.js';

const SEARCH_KEYS = [
  'availableOnly',
  'startDate',
  'endDate',
  'closingWithinHours',
  'sort',
  'page',
  'size',
];

export function getExploreHref(params) {
  const query = new URLSearchParams();
  for (const key of SEARCH_KEYS) {
    const value = key === 'page' ? 0 : params[key];
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value));
    }
  }
  return `${ROUTES.EXPLORE}?${query}`;
}

export function getItemDetailHref(id) {
  if (!Number.isSafeInteger(id) || id <= 0) return null;
  return generatePath(ROUTES.ITEM_DETAIL, { id });
}

export function buildSelectedSearchParams({ date = '', sort = 'START_ASC' }) {
  if (!['START_ASC', 'CLOSING_ASC'].includes(sort)) {
    throw new RangeError('지원하지 않는 정렬입니다.');
  }
  const params = { availableOnly: true, sort, page: 0, size: 6 };
  if (date) {
    const parsed = new Date(`${date}T00:00:00Z`);
    if (
      !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
      !Number.isFinite(parsed.getTime()) ||
      parsed.toISOString().slice(0, 10) !== date
    ) {
      throw new RangeError('날짜를 확인해주세요.');
    }
    params.startDate = date;
    params.endDate = date;
  }
  return params;
}
