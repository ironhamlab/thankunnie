import { useState } from 'react';
import { useScheduleStore } from '@/store/useScheduleStore';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import Alert from '@/components/common/Alert';

const DAY_LABELS = {
  MON: '월',
  TUE: '화',
  WED: '수',
  THU: '목',
  FRI: '금',
  SAT: '토',
  SUN: '일',
};
const DEFAULT_OPEN = '10:00';
const DEFAULT_CLOSE = '20:00';

/**
 * 요일별 영업시간·휴무 설정. 예약이 걸린 시간을 줄이면 서버가 거절한다.
 * 저장하면 예약 가능 시간이 바뀌므로 onSaved 로 부모에게 알린다.
 */
export default function BusinessHoursPanel({ hours, onSaved }) {
  const saveBusinessHours = useScheduleStore((state) => state.saveBusinessHours);
  const isSaving = useScheduleStore((state) => state.mutationStatus === 'loading');
  const [days, setDays] = useState(hours);
  const [message, setMessage] = useState(null);

  const handleDayChange = (dayOfWeek, key, value) => {
    setMessage(null);
    setDays((prev) =>
      prev.map((day) => (day.dayOfWeek === dayOfWeek ? { ...day, [key]: value } : day)),
    );
  };

  const handleClosedChange = (day, closed) => {
    setMessage(null);
    setDays((prev) =>
      prev.map((item) =>
        item.dayOfWeek === day.dayOfWeek
          ? {
              ...item,
              closed,
              openTime: item.openTime ?? DEFAULT_OPEN,
              closeTime: item.closeTime ?? DEFAULT_CLOSE,
            }
          : item,
      ),
    );
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    // HH:mm 문자열은 사전순 비교가 시각 순서와 같다
    const invalidDay = days.find((day) => !day.closed && day.closeTime <= day.openTime);
    if (invalidDay) {
      setMessage({
        type: 'error',
        text: `${DAY_LABELS[invalidDay.dayOfWeek]}요일 영업 종료는 시작보다 뒤여야 합니다.`,
      });
      return;
    }
    const payload = days.map(({ dayOfWeek, closed, openTime, closeTime }) => ({
      dayOfWeek,
      closed,
      openTime: closed ? null : openTime,
      closeTime: closed ? null : closeTime,
    }));
    try {
      await saveBusinessHours(payload);
      setMessage({ type: 'success', text: '영업시간을 저장했습니다.' });
      onSaved?.();
    } catch (error) {
      setMessage({ type: 'error', text: error.message });
    }
  };

  return (
    <Panel as="section" padded>
      <div className="b-row b-panel-heading">
        <h2>요일별 영업시간</h2>
        <small className="b-muted">30분 단위 · 자정을 넘기는 영업은 지원하지 않습니다</small>
      </div>
      <form className="b-stack" onSubmit={handleSubmit} aria-busy={isSaving}>
        <div className="b-table-scroll">
          <table>
            <thead>
              <tr>
                <th>요일</th>
                <th>휴무</th>
                <th>영업 시작</th>
                <th>영업 종료</th>
              </tr>
            </thead>
            <tbody>
              {days.map((day) => (
                <tr key={day.dayOfWeek}>
                  <td>
                    <strong>{DAY_LABELS[day.dayOfWeek]}</strong>
                  </td>
                  <td>
                    <input
                      type="checkbox"
                      aria-label={`${DAY_LABELS[day.dayOfWeek]}요일 휴무`}
                      checked={day.closed}
                      onChange={(event) => handleClosedChange(day, event.target.checked)}
                    />
                  </td>
                  <td>
                    <input
                      type="time"
                      step="1800"
                      required={!day.closed}
                      disabled={day.closed}
                      aria-label={`${DAY_LABELS[day.dayOfWeek]}요일 영업 시작`}
                      value={day.openTime ?? ''}
                      onChange={(event) =>
                        handleDayChange(day.dayOfWeek, 'openTime', event.target.value)
                      }
                    />
                  </td>
                  <td>
                    <input
                      type="time"
                      step="1800"
                      required={!day.closed}
                      disabled={day.closed}
                      aria-label={`${DAY_LABELS[day.dayOfWeek]}요일 영업 종료`}
                      value={day.closeTime ?? ''}
                      onChange={(event) =>
                        handleDayChange(day.dayOfWeek, 'closeTime', event.target.value)
                      }
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {message && (
          <Alert
            as="p"
            tone={message.type === 'success' ? 'success' : 'error'}
            role={message.type === 'success' ? 'status' : 'alert'}
          >
            {message.text}
          </Alert>
        )}
        <Button variant="primary" disabled={isSaving}>
          영업시간 저장
        </Button>
      </form>
    </Panel>
  );
}
