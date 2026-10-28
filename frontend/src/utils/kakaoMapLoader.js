const SDK_URL = 'https://dapi.kakao.com/v2/maps/sdk.js';

let loadPromise = null;

export function hasKakaoMapKey() {
  return Boolean(import.meta.env.VITE_KAKAO_MAP_APP_KEY);
}

// SDK 스크립트는 앱 전체에서 한 번만 붙이고, 이후 호출은 같은 Promise를 돌려준다
export function loadKakaoMap() {
  if (loadPromise) {
    return loadPromise;
  }
  const appKey = import.meta.env.VITE_KAKAO_MAP_APP_KEY;
  if (!appKey) {
    return Promise.reject(new Error('VITE_KAKAO_MAP_APP_KEY가 설정되지 않았습니다.'));
  }

  loadPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    // autoload=false 로 받아야 kakao.maps.load 콜백 이후에 지도를 안전하게 만들 수 있다
    script.src = `${SDK_URL}?appkey=${encodeURIComponent(appKey)}&autoload=false`;
    script.async = true;
    script.onload = () => window.kakao.maps.load(() => resolve(window.kakao.maps));
    script.onerror = () => {
      script.remove();
      // 실패한 Promise를 남겨두면 다시 시도할 수 없으므로 비운다
      loadPromise = null;
      reject(new Error('카카오맵 SDK를 불러오지 못했습니다. 앱 키와 등록 도메인을 확인하세요.'));
    };
    document.head.appendChild(script);
  });
  return loadPromise;
}
