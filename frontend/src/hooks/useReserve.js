import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useReservationStore } from '@/store/useReservationStore';
import { toKstDateTime } from '@/utils/scheduleDate';

// 다른 시간의 결제 대기 예약이 이미 있을 때 서버가 돌려주는 코드 (한 고객은 점유를 하나만 가진다)
const HOLD_EXISTS = 'RESERVATION_006';

/**
 * 예약하기 버튼 동작. 서버에 예약(5분 점유)을 만들고 결제 화면으로 이동한다.
 * 실패하면 이동하지 않고 서버가 알려준 이유를 error 로 돌려준다.
 * 결제 대기 중인 다른 예약이 있으면 pendingHold 에 그 예약을 담아, 화면이 바꿀지 묻게 한다.
 */
export default function useReserve() {
  const navigate = useNavigate();
  const createReservation = useReservationStore((s) => s.createReservation);
  const findMyHold = useReservationStore((s) => s.findMyHold);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  // 바꿀지 묻는 동안 기존 예약과 다시 보낼 요청을 들고 있는다
  const [pendingHold, setPendingHold] = useState(null);

  const submit = async (request) => {
    setError('');
    setLoading(true);
    try {
      const reservation = await createReservation(request);
      setPendingHold(null);
      // 새로고침해도 결제 화면이 서버에서 예약을 다시 불러올 수 있도록 예약 번호를 주소에 남긴다
      navigate(`${ROUTES.CHECKOUT}?reservationId=${reservation.reservationId}`);
    } catch (err) {
      if (err.code === HOLD_EXISTS && !request.replaceHold) {
        let hold;
        try {
          hold = await findMyHold();
        } catch {
          // 기존 예약을 확인하지 못했으면 바꾸지 않는다. 사용자가 다시 누르면 처음부터 다시 묻는다
          setPendingHold(null);
          setError('결제 대기 중인 예약을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.');
          setLoading(false);
          return;
        }
        // 찾는 사이 기존 예약이 만료됐으면 물을 것이 없으므로 바로 다시 보낸다
        if (!hold) {
          await submit({ ...request, replaceHold: true });
          return;
        }
        setPendingHold({ hold, request });
      } else {
        setPendingHold(null);
        setError(err.message);
      }
      setLoading(false);
    }
  };

  /**
   * @param {number} treatmentId 서버 시술 번호
   * @param {string} date        YYYY-MM-DD
   * @param {string} time        HH:mm (30분 단위)
   */
  const reserve = (treatmentId, date, time) =>
    submit({ treatmentId, startsAt: toKstDateTime(date, time.padStart(5, '0')) });

  // 기존 예약의 확보를 풀고 이 시간으로 바꾼다. 새 시간이 그새 찼으면 서버가 거절하고 기존 확보는 남는다
  const replaceHold = () => pendingHold && submit({ ...pendingHold.request, replaceHold: true });

  const cancelReplace = () => setPendingHold(null);

  return {
    reserve,
    loading,
    error,
    pendingHold: pendingHold?.hold ?? null,
    replaceHold,
    cancelReplace,
  };
}
