import { useState } from 'react';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import { datePart, timePart } from '@/utils/scheduleDate';
import { formatDayLabel } from '@/utils/scheduleSlots';
import styles from './ClosingImpactDialog.module.css';

// 길어지면 창이 넘치므로 앞의 몇 건만 보여주고 나머지는 수로 알린다
const MAX_ROWS = 5;

const timeLabel = ({ startsAt, endsAt }) =>
  `${formatDayLabel(datePart(startsAt))} ${timePart(startsAt)}~${timePart(endsAt)}`;

function TimeList({ items }) {
  return (
    <ul className={styles.list}>
      {items.slice(0, MAX_ROWS).map((item) => (
        <li key={item.startsAt}>{timeLabel(item)}</li>
      ))}
      {items.length > MAX_ROWS && <li>외 {items.length - MAX_ROWS}건</li>}
    </ul>
  );
}

/**
 * 요일을 휴무로 저장하기 전 확인창. 그 요일의 예약은 유지되고, 등록해 둔 시간대 열기는 그대로 두면
 * 휴무여도 계속 판매되므로 함께 닫을지 묻는다. 예약이 있는 열기는 닫을 수 없어 유지된다.
 *
 * @param labels    휴무로 바꾸는 요일 이름 (예: 목, 금)
 * @param impact    { reservations, openExceptions }
 * @param onConfirm 함께 닫을지 여부를 받는다
 */
export default function ClosingImpactDialog({ labels, impact, isSaving, onCancel, onConfirm }) {
  const { reservations, openExceptions } = impact;
  const closable = openExceptions.filter((item) => !item.reserved);
  const reserved = openExceptions.filter((item) => item.reserved);
  const [closeOpens, setCloseOpens] = useState(true);

  return (
    <Dialog title={`${labels}요일을 휴무로 바꿀까요?`} closeOnBackdrop={false} onClose={onCancel}>
      <div className="b-stack">
        {reservations.length > 0 && (
          <section className={styles.section}>
            <h3>남은 예약 {reservations.length}건은 그대로 유지돼요</h3>
            <TimeList items={reservations} />
            <p>새 예약만 막혀요. 이 날 쉬려면 예약 현황에서 예약을 취소해 주세요.</p>
          </section>
        )}
        {closable.length > 0 && (
          <section className={styles.section}>
            <h3>이 요일에 연 시간이 {closable.length}건 있어요</h3>
            <TimeList items={closable} />
            <label className={styles.check}>
              <input
                type="checkbox"
                checked={closeOpens}
                disabled={isSaving}
                onChange={(event) => setCloseOpens(event.target.checked)}
              />
              함께 닫기 (해제하면 휴무여도 이 시간은 계속 판매돼요)
            </label>
          </section>
        )}
        {reserved.length > 0 && (
          <section className={styles.section}>
            <h3>예약이 있는 연 시간 {reserved.length}건은 닫지 않아요</h3>
            <TimeList items={reserved} />
            <p>예약이 없는 나머지 시간도 계속 판매돼요. 필요하면 빈자리 화면에서 닫아 주세요.</p>
          </section>
        )}
        <div className="b-flex">
          <Button type="button" disabled={isSaving} onClick={onCancel}>
            취소
          </Button>
          <Button
            type="button"
            variant="primary"
            disabled={isSaving}
            onClick={() => onConfirm(closable.length > 0 && closeOpens)}
          >
            {isSaving ? '저장 중…' : '휴무로 저장'}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
