const SDK_URL = 'https://cdn.iamport.kr/v1/iamport.js';
const LOAD_TIMEOUT_MS = 10000;

let loadPromise = null;

export function hasPortOneCode() {
  return Boolean(import.meta.env.VITE_PORTONE_IMP_CODE);
}

// SDK 스크립트는 앱 전체에서 한 번만 붙이고, 가맹점 코드로 초기화한 IMP 를 돌려준다
export function loadPortOne() {
  if (loadPromise) {
    return loadPromise;
  }
  const impCode = import.meta.env.VITE_PORTONE_IMP_CODE;
  if (!impCode) {
    return Promise.reject(new Error('VITE_PORTONE_IMP_CODE가 설정되지 않았습니다.'));
  }

  loadPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');

    const fail = (message) => {
      clearTimeout(timer);
      script.remove();
      // 실패한 Promise를 남겨두면 다시 시도할 수 없으므로 비운다
      loadPromise = null;
      reject(new Error(message));
    };

    const timer = setTimeout(
      () => fail('결제 모듈 응답이 지연되었습니다. 잠시 후 다시 시도해 주세요.'),
      LOAD_TIMEOUT_MS,
    );

    script.src = SDK_URL;
    script.async = true;
    script.onload = () => {
      if (!window.IMP) {
        fail('결제 모듈을 불러오지 못했습니다.');
        return;
      }
      clearTimeout(timer);
      window.IMP.init(impCode);
      resolve(window.IMP);
    };
    script.onerror = () => fail('결제 모듈을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
    document.head.appendChild(script);
  });
  return loadPromise;
}

// 결제창 콜백을 Promise 로 바꾼다. 결제 실패·고객 취소도 resolve 되며 rsp.success 로 구분한다
export function requestPay(IMP, params) {
  return new Promise((resolve) => IMP.request_pay(params, resolve));
}
