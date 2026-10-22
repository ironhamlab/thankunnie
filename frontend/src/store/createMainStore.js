import { buildMainParams, MAIN_PRESETS } from '../constants/mainPresets.js';

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
        if (!data || !Array.isArray(data.content)) {
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
