import { validateVacancyPage } from '../utils/vacancyResponse.js';

export function createExploreStore(create, api) {
  return create((set, get) => ({
    sections: {},
    load: async (key, params) => {
      const requestId = (get().sections[key]?.requestId ?? 0) + 1;
      const update = (patch) =>
        set((state) => ({
          sections: { ...state.sections, [key]: { ...state.sections[key], ...patch } },
        }));
      update({ requestId, status: 'loading', data: null, error: null });
      try {
        const data = validateVacancyPage(await api.search(params));
        if (get().sections[key].requestId === requestId) {
          update({ data, status: 'succeeded' });
        }
      } catch (error) {
        if (get().sections[key].requestId === requestId) {
          update({ status: 'failed', error: error?.message ?? '빈자리를 불러오지 못했습니다.' });
        }
      }
    },
  }));
}
