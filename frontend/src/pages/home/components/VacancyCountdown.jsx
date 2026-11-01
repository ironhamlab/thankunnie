import { useEffect, useState } from 'react';

const pad = (value) => String(value).padStart(2, '0');

const formatRemaining = (milliseconds) => {
  const seconds = Math.floor(milliseconds / 1000);
  return `${pad(Math.floor(seconds / 3600))}:${pad(Math.floor(seconds / 60) % 60)}:${pad(seconds % 60)}`;
};

/**
 * 예약 마감까지 남은 시간. 1초마다 줄어든다.
 * 안내용 표시이며, 예약 가능 여부는 예약할 때 서버가 다시 확인한다.
 *
 * @param closesAt   예약 마감 시각 (ISO 문자열)
 * @param closesTime 화면 낭독기로 읽어 줄 마감 시각 (HH:mm)
 * @param className  겉모양
 */
export default function VacancyCountdown({ closesAt, closesTime, className }) {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const remaining = Date.parse(closesAt) - now;

  return (
    <span className={className} role="timer" aria-label={`예약 마감 ${closesTime}`}>
      {remaining > 0 ? (
        <>
          <small>마감까지</small>
          <b>{formatRemaining(remaining)}</b>
        </>
      ) : (
        <b>예약 마감</b>
      )}
    </span>
  );
}
