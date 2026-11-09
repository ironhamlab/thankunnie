import { useState } from 'react';
import { datePart, timePart } from '@/utils/scheduleDate';
import { formatDayLabel } from '@/utils/scheduleSlots';
import Button from '@/components/common/Button';
import styles from './CloseOverlapNotice.module.css';

/**
 * 열려는 시간에 닫기가 겹칠 때의 안내. 닫기가 열기보다 우선이라 그대로 열면 예약을 받지 않으므로,
 * 겹치는 닫기를 줄마다 보여주고 그 자리에서 지울 수 있게 한다. 사유는 시간 옆 ▼ 로 펼쳐 본다.
 */
export default function CloseOverlapNotice({ closes, disabled, onDelete }) {
  const [openIds, setOpenIds] = useState(() => new Set());
  const toggle = (exceptionId) =>
    setOpenIds((prev) => {
      const next = new Set(prev);
      if (!next.delete(exceptionId)) {
        next.add(exceptionId);
      }
      return next;
    });

  if (!closes.length) {
    return null;
  }
  return (
    <section className={styles.root} role="alert">
      <strong>{formatDayLabel(datePart(closes[0].startsAt))} 닫아 둔 시간이 겹쳐요</strong>
      <p>닫기가 열기보다 우선이라, 아래 닫기를 지워야 그 시간에 예약을 받아요.</p>
      <ul>
        {closes.map((close) => {
          const open = Boolean(close.reason) && openIds.has(close.exceptionId);
          return (
            <li key={close.exceptionId}>
              <span className={styles.item}>
                <span className={styles.time}>
                  <b>
                    {timePart(close.startsAt)}~{timePart(close.endsAt)}
                  </b>
                  {close.reason && (
                    <button
                      type="button"
                      className={styles.toggle}
                      aria-expanded={open}
                      aria-label={open ? '사유 접기' : '사유 보기'}
                      onClick={() => toggle(close.exceptionId)}
                    >
                      {open ? '▲' : '▼'}
                    </button>
                  )}
                </span>
                {open && <small>사유: {close.reason}</small>}
              </span>
              {/* 창의 폼 안에 있어 type 을 주지 않으면 저장(submit)까지 함께 실행된다 */}
              <Button
                type="button"
                size="small"
                variant="danger"
                disabled={disabled}
                onClick={() => onDelete(close.exceptionId)}
              >
                닫기 지우기
              </Button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
