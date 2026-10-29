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
  loadShop: async () => {
    set({ shop: null, status: 'loading', error: null });
    try {
      const user = await shopApi.getCurrentUser();
      if (user?.role !== 'OWNER' || !user.shopId)
        throw { message: '사장님 계정으로 로그인해 주세요.' };
      const shop = await shopApi.getShop(user.shopId);
      set({ shop, status: 'succeeded' });
    } catch (error) {
      set({ status: 'failed', error: failure(error) });
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
