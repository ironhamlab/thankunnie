import { useState } from 'react';
import { useScheduleStore } from '@/store/useScheduleStore';
import { datePart, timePart, toKstDateTime } from '@/utils/scheduleDate';
import Dialog from '@/components/common/Dialog';

const TYPE_LABELS = {
  OPEN: '열기 (영업시간 밖 추가 영업)',
  CLOSE: '닫기 (임시 휴무·예약 받지 않음)',
};

const toForm = (exception, date) =>
  exception
    ? {
        exceptionType: exception.exceptionType,
        date: datePart(exception.startsAt),
        startTime: timePart(exception.startsAt),
        endTime: timePart(exception.endsAt),
        reason: exception.reason ?? '',
      }
    : { exceptionType: 'CLOSE', date, startTime: '12:00', endTime: '13:00', reason: '' };

/**
 * 시간대 열기·닫기 등록·수정. exception 이 있으면 수정, 없으면 등록한다.
 */
export default function ScheduleExceptionDialog({ exception, date, minDate, maxDate, onClose }) {
  const saveException = useScheduleStore((state) => state.saveException);
  const isSaving = useScheduleStore((state) => state.mutationStatus === 'loading');
  const [form, setForm] = useState(() => toForm(exception, date));
  const [errorMessage, setErrorMessage] = useState('');

  const handleChange = (event) => {
    const { name, value } = event.target;
    setErrorMessage('');
    setForm((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    // HH:mm 문자열은 사전순 비교가 시각 순서와 같다
    if (form.endTime <= form.startTime) {
      setErrorMessage('종료 시각은 시작 시각보다 뒤여야 합니다.');
      return;
    }
    try {
      await saveException(exception?.exceptionId, {
        exceptionType: form.exceptionType,
        startsAt: toKstDateTime(form.date, form.startTime),
        endsAt: toKstDateTime(form.date, form.endTime),
        reason: form.reason.trim() || null,
      });
      onClose(true);
    } catch (error) {
      setErrorMessage(error.message);
    }
  };

  return (
    <Dialog title={exception ? '시간대 열기·닫기 수정' : '시간대 열기·닫기 등록'} onClose={onClose}>
      <form className="b-form-grid" onSubmit={handleSubmit} aria-busy={isSaving}>
        <fieldset className="b-span-two b-options">
          <legend>구분 *</legend>
          {Object.entries(TYPE_LABELS).map(([type, label]) => (
            <label key={type} className={form.exceptionType === type ? 'is-selected' : ''}>
              <input
                type="radio"
                name="exceptionType"
                value={type}
                checked={form.exceptionType === type}
                onChange={handleChange}
              />
              {label}
            </label>
          ))}
        </fieldset>
        <label className="b-span-two">
          날짜 *
          <input
            type="date"
            name="date"
            required
            min={minDate}
            max={maxDate}
            value={form.date}
            onChange={handleChange}
          />
        </label>
        <label>
          시작 시각 *
          <input
            type="time"
            name="startTime"
            step="1800"
            required
            value={form.startTime}
            onChange={handleChange}
          />
        </label>
        <label>
          종료 시각 *
          <input
            type="time"
            name="endTime"
            step="1800"
            required
            value={form.endTime}
            onChange={handleChange}
          />
          <small>30분 단위, 종료는 시작보다 뒤</small>
        </label>
        <label className="b-span-two">
          사유 (선택)
          <input
            name="reason"
            maxLength={500}
            placeholder="예: 원장님 개인 일정"
            value={form.reason}
            onChange={handleChange}
          />
        </label>
        <p className="b-help b-span-two">
          열기는 시작 30분 전까지 등록할 수 있고, 예약이 있는 시간은 닫을 수 없습니다.
        </p>
        {errorMessage && (
          <p role="alert" className="b-error b-span-two">
            {errorMessage}
          </p>
        )}
        <button className="b-button b-primary b-span-two" disabled={isSaving}>
          {exception ? '수정 저장' : '등록하기'}
        </button>
      </form>
    </Dialog>
  );
}
