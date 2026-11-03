import { create } from 'zustand';
import { notificationApi } from '@/api/notificationApi';
import { shopDetailApi } from '@/api/shopDetailApi';
import { ERROR_MESSAGES } from '@/constants/errorMessages';
import { withTermsAgreement } from '@/store/useTermsStore';
import {
  duplicateCandidate,
  findDuplicateSubscription,
  hopeTimeError,
  sameSubscriptionId,
  toUpdateBody,
} from '@/utils/notificationRules';

const toError = (error, fallback) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? fallback,
});

const ruleError = (code) => ({
  code,
  message: ERROR_MESSAGES[code],
});

// 서버에 보내기 전에 시간(003)과 같은 대상·같은 희망 시간(004)을 거른다
const rejectIfInvalid = (subscriptions, candidate, excludeId) => {
  const timeCode = hopeTimeError(candidate.startTime, candidate.endTime);
  if (timeCode) return ruleError(timeCode);
  if (findDuplicateSubscription(subscriptions, candidate, excludeId)) {
    return ruleError('NOTIFICATION_004');
  }
  return null;
};

/** 고객 빈자리 알림 조건(구독). 마이페이지 알림 조건 탭과 요약 건수가 함께 쓴다. */
export const useNotificationStore = create((set, get) => ({
  subscriptions: [],
  listStatus: 'idle',
  listError: null,
  listRequestId: 0,
  categories: [],
  categoryStatus: 'idle',
  // 업장 구독 카드용 매장명. 키는 매장 ID 문자열. 성공한 조회만 넣는다
  shopNames: {},
  // 그 매장 상세에 들어 있는 시술. 키는 매장 ID 문자열. 조회 전·실패면 키가 없다
  shopTreatments: {},
  loadingShopIds: {},
  saving: false,

  // 등록·해제보다 늦게 도착한 이전 조회가 목록을 덮지 않도록 마지막 요청의 응답만 반영한다
  loadSubscriptions: async () => {
    const requestId = get().listRequestId + 1;
    set({ listRequestId: requestId, listStatus: 'loading', listError: null });
    try {
      const subscriptions = await notificationApi.getMySubscriptions();
      if (get().listRequestId === requestId) {
        set({ subscriptions: subscriptions ?? [], listStatus: 'succeeded' });
      }
    } catch (error) {
      if (get().listRequestId !== requestId) {
        return;
      }
      set({
        subscriptions: [],
        listStatus: 'failed',
        listError: toError(error, '알림 조건을 불러오지 못했습니다.'),
      });
    }
  },

  // 카테고리는 자주 바뀌지 않으므로 한 번 받은 목록을 재사용한다
  loadCategories: async () => {
    if (get().categoryStatus === 'succeeded' || get().categoryStatus === 'loading') {
      return;
    }
    set({ categoryStatus: 'loading' });
    try {
      const categories = await notificationApi.getCategories();
      set({ categories: categories ?? [], categoryStatus: 'succeeded' });
    } catch {
      set({ categories: [], categoryStatus: 'failed' });
    }
  },

  // 아직 이름을 모르는 매장만 매장 상세 API로 조회한다. 실패하면 캐시하지 않아 다시 열 때 재시도한다
  loadShopNames: async (shopIds) => {
    const ids = [...new Set(shopIds.map((id) => Number(id)).filter((id) => id > 0))];
    for (const shopId of ids) {
      const key = String(shopId);
      if (key in get().shopNames || get().loadingShopIds[key]) continue;
      set((state) => ({ loadingShopIds: { ...state.loadingShopIds, [key]: true } }));
      try {
        const shop = await shopDetailApi.getShop(shopId);
        set((state) => ({
          shopNames: { ...state.shopNames, [key]: shop?.shopName ?? null },
          shopTreatments: { ...state.shopTreatments, [key]: shop?.treatments ?? [] },
          loadingShopIds: { ...state.loadingShopIds, [key]: false },
        }));
      } catch {
        // 실패를 성공처럼 남기면 시술 목록이 비어 저장할 수 없다. 로딩만 끈다
        set((state) => ({
          loadingShopIds: { ...state.loadingShopIds, [key]: false },
        }));
      }
    }
  },

  /** 등록 결과를 { ok, error } 로 돌려준다. 화면이 성공·실패 안내를 직접 띄운다 */
  addSubscription: async (request) => {
    const rejected = rejectIfInvalid(get().subscriptions, request);
    if (rejected) return { ok: false, error: rejected };
    set({ saving: true });
    try {
      // 문자 수신 동의가 없으면(TERMS_003) 동의 모달을 띄우고, 동의하면 한 번 다시 등록한다
      const created = await withTermsAgreement(() => notificationApi.createSubscription(request));
      // 목록을 온전히 받지 못한 상태면 새 구독만으로 성공 처리하지 않고 서버 목록을 다시 받는다
      const listLoaded = get().listStatus === 'succeeded';
      set((state) => ({
        subscriptions: [created, ...state.subscriptions],
        saving: false,
      }));
      if (!listLoaded) {
        await get().loadSubscriptions();
      }
      return { ok: true };
    } catch (error) {
      set({ saving: false });
      return { ok: false, error: toError(error, '알림 조건을 저장하지 못했습니다.') };
    }
  },

  /** 구독 유형은 유지한 채 대상과 희망 시간만 바꾼다 */
  updateSubscription: async (subscriptionId, request) => {
    const current = get().subscriptions.find((item) =>
      sameSubscriptionId(item.subscriptionId, subscriptionId),
    );
    const body = toUpdateBody(current, request);
    const rejected = rejectIfInvalid(
      get().subscriptions,
      duplicateCandidate(current, body),
      subscriptionId,
    );
    if (rejected) return { ok: false, error: rejected };
    set({ saving: true });
    try {
      // 찜에 희망 시간을 넣어 알림을 켤 때 문자 수신 동의가 없으면(TERMS_003) 동의 모달을 띄우고, 동의하면 한 번 다시 저장한다
      const updated = await withTermsAgreement(() =>
        notificationApi.updateSubscription(subscriptionId, body),
      );
      set((state) => ({
        subscriptions: state.subscriptions.map((item) =>
          sameSubscriptionId(item.subscriptionId, subscriptionId) ? updated : item,
        ),
        saving: false,
      }));
      get().reloadIfLoading();
      return { ok: true };
    } catch (error) {
      set({ saving: false });
      return { ok: false, error: toError(error, '알림 조건을 수정하지 못했습니다.') };
    }
  },

  removeSubscription: async (subscriptionId) => {
    set({ saving: true });
    try {
      await notificationApi.deleteSubscription(subscriptionId);
      set((state) => ({
        subscriptions: state.subscriptions.filter(
          (item) => !sameSubscriptionId(item.subscriptionId, subscriptionId),
        ),
        saving: false,
      }));
      get().reloadIfLoading();
      return { ok: true };
    } catch (error) {
      set({ saving: false });
      return { ok: false, error: toError(error, '알림 조건을 해제하지 못했습니다.') };
    }
  },

  // 변경 전에 보낸 조회가 아직 진행 중이면 그 응답은 버리고 서버 목록을 새로 받는다
  reloadIfLoading: () => {
    if (get().listStatus === 'loading') {
      get().loadSubscriptions();
    }
  },
}));
