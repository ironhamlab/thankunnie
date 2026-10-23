export const MAIN_PRESETS = Object.freeze({
  TODAY: Object.freeze({ title: '오늘', emptyMessage: '오늘 신청 가능한 항목이 없습니다.' }),
  CLOSING_SOON: Object.freeze({ title: '마감 임박', emptyMessage: '24시간 이내 마감되는 항목이 없습니다.' }),
});

const koreaDateFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
});

export function getKoreaDate(now = new Date()) {
  const parts = Object.fromEntries(koreaDateFormatter.formatToParts(now)
    .filter(({ type }) => type !== 'literal').map(({ type, value }) => [type, value]));
  return `${parts.year}-${parts.month}-${parts.day}`;
}

export function buildMainParams(preset, now = new Date()) {
  const common = { availableOnly: true, page: 0, size: 6 };
  switch (preset) {
    case 'TODAY': {
      const today = getKoreaDate(now);
      return { ...common, startDate: today, endDate: today, sort: 'START_ASC' };
    }
    case 'CLOSING_SOON':
      return { ...common, closingWithinHours: 24, sort: 'CLOSING_ASC' };
    default:
      throw new RangeError(`지원하지 않는 메인 프리셋: ${preset}`);
  }
}
