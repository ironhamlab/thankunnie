import { create } from 'zustand';
import { termsApi } from '@/api/termsApi';
import { AGREEMENT_REQUIRED_ERRORS, TERMS_CODE } from '@/constants/terms';

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

  loadTerms: async () => {
    set({ status: 'loading', error: null });
    try {
      const terms = await termsApi.getTerms();
      set({ terms: terms ?? [], status: 'succeeded' });
    } catch (error) {
      set({ status: 'failed', error: toError(error, '약관을 불러오지 못했습니다.') });
    }
  },

  // 열려 있는 동의 모달: { id, termsCode, smsRequestId, resolve, saving, error }
  pending: null,
  pendingId: 0,

  /** 동의 모달을 띄우고, 동의해서 저장까지 끝나면 true·닫으면 false 를 돌려준다 */
  requestAgreement: (termsCode) =>
    new Promise((resolve) => {
      // 이미 열린 모달이 있으면 먼저 닫힌 것으로 처리한다
      get().pending?.resolve(false);
      const id = get().pendingId + 1;
      // 모달을 연 때의 문자 수신 조회 번호. 그 뒤 화면을 벗어났으면 동의 결과를 체크박스에 반영하지 않는다
      const { smsRequestId } = get();
      set({
        pendingId: id,
        pending: { id, termsCode, smsRequestId, resolve, saving: false, error: null },
      });
    }),

  confirmAgreement: async () => {
    const { pending } = get();
    if (!pending || pending.saving) return;
    set({ pending: { ...pending, saving: true, error: null } });
    // 저장 중에 모달을 닫았거나 다른 동의 모달이 열렸으면, 이 요청의 결과는 지금 모달에 반영하지 않는다
    const isCurrent = () => get().pending?.id === pending.id;
    try {
      await termsApi.agree(pending.termsCode);
      // 구독 등록 중에 동의해도 마이페이지 문자 수신 체크박스가 켜지게 한다
      if (
        pending.termsCode === TERMS_CODE.SMS_NOTIFICATION &&
        get().smsRequestId === pending.smsRequestId
      ) {
        set({ smsAgreement: { ...get().smsAgreement, agreed: true } });
      }
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

  // 마이페이지 알림 문자 수신 설정. agreed 는 서버에서 확인한 값이다
  smsAgreement: { status: 'idle', agreed: false, saving: false, error: null },
  // 조회·초기화마다 올린다. 화면을 벗어난 뒤(계정 전환 포함) 늦게 온 응답이 지금 상태를 덮어쓰지 않게 한다
  smsRequestId: 0,

  loadSmsAgreement: async () => {
    const requestId = get().smsRequestId + 1;
    set({
      smsRequestId: requestId,
      smsAgreement: { ...get().smsAgreement, status: 'loading', error: null },
    });
    const isCurrent = () => get().smsRequestId === requestId;
    try {
      const agreements = await termsApi.getMyAgreements();
      if (!isCurrent()) return;
      const agreed = (agreements ?? []).some((item) => item.code === TERMS_CODE.SMS_NOTIFICATION);
      set({ smsAgreement: { status: 'succeeded', agreed, saving: false, error: null } });
    } catch (error) {
      if (!isCurrent()) return;
      set({
        smsAgreement: {
          ...get().smsAgreement,
          status: 'failed',
          error: toError(error, '문자 수신 설정을 불러오지 못했습니다.'),
        },
      });
    }
  },

  /** 알림 문자 수신 동의를 철회한다. 성공하면 { ok: true } */
  withdrawSmsAgreement: async () => {
    if (get().smsAgreement.saving) return { ok: false };
    const requestId = get().smsRequestId;
    set({ smsAgreement: { ...get().smsAgreement, saving: true, error: null } });
    const isCurrent = () => get().smsRequestId === requestId;
    try {
      await termsApi.withdraw(TERMS_CODE.SMS_NOTIFICATION);
      if (isCurrent()) {
        set({ smsAgreement: { ...get().smsAgreement, agreed: false, saving: false } });
      } else if (get().smsAgreement.status !== 'idle') {
        // 철회 중에 화면을 벗어났다가 다시 열었으면, 새 조회가 철회보다 먼저 처리됐을 수 있어 한 번 더 불러온다
        get().loadSmsAgreement();
      }
      return { ok: true };
    } catch (error) {
      const failure = toError(error, '문자 수신을 철회하지 못했습니다.');
      if (isCurrent()) set({ smsAgreement: { ...get().smsAgreement, saving: false, error: failure } });
      return { ok: false, error: failure };
    }
  },

  // 로그아웃 뒤 다른 계정에 이전 설정이 보이지 않도록 화면을 벗어나면 지운다
  resetSmsAgreement: () =>
    set({
      smsRequestId: get().smsRequestId + 1,
      smsAgreement: { status: 'idle', agreed: false, saving: false, error: null },
    }),

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
