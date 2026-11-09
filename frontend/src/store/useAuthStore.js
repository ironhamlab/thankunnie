import { create } from 'zustand';
import { authApi } from '@/api/authApi';
import { ACCOUNT_TYPE } from '@/constants/accountType';

// 새로고침해도 로그인이 유지되도록 토큰과 사용자 정보를 브라우저에 보관한다 (토큰 유효기간 24시간, 재발급 없음)
const STORAGE_KEY = 'thankunnie.auth';

const toError = (error, fallback) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? fallback,
});

const removeStoredSession = () => {
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // 저장소를 쓸 수 없으면 메모리 세션만 지운다
  }
};

/**
 * 저장된 세션을 읽는다. 만료 시각이 지났으면 버린다.
 * 만료 판정의 기준은 서버이며, 여기서는 이미 지난 토큰을 보내지 않기 위한 정리만 한다.
 */
const readStoredSession = () => {
  try {
    const stored = JSON.parse(window.localStorage.getItem(STORAGE_KEY));
    if (stored?.token && Date.parse(stored.expiresAt) > Date.now()) {
      return stored;
    }
  } catch {
    // 형식이 깨졌거나 저장소를 쓸 수 없으면 비로그인으로 시작한다
  }
  removeStoredSession();
  return null;
};

const writeStoredSession = (session) => {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  } catch {
    // 저장하지 못해도 이번 화면에서는 로그인 상태를 유지한다
  }
};

/** 로그인·가입 응답(AuthTokenResponse)을 store 세션 형태로 바꾼다. */
const toSession = (response) => ({
  token: response.accessToken,
  expiresAt: response.expiresAt,
  user: { userId: response.userId, role: response.role, shopId: response.shopId },
});

const storedSession = readStoredSession();

/**
 * 인증 상태 store. 토큰은 axiosInstance 가 Authorization: Bearer 로 붙인다 (main.jsx 의 configureAuth).
 * 로그인·가입은 성공하면 사용자 정보를 반환하고, 실패하면 오류 상태를 갱신한 뒤 오류를 던진다.
 */
export const useAuthStore = create((set) => {
  const applySession = (response) => {
    const session = toSession(response);
    writeStoredSession(session);
    set({ token: session.token, data: session.user, status: 'succeeded', error: null });
    return session.user;
  };

  const request = async (call, fallbackMessage) => {
    set({ status: 'loading', error: null });
    try {
      return applySession(await call());
    } catch (error) {
      const authError = toError(error, fallbackMessage);
      set({ status: 'failed', error: authError });
      throw authError;
    }
  };

  // 매장 가입은 관리자 승인 전이라 토큰이 없다. 세션은 그대로 두고 신청 결과 { shopId, status } 를 돌려준다
  const signupShop = async (payload) => {
    set({ status: 'loading', error: null });
    try {
      const result = await authApi.signupShop(payload);
      set({ status: 'idle' });
      return result;
    } catch (error) {
      const authError = toError(error, '가입하지 못했습니다.');
      set({ status: 'failed', error: authError });
      throw authError;
    }
  };

  return {
    data: storedSession?.user ?? null, // 로그인 사용자 정보 { userId, role, shopId }
    token: storedSession?.token ?? null,
    status: storedSession ? 'succeeded' : 'idle', // 'idle' | 'loading' | 'succeeded' | 'failed'
    error: null,

    /** accountType: ACCOUNT_TYPE.CUSTOMER(고객·관리자) 또는 ACCOUNT_TYPE.SHOP(매장) */
    login: (accountType, credentials) =>
      request(
        () =>
          accountType === ACCOUNT_TYPE.SHOP
            ? authApi.loginShop(credentials)
            : authApi.login(credentials),
        '로그인하지 못했습니다.',
      ),

    /** 고객은 가입 후 바로 로그인 상태가 된다. */
    signup: (accountType, payload) => {
      if (accountType === ACCOUNT_TYPE.SHOP) return signupShop(payload);
      return request(() => authApi.signup(payload), '가입하지 못했습니다.');
    },

    setSession: ({ token, user }) => set({ token, data: user, status: 'succeeded', error: null }),

    /** 로그아웃·인증 만료(401) 시 메모리와 브라우저 저장 세션을 모두 지운다. */
    clearSession: () => {
      removeStoredSession();
      set({ token: null, data: null, status: 'idle', error: null });
    },

    clearError: () => set({ error: null }),

    // 매장 가입 화면의 카테고리 선택지 (인증 API 의 비로그인 조회)
    shopCategories: [],
    shopCategoriesStatus: 'idle',
    shopCategoriesError: null,

    loadShopCategories: async () => {
      set({ shopCategoriesStatus: 'loading', shopCategoriesError: null });
      try {
        const categories = await authApi.getShopCategories();
        set({ shopCategories: categories ?? [], shopCategoriesStatus: 'succeeded' });
      } catch (error) {
        set({
          shopCategoriesStatus: 'failed',
          shopCategoriesError: toError(error, '매장 카테고리를 불러오지 못했습니다.'),
        });
      }
    },
  };
});

// 다른 탭에서 로그인·로그아웃하면 이 탭도 같은 세션으로 맞춘다.
// storage 이벤트는 값을 바꾼 탭이 아닌 다른 탭에서만 발생하므로 서로 다시 부르지 않는다
window.addEventListener('storage', (event) => {
  if (event.key !== STORAGE_KEY && event.key !== null) {
    return;
  }
  const session = readStoredSession();
  useAuthStore.setState(
    session
      ? { token: session.token, data: session.user, status: 'succeeded', error: null }
      : { token: null, data: null, status: 'idle', error: null },
  );
});
