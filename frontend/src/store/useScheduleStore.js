import { create } from 'zustand';
import { scheduleApi } from '@/api/scheduleApi';
import { addDays, todayKst } from '@/utils/scheduleDate';

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
  availableTimes: [],
  timesStatus: 'idle',
  timesRequestId: 0,
  mutationStatus: 'idle',
  mutationError: null,
  refreshFailed: false,

  loadSchedule: async () => {
    set({ status: 'loading', error: null, mutationError: null });
    try {
      // 매장 ID·역할은 인증 공통 API에서 받아 사용한다.
      const user = await scheduleApi.getCurrentUser();
      if (user?.role !== 'OWNER' || !user.shopId) {
        throw { code: 'COMMON_403', message: '사장님 계정으로 로그인해 주세요.' };
      }
      const { from, to } = exceptionRange();
      const [businessHours, exceptions] = await Promise.all([
        scheduleApi.getBusinessHours(),
        scheduleApi.getExceptions(from, to),
      ]);
      set({
        shopId: user.shopId,
        businessHours,
        exceptions,
        status: 'succeeded',
        refreshFailed: false,
      });
    } catch (error) {
      set({ status: 'failed', error: toError(error) });
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

  saveBusinessHours: async (days) => {
    return get().runMutation(async () => {
      const businessHours = await scheduleApi.updateBusinessHours(days);
      set({ businessHours });
    });
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
  reloadExceptions: async () => {
    const { from, to } = exceptionRange();
    try {
      const exceptions = await scheduleApi.getExceptions(from, to);
      set({ exceptions, refreshFailed: false });
    } catch {
      set({ refreshFailed: true });
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
