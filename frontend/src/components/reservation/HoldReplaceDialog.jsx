import { Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { datePart, timePart } from '@/utils/scheduleDate';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';

/**
 * 결제 대기 중인 예약이 있는데 다른 시간을 눌렀을 때 바꿀지 묻는다 (docs/api/reservations.md R6).
 *
 * @param hold      결제 대기 중인 기존 예약 (내 예약 목록 요약)
 * @param loading   바꾸는 요청을 보내는 중인지
 * @param onReplace 이 시간으로 바꾸기
 * @param onClose   닫기 (아무것도 바꾸지 않는다)
 */
export default function HoldReplaceDialog({ hold, loading, onReplace, onClose }) {
  return (
    <Dialog title="결제 대기 중인 예약이 있어요" onClose={onClose}>
      <p>
        <strong>
          {hold.shopName} · {hold.treatmentName}
        </strong>
        <br />
        {datePart(hold.startsAt)} {timePart(hold.startsAt)}
      </p>
      <p>이 시간으로 바꾸면 기존 예약의 임시 확보는 풀려요.</p>
      <div className="b-flex">
        <Button variant="primary" disabled={loading} onClick={onReplace}>
          {loading ? '바꾸는 중...' : '이 시간으로 바꾸기'}
        </Button>
        <Button as={Link} to={`${ROUTES.CHECKOUT}?reservationId=${hold.reservationId}`}>
          기존 예약 결제하기
        </Button>
      </div>
    </Dialog>
  );
}
