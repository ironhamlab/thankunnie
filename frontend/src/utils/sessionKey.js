import { useAuthStore } from '@/store/useAuthStore';
import { getDevTestUser } from '@/utils/devTestUser';

/**
 * 지금 로그인한 계정을 가리키는 값. 값이 바뀌면 다른 계정이다.
 * 메뉴를 다시 열 때 받아 둔 내용을 남겨 두되, 다른 계정에게 이전 매장의 내용이 보이지 않게 비교하는 데 쓴다.
 */
export const currentSessionKey = () => useAuthStore.getState().token ?? getDevTestUser() ?? '';
