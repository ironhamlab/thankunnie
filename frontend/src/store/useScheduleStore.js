import { create } from 'zustand';
import { scheduleApi } from '@/api/scheduleApi';
import { addDays, todayKst } from '@/utils/scheduleDate';
import { onSessionChange } from '@/utils/sessionChange';

// 열기·닫기는 오늘부터 60일 안에서만 등록할 수 있어 같은 범위를 불러온다.
const BOOKING_MAX_DAYS = 60;

const toError = (error) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? '영업시간·일정 요청에 실패했습니다.',
});

const exceptionRange = () => {
  const from = todayKst();
  return { from, to: addDays(from, BOOKING_MAX_DAYS - 1) };
};

export const useScheduleStore = create((set, get) => ({
  shopId: null,
  businessHours: [],
  exceptions: [],
  status: 'idle',
  error: null,
  loadRequestId: 0,
  availableTimes: [],
  timesStatus: 'idle',
  timesRequestId: 0,
  mutationStatus: 'idle',
  mutationError: null,
  refreshFailed: false,
  isRefreshing: false,

  // 마지막 요청의 응답만 반영해 늦게 온 이전 계정의 일정이 덮어쓰지 않게 한다
  loadSchedule: async () => {
    const requestId = get().loadRequestId + 1;
    const isCurrent = () => get().loadRequestId === requestId;
    set({ loadRequestId: requestId, status: 'loading', error: null, mutationError: null });
    try {
      // 매장 ID·역할은 인증 공통 API에서 받아 사용한다.
      const user = await scheduleApi.getCurrentUser();
      if (!isCurrent()) return;
      if (user?.role !== 'OWNER' || !user.shopId) {
        throw { code: 'COMMON_403', message: '매장 계정으로 로그인해 주세요.' };
      }
      const { from, to } = exceptionRange();
      const [businessHours, exceptions] = await Promise.all([
        scheduleApi.getBusinessHours(),
        scheduleApi.getExceptions(from, to),
      ]);
      if (!isCurrent()) return;
      set({
        shopId: user.shopId,
        businessHours,
        exceptions,
        status: 'succeeded',
        refreshFailed: false,
      });
    } catch (error) {
      if (isCurrent()) set({ status: 'failed', error: toError(error) });
    }
  },

  loadAvailableTimes: async (date) => {
    // 날짜를 빠르게 바꾸면 요청이 겹치므로, 늦게 도착한 이전 요청의 응답은 버린다.
    const requestId = get().timesRequestId + 1;
    set({ timesRequestId: requestId, timesStatus: 'loading' });
    try {
      const result = await scheduleApi.getAvailableTimes(get().shopId, date);
      if (get().timesRequestId === requestId) {
        set({ availableTimes: result.startTimes, timesStatus: 'succeeded' });
      }
    } catch {
      if (get().timesRequestId === requestId) {
        set({ availableTimes: [], timesStatus: 'failed' });
      }
    }
  },

  // 열기·닫기 시간 선택 칸의 상태용. 일정 화면의 날짜별 가능 시간(availableTimes)과 섞이지 않게 값만 돌려준다
  fetchAvailableTimes: async (date) =>
    (await scheduleApi.getAvailableTimes(get().shopId, date)).startTimes,

  saveBusinessHours: async (days, closeOpenExceptions = false) => {
    return get().runMutation(async () => {
      const businessHours = await scheduleApi.updateBusinessHours(days, closeOpenExceptions);
      set({ businessHours });
    });
  },

  // 저장 전 확인창에만 쓰므로 store 에 두지 않고 값만 돌려준다
  fetchClosingImpact: async (days) => {
    try {
      return await scheduleApi.getClosingImpact(days);
    } catch (error) {
      throw toError(error);
    }
  },

  // 목록 새로고침은 변경 요청과 분리한다. 저장은 됐는데 목록 조회만 실패한 것을 저장 실패로 보이면
  // 사용자가 다시 눌러 같은 일정이 중복 등록될 수 있다.
  saveException: async (exceptionId, payload) => {
    await get().runMutation(async () => {
      if (exceptionId) {
        await scheduleApi.updateException(exceptionId, payload);
      } else {
        await scheduleApi.createException(payload);
      }
    });
    await get().reloadExceptions();
  },

  deleteException: async (exceptionId) => {
    await get().runMutation(() => scheduleApi.deleteException(exceptionId));
    await get().reloadExceptions();
  },

  // 실패해도 던지지 않고 refreshFailed 로 알린다. 화면은 새로고침 안내를 보여준다.
  // 그사이 일정을 새로 받았거나 계정이 바뀌었으면 이 응답은 버린다
  reloadExceptions: async () => {
    const { from, to } = exceptionRange();
    const requestId = get().loadRequestId;
    const isCurrent = () => get().loadRequestId === requestId;
    set({ isRefreshing: true });
    try {
      const exceptions = await scheduleApi.getExceptions(from, to);
      if (isCurrent()) set({ exceptions, refreshFailed: false });
    } catch {
      if (isCurrent()) set({ refreshFailed: true });
    } finally {
      set({ isRefreshing: false });
    }
  },

  // 변경 요청 공통 처리. 실패하면 오류 상태를 남기고 화면이 메시지를 보여줄 수 있게 다시 던진다.
  runMutation: async (request) => {
    set({ mutationStatus: 'loading', mutationError: null });
    try {
      await request();
      set({ mutationStatus: 'succeeded' });
    } catch (error) {
      const failure = toError(error);
      set({ mutationStatus: 'failed', mutationError: failure });
      throw failure;
    }
  },
}));

onSessionChange(() =>
  useScheduleStore.setState((state) => ({
    shopId: null,
    businessHours: [],
    exceptions: [],
    status: 'idle',
    error: null,
    availableTimes: [],
    timesStatus: 'idle',
    timesRequestId: state.timesRequestId + 1,
    loadRequestId: state.loadRequestId + 1,
    refreshFailed: false,
  })),
);
