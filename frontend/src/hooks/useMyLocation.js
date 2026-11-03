import { useEffect, useState } from 'react';

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
 *
 * @returns {{ position: { lat: number, lng: number } | null, locating: boolean, message: string, locate: () => void }}
 */
export function useMyLocation() {
  const [position, setPosition] = useState(null);
  const [locating, setLocating] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!message) return undefined;
    const timer = window.setTimeout(() => setMessage(''), LOCATE_MESSAGE_MS);
    return () => window.clearTimeout(timer);
  }, [message]);

  const locate = () => {
    if (!navigator.geolocation) {
      setMessage('이 브라우저에서는 위치를 확인할 수 없어요');
      return;
    }
    setLocating(true);
    setMessage('');
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setLocating(false);
        setPosition({ lat: coords.latitude, lng: coords.longitude });
      },
      (error) => {
        setLocating(false);
        setMessage(
          error.code === PERMISSION_DENIED
            ? '위치 권한이 꺼져 있어요. 브라우저 설정에서 허용해 주세요'
            : '위치를 가져오지 못했어요. 잠시 후 다시 시도해 주세요',
        );
      },
      LOCATE_OPTIONS,
    );
  };

  return { position, locating, message, locate };
}
