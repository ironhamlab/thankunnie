import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useScheduleStore } from '@/store/useScheduleStore';
import { addDays, datePart, timePart, todayKst } from '@/utils/scheduleDate';
import AvailableTimesPanel from './AvailableTimesPanel';
import BusinessHoursPanel from './BusinessHoursPanel';
import ScheduleExceptionDialog from './ScheduleExceptionDialog';

// 열기·닫기는 오늘 포함 60일 안에서만 등록할 수 있다.
const BOOKING_MAX_DAYS = 60;
const TYPE_BADGES = {
  OPEN: ['열기', 'b-green'],
  CLOSE: ['닫기', 'b-neutral'],
};

/**
 * 사장님 영업시간·시간대 열기·닫기 관리 (B-08).
 * 예약 가능 시간 = 영업시간 + 열기 - 닫기 - 예약. 시술별 빈자리를 따로 등록하지 않는다.
 */
export default function SchedulePage() {
  const loadSchedule = useScheduleStore((state) => state.loadSchedule);
  const status = useScheduleStore((state) => state.status);
  const error = useScheduleStore((state) => state.error);
  const businessHours = useScheduleStore((state) => state.businessHours);
  const exceptions = useScheduleStore((state) => state.exceptions);
  const deleteException = useScheduleStore((state) => state.deleteException);
  const refreshFailed = useScheduleStore((state) => state.refreshFailed);
  const [params, setParams] = useSearchParams();
  const today = todayKst();
  const lastDate = addDays(today, BOOKING_MAX_DAYS - 1);
  const [selectedDate, setSelectedDate] = useState(today);
  const [editing, setEditing] = useState(null);
  const [notice, setNotice] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const isCreating = params.has('new');

  useEffect(() => {
    loadSchedule();
  }, [loadSchedule]);

  const handleDialogClose = (saved) => {
    setEditing(null);
    setParams({});
    if (saved === true) {
      setNotice({ type: 'success', text: '시간대 열기·닫기를 저장했습니다.' });
      setReloadKey((key) => key + 1);
    }
  };

  const handleDelete = async (exception) => {
    const label = `${datePart(exception.startsAt)} ${timePart(exception.startsAt)}~${timePart(exception.endsAt)}`;
    if (!window.confirm(`${label} 일정을 삭제할까요?`)) {
      return;
    }
    try {
      await deleteException(exception.exceptionId);
      setNotice({ type: 'success', text: '일정을 삭제했습니다.' });
      setReloadKey((key) => key + 1);
    } catch (failure) {
      setNotice({ type: 'error', text: failure.message });
    }
  };

  return (
    <div className="b-stack">
      <div className="b-row b-title-row">
        <div>
          <h1>영업시간·일정 관리</h1>
          <p className="b-muted">
            요일별 영업시간을 정하고, 특정 날짜만 시간대를 추가로 열거나 닫습니다. 고객은 이 시간
            안에서 시술 소요 시간만큼 비어 있는 시각을 예약합니다.
          </p>
        </div>
        <button className="b-button b-primary" onClick={() => setParams({ new: '1' })}>
          + 시간대 열기·닫기
        </button>
      </div>
      {(status === 'idle' || status === 'loading') && (
        <p role="status">영업시간과 일정을 불러오는 중입니다.</p>
      )}
      {status === 'failed' && (
        <div role="alert">
          <p>{error.message}</p>
          <button className="b-button" onClick={loadSchedule}>
            다시 불러오기
          </button>
        </div>
      )}
      {status === 'succeeded' && (
        <>
          {refreshFailed && (
            <p className="b-error" role="alert">
              변경은 완료됐지만 목록을 새로 불러오지 못했습니다. 페이지를 새로고침해 주세요.
            </p>
          )}
          {notice && (
            <p
              className={notice.type === 'success' ? 'b-success' : 'b-error'}
              role={notice.type === 'success' ? 'status' : 'alert'}
            >
              {notice.text}
            </p>
          )}
          <div className="b-columns">
            <BusinessHoursPanel
              hours={businessHours}
              onSaved={() => setReloadKey((key) => key + 1)}
            />
            <AvailableTimesPanel
              date={selectedDate}
              minDate={today}
              maxDate={lastDate}
              onDateChange={setSelectedDate}
              reloadKey={reloadKey}
            />
          </div>
          <section className="b-panel b-table-panel">
            <div className="b-pad">
              <h2>
                시간대 열기·닫기{' '}
                <small className="b-muted">(오늘부터 60일, {exceptions.length}건)</small>
              </h2>
            </div>
            <div className="b-table-scroll">
              <table>
                <thead>
                  <tr>
                    {['구분', '날짜', '시간', '사유', '관리'].map((header) => (
                      <th key={header}>{header}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {exceptions.map((exception) => {
                    const [label, badge] = TYPE_BADGES[exception.exceptionType];
                    return (
                      <tr key={exception.exceptionId}>
                        <td>
                          <span className={`b-badge ${badge}`}>{label}</span>
                        </td>
                        <td>
                          <button
                            type="button"
                            className="b-button b-small"
                            onClick={() => setSelectedDate(datePart(exception.startsAt))}
                          >
                            {datePart(exception.startsAt)}
                          </button>
                        </td>
                        <td>
                          {timePart(exception.startsAt)} ~ {timePart(exception.endsAt)}
                        </td>
                        <td>{exception.reason ?? '-'}</td>
                        <td>
                          <div className="b-flex">
                            <button
                              className="b-button b-small"
                              onClick={() => setEditing(exception)}
                            >
                              수정
                            </button>
                            <button
                              className="b-button b-small"
                              onClick={() => handleDelete(exception)}
                            >
                              삭제
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            {!exceptions.length && <p className="b-empty">등록된 열기·닫기 일정이 없습니다.</p>}
          </section>
        </>
      )}
      {status === 'succeeded' && (editing || isCreating) && (
        <ScheduleExceptionDialog
          exception={editing}
          date={selectedDate}
          minDate={today}
          maxDate={lastDate}
          onClose={handleDialogClose}
        />
      )}
    </div>
  );
}
