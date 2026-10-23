import { buildMainParams, MAIN_PRESETS } from '../constants/mainPresets.js';

const OFFSET_DATE_TIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?(?:Z|[+-](\d{2}):(\d{2}))$/;

function isValidDateString(value) {
  if (typeof value !== 'string') return false;
  const parts = OFFSET_DATE_TIME_PATTERN.exec(value);
  if (!parts) return false;
  const [year, month, day, hour, minute, second] = parts.slice(1, 7)
    .map((part) => Number(part ?? 0));
  const offsetHour = Number(parts[7] ?? 0);
  const offsetMinute = Number(parts[8] ?? 0);
  const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const daysInMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  return month >= 1 && month <= 12
    && day >= 1 && day <= daysInMonth[month - 1]
    && hour <= 23 && minute <= 59 && second <= 59
    && offsetHour <= 23 && offsetMinute <= 59
    && Number.isFinite(Date.parse(value));
}

/** 팀 Zustand create와 itemApi를 받아 독립적인 메인 store를 만든다. */
export function createMainStore(create, itemApi) {
  let sequence = 0;
  const initialSections = Object.fromEntries(Object.keys(MAIN_PRESETS).map((key) => [key, {
    data: null, status: 'idle', error: null, requestId: 0,
  }]));

  return create((set, get) => ({
    sections: initialSections,
    fetchSection: async (preset, now = new Date()) => {
      const params = buildMainParams(preset, now);
      const requestId = ++sequence;
      const update = (patch) => set((state) => ({
        sections: { ...state.sections, [preset]: { ...state.sections[preset], ...patch } },
      }));
      update({ data: null, status: 'loading', error: null, requestId });
      try {
        const data = await itemApi.search(params);
        const validContent = data
          && Array.isArray(data.content)
          && data.content.every((item) => (
            item !== null
            && typeof item === 'object'
            && Number.isSafeInteger(item.id)
            && item.id > 0
            && typeof item.title === 'string'
            && isValidDateString(item.startsAt)
            && (item.applicationClosesAt == null || isValidDateString(item.applicationClosesAt))
          ));
        if (!validContent) {
          throw new Error('목록 응답 형식이 올바르지 않습니다.');
        }
        if (get().sections[preset].requestId === requestId) {
          update({ data, status: 'succeeded' });
        }
      } catch (error) {
        if (get().sections[preset].requestId === requestId) {
          const serverError = error?.response?.data?.error;
          update({ status: 'failed', error: {
            code: serverError?.code ?? error?.code ?? 'MAIN_LOAD_FAILED',
            message: serverError?.message ?? error?.message ?? '목록을 불러오지 못했습니다.',
          } });
        }
      }
    },
    fetchAll: (now = new Date()) => Promise.all(
      Object.keys(MAIN_PRESETS).map((preset) => get().fetchSection(preset, now)),
    ),
  }));
}
