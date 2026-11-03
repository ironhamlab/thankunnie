import { create } from 'zustand';
import { termsApi } from '@/api/termsApi';
import { AGREEMENT_REQUIRED_ERRORS, TERMS_TARGET } from '@/constants/terms';

const toError = (error, fallback) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? fallback,
});

/**
 * 약관 목록(회원가입 화면)과 동의 모달 상태.
 * 동의 모달은 App 에 하나만 두고(TermsAgreementDialog), requestAgreement 가 사용자의 선택을 Promise 로 돌려준다.
 */
export const useTermsStore = create((set, get) => ({
  terms: [],
  status: 'idle',
  error: null,

  /** 고객·매장 가입 화면이 같은 상태를 쓰므로, 다른 대상을 불러오면 이전 목록을 비우고 가장 마지막 요청만 반영한다 */
  loadTerms: async (target = TERMS_TARGET.CUSTOMER) => {
    const requestId = get().termsRequestId + 1;
    set({ terms: [], status: 'loading', error: null, termsRequestId: requestId });
    try {
      const terms = await termsApi.getTerms(target);
      if (get().termsRequestId !== requestId) return;
      set({ terms: terms ?? [], status: 'succeeded' });
    } catch (error) {
      if (get().termsRequestId !== requestId) return;
      set({ status: 'failed', error: toError(error, '약관을 불러오지 못했습니다.') });
    }
  },
  termsRequestId: 0,

  // 열려 있는 동의 모달: { id, termsCode, resolve, saving, error }
  pending: null,
  pendingId: 0,

  /** 동의 모달을 띄우고, 동의해서 저장까지 끝나면 true·닫으면 false 를 돌려준다 */
  requestAgreement: (termsCode) =>
    new Promise((resolve) => {
      // 이미 열린 모달이 있으면 먼저 닫힌 것으로 처리한다
      get().pending?.resolve(false);
      const id = get().pendingId + 1;
      set({ pendingId: id, pending: { id, termsCode, resolve, saving: false, error: null } });
    }),

  confirmAgreement: async () => {
    const { pending } = get();
    if (!pending || pending.saving) return;
    set({ pending: { ...pending, saving: true, error: null } });
    // 저장 중에 모달을 닫았거나 다른 동의 모달이 열렸으면, 이 요청의 결과는 지금 모달에 반영하지 않는다
    const isCurrent = () => get().pending?.id === pending.id;
    try {
      await termsApi.agree(pending.termsCode);
      if (!isCurrent()) return;
      set({ pending: null });
      pending.resolve(true);
    } catch (error) {
      if (!isCurrent()) return;
      set({
        pending: {
          ...pending,
          saving: false,
          error: toError(error, '동의를 저장하지 못했습니다.'),
        },
      });
    }
  },

  /**
   * 이미 동의했으면 바로 true, 아니면 동의 모달을 띄워 결과를 돌려준다.
   * 요청 전에 동의를 먼저 받고 싶은 화면(찜 → 알림 받기)에서 쓴다. 계정 전환을 고려해 매번 서버에서 확인한다.
   */
  ensureAgreement: async (termsCode) => {
    try {
      const agreements = await termsApi.getMyAgreements();
      if ((agreements ?? []).some((item) => item.code === termsCode)) return true;
    } catch {
      // 확인하지 못했으면 동의 모달을 띄운다. 동의 API 는 이미 동의했어도 안전하다
    }
    return get().requestAgreement(termsCode);
  },

  // 저장 중에는 닫지 않는다 (모달도 닫기를 막지만, 다른 경로로 불려도 저장 결과와 어긋나지 않게 한다)
  cancelAgreement: () => {
    const { pending } = get();
    if (!pending || pending.saving) return;
    set({ pending: null });
    pending.resolve(false);
  },
}));

/**
 * 요청이 약관 미동의(TERMS_003·TERMS_004)로 막히면 동의 모달을 띄우고, 동의하면 한 번 다시 보낸다.
 * 동의하지 않으면 원래 오류를 그대로 던진다. 다른 오류는 그대로 던진다.
 *
 * @param {() => Promise<any>} action 다시 보낼 수 있는 요청
 */
export async function withTermsAgreement(action) {
  try {
    return await action();
  } catch (error) {
    const termsCode = AGREEMENT_REQUIRED_ERRORS[error?.code];
    if (!termsCode) throw error;
    const agreed = await useTermsStore.getState().requestAgreement(termsCode);
    if (!agreed) throw error;
    return action();
  }
}
