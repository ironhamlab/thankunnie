import { create } from 'zustand';
import { shopApi } from '@/api/shopApi';
import { onSessionChange } from '@/utils/sessionChange';

const failure = (error) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? '매장 요청에 실패했습니다.',
});

export const useShopStore = create((set, get) => ({
  shop: null,
  status: 'idle',
  error: null,
  mutationStatus: 'idle',
  loadRequestId: 0,
  // 매장 센터 레이아웃과 매장 정보 화면이 함께 부르고, 로그아웃 후 다른 매장이 바로 로그인할 수도 있다.
  // 마지막 요청의 응답만 반영해 늦게 도착한 이전 계정의 매장 정보가 덮어쓰지 않게 한다.
  // 다시 받는 동안 기존 정보를 남겨 사이드바 카드가 깜빡이지 않게 한다. 계정이 바뀌면 아래 onSessionChange 가 비운다
  loadShop: async () => {
    const requestId = get().loadRequestId + 1;
    set({ loadRequestId: requestId, status: 'loading', error: null });
    const isCurrent = () => get().loadRequestId === requestId;
    try {
      const user = await shopApi.getCurrentUser();
      if (!isCurrent()) return;
      if (user?.role !== 'OWNER' || !user.shopId)
        throw { message: '매장 계정으로 로그인해 주세요.' };
      const shop = await shopApi.getShop(user.shopId);
      if (isCurrent()) set({ shop, status: 'succeeded' });
    } catch (error) {
      if (isCurrent()) set({ status: 'failed', error: failure(error) });
    }
  },
  saveShop: async (payload) => {
    set({ mutationStatus: 'loading' });
    try {
      const { shopId } = get().shop;
      const shop = await shopApi.updateShop(shopId, payload);
      // 저장 중에 로그아웃했으면 비워 둔 상태에 이전 매장 정보를 다시 넣지 않는다
      set((state) => ({
        mutationStatus: 'succeeded',
        ...(state.shop?.shopId === shopId ? { shop } : {}),
      }));
      return shop;
    } catch (error) {
      set({ mutationStatus: 'failed' });
      throw failure(error);
    }
  },
  saveImage: async (file) => {
    set({ mutationStatus: 'loading' });
    const shopId = get().shop?.shopId;
    try {
      const result = file ? await shopApi.uploadImage(file) : await shopApi.deleteImage();
      // 업로드 중에 계정이 바뀌었으면 비워 둔 상태나 다른 매장에 이미지 값을 넣지 않는다
      set((state) => ({
        mutationStatus: 'succeeded',
        ...(state.shop?.shopId === shopId
          ? { shop: { ...state.shop, imageUrl: result?.imageUrl ?? null } }
          : {}),
      }));
    } catch (error) {
      set({ mutationStatus: 'failed' });
      throw failure(error);
    }
  },
}));

onSessionChange(() =>
  useShopStore.setState((state) => ({
    shop: null,
    status: 'idle',
    error: null,
    loadRequestId: state.loadRequestId + 1,
  })),
);
