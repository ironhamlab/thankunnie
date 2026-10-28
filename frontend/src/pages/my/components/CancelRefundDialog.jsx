import { useState } from 'react';
import { SERVICES, finish, money } from '@/utils/demoData';
import { CANCEL_REASONS, quoteRefund } from '@/utils/demoRefundPolicy';
import Dialog from '@/components/common/Dialog';
import Button from '@/components/common/Button';
import styles from './CancelRefundDialog.module.css';

/**
 * 예약 취소 확인 및 환불 내역 안내
 */
export default function CancelRefundDialog({ booking, onClose, onConfirm }) {
  const [reason, setReason] = useState(CANCEL_REASONS[0]);
  const [isAgreed, setIsAgreed] = useState(false);
  const quote = quoteRefund(booking);
  const service = SERVICES.find((item) => item.id === booking.serviceId);
  const isNoRefund = quote.refundAmount === 0;

  const handleConfirm = () => {
    onConfirm(reason);
  };

  return (
    <Dialog title="예약을 취소하시겠어요?" onClose={onClose}>
      <div className={styles.cancelDialog}>
        <div className={styles.cancelTarget}>
          <small className="b-muted">{booking.id}</small>
          <strong>{service?.name}</strong>
          <span>
            {booking.date} {booking.time} ~ {finish(booking.time, booking.duration)}
          </span>
        </div>

        <div className={`${styles.cancelAmount} ${isNoRefund ? styles.isZero : ''}`}>
          <span>환불 예정 금액</span>
          <strong>{money(quote.refundAmount)}</strong>
          <small>
            {quote.tier.label} 취소 · 납부 예약금 {money(quote.paidAmount)} 중{' '}
            {money(quote.nonRefundableAmount)} 차감
          </small>
        </div>

        {isNoRefund && (
          <p className="b-error" role="alert">
            이용 당일 취소는 예약금이 환불되지 않습니다.
          </p>
        )}

        <fieldset className={styles.cancelReasons}>
          <legend>취소 사유</legend>
          {CANCEL_REASONS.map((item) => (
            <label key={item} className={reason === item ? 'is-selected' : ''}>
              <input
                type="radio"
                name="cancel-reason"
                value={item}
                checked={reason === item}
                onChange={() => setReason(item)}
              />
              {item}
            </label>
          ))}
        </fieldset>

        <label className={styles.cancelAgree}>
          <input
            type="checkbox"
            checked={isAgreed}
            onChange={(event) => setIsAgreed(event.target.checked)}
          />
          환불 정책과 환불 예정 금액을 확인했습니다.
        </label>

        <div className={styles.cancelActions}>
          <Button onClick={onClose}>예약 유지</Button>
          <Button variant="primary" disabled={!isAgreed} onClick={handleConfirm}>
            {money(quote.refundAmount)} 환불받고 취소
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
