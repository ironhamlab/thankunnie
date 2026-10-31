import { create } from 'zustand';
import { shopApi } from '@/api/shopApi';

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
  // 사장님 센터 레이아웃과 매장 정보 화면이 함께 부르고, 로그아웃 후 다른 사장님이 바로 로그인할 수도 있다.
  // 마지막 요청의 응답만 반영해 늦게 도착한 이전 계정의 매장 정보가 덮어쓰지 않게 한다
  loadShop: async () => {
    const requestId = get().loadRequestId + 1;
    set({ loadRequestId: requestId, shop: null, status: 'loading', error: null });
    const isCurrent = () => get().loadRequestId === requestId;
    try {
      const user = await shopApi.getCurrentUser();
      if (!isCurrent()) return;
      if (user?.role !== 'OWNER' || !user.shopId)
        throw { message: '사장님 계정으로 로그인해 주세요.' };
      const shop = await shopApi.getShop(user.shopId);
      if (isCurrent()) set({ shop, status: 'succeeded' });
    } catch (error) {
      if (isCurrent()) set({ status: 'failed', error: failure(error) });
    }
  },
  saveShop: async (payload) => {
    set({ mutationStatus: 'loading' });
    try {
      const shop = await shopApi.updateShop(get().shop.shopId, payload);
      set({ shop, mutationStatus: 'succeeded' });
      return shop;
    } catch (error) {
      set({ mutationStatus: 'failed' });
      throw failure(error);
    }
  },
  saveImage: async (file) => {
    set({ mutationStatus: 'loading' });
    try {
      const result = file ? await shopApi.uploadImage(file) : await shopApi.deleteImage();
      set((state) => ({
        shop: { ...state.shop, imageUrl: result?.imageUrl ?? null },
        mutationStatus: 'succeeded',
      }));
    } catch (error) {
      set({ mutationStatus: 'failed' });
      throw failure(error);
    }
  },
}));
