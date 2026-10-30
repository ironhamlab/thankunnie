import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useReservationStore } from '@/store/useReservationStore';
import { toKstDateTime } from '@/utils/scheduleDate';

/**
 * 예약하기 버튼 동작. 서버에 예약(5분 점유)을 만들고 결제 화면으로 이동한다.
 * 실패하면 이동하지 않고 서버가 알려준 이유를 error 로 돌려준다.
 */
export default function useReserve() {
  const navigate = useNavigate();
  const createReservation = useReservationStore((s) => s.createReservation);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  /**
   * @param {number} treatmentId 서버 시술 번호
   * @param {string} date        YYYY-MM-DD
   * @param {string} time        HH:mm (30분 단위)
   */
  const reserve = async (treatmentId, date, time) => {
    setError('');
    setLoading(true);
    try {
      const reservation = await createReservation({
        treatmentId,
        startsAt: toKstDateTime(date, time.padStart(5, '0')),
      });
      // 새로고침해도 결제 화면이 서버에서 예약을 다시 불러올 수 있도록 예약 번호를 주소에 남긴다
      navigate(`${ROUTES.CHECKOUT}?reservationId=${reservation.reservationId}`);
    } catch (err) {
      setError(err.message);
      setLoading(false);
    }
  };

  return { reserve, loading, error };
}
