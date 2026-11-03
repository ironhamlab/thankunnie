import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useReservationStore } from '@/store/useReservationStore';
import { toKstDateTime } from '@/utils/scheduleDate';

// 다른 시간의 결제 대기 예약이 이미 있을 때 서버가 돌려주는 코드 (한 고객은 점유를 하나만 가진다)
const HOLD_EXISTS = 'RESERVATION_006';
// 같은 빈자리에 먼저 선 사람이 있어 아직 예약할 차례가 아닐 때 서버가 돌려주는 코드
const NOT_YOUR_TURN = 'RESERVATION_007';
// 대기 중 순서를 다시 묻는 간격. 서버는 15초 동안 묻지 않으면 떠난 것으로 보고 줄에서 뺀다
const QUEUE_POLL_MS = 2000;
const QUEUE_CLOSED_MESSAGE =
  '다른 분이 먼저 예약했거나 예약할 수 없는 시간이 되었어요. 다른 시간을 선택해 주세요.';

const toSlot = ({ treatmentId, startsAt }) => ({ treatmentId, startsAt });

/**
 * 예약하기 버튼 동작. 같은 빈자리를 여러 명이 동시에 누를 수 있으므로 먼저 대기열에 선다.
 * 내 차례면 서버에 예약(5분 점유)을 만들고 결제 화면으로 이동하고, 아니면 queue 에 순서를 담아 대기 화면을 띄운다.
 * 대기 중에는 몇 초마다 순서를 다시 묻고, 차례가 오면 바로 예약한다.
 * 실패하면 이동하지 않고 서버가 알려준 이유를 error 로 돌려준다.
 * 결제 대기 중인 다른 예약이 있으면 pendingHold 에 그 예약을 담아, 화면이 바꿀지 묻게 한다.
 */
export default function useReserve() {
  const navigate = useNavigate();
  const createReservation = useReservationStore((s) => s.createReservation);
  const findMyHold = useReservationStore((s) => s.findMyHold);
  const joinQueue = useReservationStore((s) => s.joinQueue);
  const leaveQueue = useReservationStore((s) => s.leaveQueue);
  const releaseHold = useReservationStore((s) => s.releaseHold);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  // 바꿀지 묻는 동안 기존 예약과 다시 보낼 요청을 들고 있는다
  const [pendingHold, setPendingHold] = useState(null);
  // 대기 중일 때 { request, aheadCount, paying }. 대기하지 않으면 null
  const [queue, setQueue] = useState(null);
  // 화면을 떠날 때 서 있던 줄에서 나가도록 지금 대기 중인 빈자리를 기억한다
  const waitingSlotRef = useRef(null);
  // 응답을 기다리는 순서 조회. 나가기가 이 조회보다 먼저 서버에 닿으면 조회가 다시 줄에 세우므로 끝난 뒤 나간다
  const joinInFlightRef = useRef(null);

  const requestTicket = (slot) => {
    const pending = joinQueue(slot);
    joinInFlightRef.current = pending;
    const clear = () => {
      if (joinInFlightRef.current === pending) joinInFlightRef.current = null;
    };
    pending.then(clear, clear);
    return pending;
  };

  const leaveAfterJoin = useCallback(
    (slot) => {
      Promise.resolve(joinInFlightRef.current)
        .catch(() => {})
        .then(() => leaveQueue(slot));
    },
    [leaveQueue],
  );

  // 줄 서기·예약 응답을 기다리는 사이 화면을 떠났는지. 대기 창이 뜨기 전이라 화면을 떠날 때 줄에서 나가는 처리가 걸리지 않는다
  const unmountedRef = useRef(false);
  useEffect(() => {
    unmountedRef.current = false;
    return () => {
      unmountedRef.current = true;
    };
  }, []);

  // retried: 기존 예약이 없어 한 번 다시 보낸 요청인지. 서버와 조회가 엇갈려도 계속 다시 보내지 않도록 한 번만 한다
  const create = async (request, retried = false) => {
    setError('');
    setLoading(true);
    try {
      const reservation = await createReservation(request);
      // 예약 응답을 기다리는 사이 화면을 떠났으면 결제 화면으로 데려가지 않는다.
      // 차례가 와서 자동으로 만든 점유일 수 있으므로 5분 동안 남겨 두지 않고 바로 푼다
      if (unmountedRef.current) {
        await releaseHold(reservation.reservationId);
        return;
      }
      setPendingHold(null);
      // 새로고침해도 결제 화면이 서버에서 예약을 다시 불러올 수 있도록 예약 번호를 주소에 남긴다
      navigate(`${ROUTES.CHECKOUT}?reservationId=${reservation.reservationId}`);
    } catch (err) {
      if (err.code === NOT_YOUR_TURN) {
        // 차례 시간이 지나 다음 사람에게 넘어갔다. 다시 줄에 서서 기다린다
        await submit(request);
        return;
      }
      // 예약하지 못하고 화면에 머물면 순서를 더 묻지 않으므로, 맨 앞 자리를 비워 뒷사람이 기다리지 않게 한다.
      // 결제 대기 예약을 바꾸기로 하면 다시 줄에 선다
      leaveQueue(toSlot(request));
      if (err.code === HOLD_EXISTS) {
        // 바꾸기를 눌렀는데도 오면 그 사이 점유가 다른 예약으로 바뀐 것이다. 지금 점유로 다시 묻는다
        const baseRequest = toSlot(request);
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
        // 찾는 사이 기존 예약이 만료됐으면 물을 것이 없으므로 바꾸기 없이 한 번 다시 보낸다
        if (!hold) {
          if (!retried) {
            await create(baseRequest, true);
            return;
          }
          setPendingHold(null);
          setError(err.message);
        } else {
          setPendingHold({ hold, request: baseRequest });
        }
      } else {
        setPendingHold(null);
        setError(err.message);
      }
      setLoading(false);
    }
  };

  // 서버가 알려준 내 순서에 따라 예약하거나, 결제 화면으로 가거나, 계속 기다린다
  const applyTicket = async (ticket, request) => {
    // 떠난 화면에 늦게 온 응답으로 예약(5분 점유)을 만들거나 줄에 남지 않도록 줄에서만 나간다
    if (unmountedRef.current) {
      leaveQueue(toSlot(request));
      return;
    }
    if (ticket.status === 'WAITING') {
      waitingSlotRef.current = toSlot(request);
      setQueue({ request, aheadCount: ticket.aheadCount, paying: ticket.paying });
      setLoading(false);
      return;
    }
    waitingSlotRef.current = null;
    setQueue(null);
    if (ticket.status === 'MY_TURN') {
      await create(request);
    } else if (ticket.status === 'HOLDING') {
      // 이미 이 빈자리를 점유하고 있다 (다른 탭에서 먼저 예약한 경우)
      navigate(`${ROUTES.CHECKOUT}?reservationId=${ticket.reservationId}`);
    } else {
      setError(QUEUE_CLOSED_MESSAGE);
      setLoading(false);
    }
  };

  const submit = async (request) => {
    setError('');
    setLoading(true);
    try {
      await applyTicket(await requestTicket(toSlot(request)), request);
    } catch (err) {
      setError(err.message);
      setLoading(false);
    }
  };

  // applyTicket 등은 렌더마다 새로 만들어진다. 순서 조회는 queue 가 바뀔 때만 다시 예약하도록 최신 함수를 ref 로 넘긴다
  const applyTicketRef = useRef(applyTicket);
  const requestTicketRef = useRef(requestTicket);
  useEffect(() => {
    applyTicketRef.current = applyTicket;
    requestTicketRef.current = requestTicket;
  });

  // 대기 중이면 몇 초마다 순서를 다시 묻는다. 서버가 살아 있음을 알아야 줄에서 빼지 않는다
  useEffect(() => {
    if (!queue) return undefined;
    let cancelled = false;
    const timer = setTimeout(async () => {
      let ticket;
      try {
        ticket = await requestTicketRef.current(toSlot(queue.request));
      } catch {
        // 잠깐 연결이 끊겨도 대기는 이어간다. 다음 차례에 다시 묻도록 같은 순서로 다시 그린다
        if (!cancelled) setQueue((current) => current && { ...current });
        return;
      }
      if (!cancelled) await applyTicketRef.current(ticket, queue.request);
    }, QUEUE_POLL_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [queue]);

  // 대기 화면을 띄운 채 다른 화면으로 가면 줄에서 나간다
  useEffect(
    () => () => {
      if (waitingSlotRef.current) leaveAfterJoin(waitingSlotRef.current);
    },
    [leaveAfterJoin],
  );

  const cancelQueue = () => {
    if (waitingSlotRef.current) leaveAfterJoin(waitingSlotRef.current);
    waitingSlotRef.current = null;
    setQueue(null);
  };

  /**
   * @param {number} treatmentId 서버 시술 번호
   * @param {string} date        YYYY-MM-DD
   * @param {string} time        HH:mm (30분 단위)
   */
  const reserve = (treatmentId, date, time) =>
    submit({ treatmentId, startsAt: toKstDateTime(date, time.padStart(5, '0')) });

  // 확인창에 보여준 그 예약의 확보를 풀고 이 시간으로 바꾼다. 새 시간이 그새 찼으면 서버가 거절하고 기존 확보는 남는다
  const replaceHold = () =>
    pendingHold &&
    submit({ ...pendingHold.request, replaceReservationId: pendingHold.hold.reservationId });

  const cancelReplace = () => setPendingHold(null);

  return {
    reserve,
    loading,
    error,
    pendingHold: pendingHold?.hold ?? null,
    replaceHold,
    cancelReplace,
    queue: queue && { aheadCount: queue.aheadCount, paying: queue.paying },
    cancelQueue,
  };
}
