import { useEffect, useRef, useState } from 'react';

// 내 위치는 정확도보다 빠른 응답이 중요하고, 1분 안에 얻은 위치는 다시 쓴다
const LOCATE_OPTIONS = { enableHighAccuracy: false, timeout: 10000, maximumAge: 60000 };
// 위치를 못 얻었을 때 안내를 보여주는 시간
const LOCATE_MESSAGE_MS = 4000;
const PERMISSION_DENIED = 1;

/**
 * 브라우저 위치 확인. 권한은 처음에 묻지 않고 locate() 를 불렀을 때만 묻는다.
 * 위치는 화면에서만 쓰고 서버로 보내지 않는다.
 *
 * position 은 locate() 가 성공할 때마다 새 객체로 바뀌므로, 같은 자리에서 다시 눌러도 이펙트가 다시 돈다.
 * message 는 위치를 못 얻은 이유이며 잠시 뒤 저절로 지워진다.
 * locate() 는 얻은 위치를, 못 얻으면 null 을 돌려주는 Promise 다. 실패에 따라 화면 선택을 되돌릴 때 쓴다.
 * 확인하는 중에 다시 부르면 새로 묻지 않고 진행 중인 결과를 같이 받는다.
 * 겹친 요청이 서로 다른 결과로 끝나면 먼저 성공한 위치를 늦게 온 실패가 되돌릴 수 있기 때문이다.
 *
 * @returns {{ position: { lat: number, lng: number } | null, locating: boolean, message: string, locate: () => Promise<{ lat: number, lng: number } | null> }}
 */
export function useMyLocation() {
  const [position, setPosition] = useState(null);
  const [locating, setLocating] = useState(false);
  const [message, setMessage] = useState('');
  const pendingRef = useRef(null);

  useEffect(() => {
    if (!message) return undefined;
    const timer = window.setTimeout(() => setMessage(''), LOCATE_MESSAGE_MS);
    return () => window.clearTimeout(timer);
  }, [message]);

  const request = () =>
    new Promise((resolve) => {
      if (!navigator.geolocation) {
        setMessage('이 브라우저에서는 위치를 확인할 수 없어요');
        resolve(null);
        return;
      }
      setLocating(true);
      setMessage('');
      navigator.geolocation.getCurrentPosition(
        ({ coords }) => {
          const found = { lat: coords.latitude, lng: coords.longitude };
          setLocating(false);
          setPosition(found);
          resolve(found);
        },
        (error) => {
          setLocating(false);
          setMessage(
            error.code === PERMISSION_DENIED
              ? '위치 권한이 꺼져 있어요. 브라우저 설정에서 허용해 주세요'
              : '위치를 가져오지 못했어요. 잠시 후 다시 시도해 주세요',
          );
          resolve(null);
        },
        LOCATE_OPTIONS,
      );
    });

  const locate = () => {
    if (!pendingRef.current) {
      pendingRef.current = request().finally(() => {
        pendingRef.current = null;
      });
    }
    return pendingRef.current;
  };

  return { position, locating, message, locate };
}
