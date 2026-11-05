import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { VACANCY_POLICY as P } from '@/constants/vacancyPolicy';
import { useScheduleStore } from '@/store/useScheduleStore';
import useDaySlots from '@/hooks/useDaySlots';
import { addDays, datePart, timePart, todayKst, toKstDateTime } from '@/utils/scheduleDate';
import { formatDayLabel, hasBookedSlot, unsellableTimes } from '@/utils/scheduleSlots';
import { formOf, minStartOf, newForm } from '@/utils/scheduleForm';
import AvailableTimesPanel from '@/pages/owner/components/AvailableTimesPanel';
import DayReservationsPanel from '@/pages/owner/components/DayReservationsPanel';
import { EXCEPTION_TYPES, MULTI_DAY_EDIT_BLOCKED } from '@/constants/scheduleException';
import ScheduleExceptionForm from '@/pages/owner/components/ScheduleExceptionForm';
import ScheduleExceptionList from '@/pages/owner/components/ScheduleExceptionList';
import VacancyPreviewPanel from '@/pages/owner/components/VacancyPreviewPanel';
import VacancyNotices from '@/pages/owner/components/VacancyNotices';
import VacancyTabs from '@/pages/owner/components/VacancyTabs';
import styles from './VacancyManagePage.module.css';
import motion from './components/OwnerMotion.module.css';

// 지운 일정이 사라지는 효과 시간 (OwnerMotion.module.css 의 .leaving 과 같게 둔다)
const LEAVE_MS = 280;
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * 빈자리 열기·닫기 (B-F3). 열면 그 시간에 할 수 있는 시술이 고객 빈자리 목록에 노출되고, 닫으면 예약을 받지 않는다.
 * 등록 탭은 입력과 그날 상태·고객 노출 미리보기를, 일정 탭은 등록한 일정의 수정·삭제를 보여준다.
 */
export default function VacancyManagePage() {
  const [params, setParams] = useSearchParams();
  // 등록과 목록을 탭으로 나눈다. 새로고침해도 보던 탭이 남게 주소에 둔다
  const tab = params.get('tab') === 'list' ? 'list' : 'form';
  const changeTab = (next) => setParams(next === 'list' ? { tab: 'list' } : {}, { replace: true });
  const loadSchedule = useScheduleStore((state) => state.loadSchedule);
  const scheduleFailed = useScheduleStore((state) => state.status === 'failed');
  const exceptions = useScheduleStore((state) => state.exceptions);
  const businessHours = useScheduleStore((state) => state.businessHours);
  const refreshFailed = useScheduleStore((state) => state.refreshFailed);
  const saveException = useScheduleStore((state) => state.saveException);
  const deleteException = useScheduleStore((state) => state.deleteException);
  // 변경·목록 새로고침 중에는 늦게 끝난 조회가 최신 목록을 덮지 않게 변경 버튼을 막는다
  const isBusy = useScheduleStore(
    (state) => state.mutationStatus === 'loading' || state.isRefreshing,
  );
  const [form, setForm] = useState(() =>
    newForm(params.get('type') === 'CLOSE' ? 'CLOSE' : 'OPEN'),
  );
  const [editingId, setEditingId] = useState(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [notice, setNotice] = useState(null);
  const [leavingId, setLeavingId] = useState(null);
  const { slots, ready, fresh, failed, retry } = useDaySlots(form.date);
  const today = todayKst();
  const lastDate = addDays(today, P.BOOKING_MAX_DAYS - 1);

  // 화면을 열 때마다 새로 받는다. 로그아웃 후 다른 매장으로 들어와도 이전 매장 일정이 남지 않게 한다
  useEffect(() => {
    loadSchedule();
  }, [loadSchedule]);

  const update = (next) => {
    setErrorMessage('');
    setForm((prev) => ({ ...prev, ...next }));
  };

  const resetForm = () => {
    setEditingId(null);
    setForm((prev) => newForm(prev.exceptionType, prev.date));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    // 새로 닫을 때 예약이 있는 칸이 섞여 있으면 폼이 안내한 대로 예약 시간을 뺀 나머지만 닫는다
    const excludeReserved =
      !editingId &&
      form.exceptionType === 'CLOSE' &&
      hasBookedSlot(slots, form.startTime, form.endTime);
    try {
      const saved = await saveException(editingId, {
        exceptionType: form.exceptionType,
        startsAt: toKstDateTime(form.date, form.startTime),
        endsAt: toKstDateTime(form.date, form.endTime),
        reason: form.reason.trim() || null,
        excludeReserved,
      });
      const label = `${formatDayLabel(form.date)} ${form.startTime}~${form.endTime}`;
      let text = `${label} ${EXCEPTION_TYPES[form.exceptionType].verb}를 저장했습니다.`;
      if (editingId) text = `${label} 일정을 수정했습니다.`;
      // 그사이 예약이 풀렸으면 고른 구간이 그대로 닫힌다. 서버가 나눠 저장했을 때만 예약을 뺐다고 알린다
      const splitAround =
        excludeReserved &&
        (timePart(saved.startsAt) !== form.startTime || timePart(saved.endsAt) !== form.endTime);
      if (splitAround) text = `${label}에서 예약 시간을 뺀 나머지를 닫았습니다.`;
      setNotice({ tone: 'success', text });
      resetForm();
    } catch (error) {
      setErrorMessage(error.message);
    }
  };

  // 겹침 안내의 닫기 지우기. 실패하면 저장 버튼 위에 이유를 보여준다
  const handleDeleteClose = (exceptionId) => {
    // 다시 시도해 성공하면 이전 실패 안내가 남지 않게 먼저 지운다
    setErrorMessage('');
    return deleteException(exceptionId).catch((error) => setErrorMessage(error.message));
  };

  // 목록에서 날짜를 고르면 새 등록으로 시작한다. 수정 중이던 일정이 다른 날짜로 바뀌지 않게 한다
  const handleSelectDate = (date) => {
    setEditingId(null);
    setErrorMessage('');
    setForm((prev) => newForm(prev.exceptionType, date));
    changeTab('form');
  };

  const handleEdit = (exception) => {
    // 이 화면은 하루 안 일정만 다룬다. 여러 날에 걸친 일정을 하루로 줄여 저장하지 않게 막는다
    if (datePart(exception.startsAt) !== datePart(exception.endsAt)) {
      setNotice({ tone: 'error', text: MULTI_DAY_EDIT_BLOCKED });
      return;
    }
    setErrorMessage('');
    changeTab('form');
    setEditingId(exception.exceptionId);
    setForm(formOf(exception));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleDelete = async (exception) => {
    const { date, startTime, endTime } = formOf(exception);
    if (!window.confirm(`${formatDayLabel(date)} ${startTime}~${endTime} 일정을 삭제할까요?`)) {
      return;
    }
    // 사라지는 효과가 끝난 뒤 지운다. 실패하면 효과를 풀어 일정이 다시 보이게 한다
    setLeavingId(exception.exceptionId);
    await wait(window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : LEAVE_MS);
    try {
      await deleteException(exception.exceptionId);
      setNotice({ tone: 'success', text: '일정을 삭제했습니다.' });
      if (exception.exceptionId === editingId) {
        resetForm();
      }
    } catch (failure) {
      setNotice({ tone: 'error', text: failure.message });
    } finally {
      setLeavingId(null);
    }
  };

  return (
    <div className={`b-stack ${styles.root}`}>
      <div className="b-title-row">
        <h1>빈자리 열기·닫기</h1>
        <p className="b-muted">
          손님을 더 받을 시간을 열면 그 시간에 할 수 있는 시술이 고객 빈자리 목록에 바로 올라가고,
          닫으면 예약을 받지 않습니다. 시간이 임박할수록 초임박가로 노출됩니다.
        </p>
      </div>
      <VacancyNotices refreshFailed={refreshFailed} notice={notice} />
      <VacancyTabs
        tab={tab}
        listCount={exceptions.length}
        editing={Boolean(editingId)}
        onChange={changeTab}
      />
      {tab === 'form' && (
        <div className={`${styles.columns} ${motion.step}`}>
          <ScheduleExceptionForm
            form={form}
            editingId={editingId}
            slots={slots}
            ready={ready}
            fresh={fresh}
            failed={failed || scheduleFailed}
            onRetry={scheduleFailed ? loadSchedule : retry}
            exceptions={exceptions}
            businessHours={businessHours}
            minStart={minStartOf(form.exceptionType, form.date)}
            minDate={today}
            maxDate={lastDate}
            isSaving={isBusy || leavingId !== null}
            errorMessage={errorMessage}
            onChange={update}
            onSubmit={handleSubmit}
            onCancelEdit={resetForm}
            onDeleteClose={handleDeleteClose}
          />
          <aside className={styles.side}>
            <AvailableTimesPanel
              className={styles.timesPanel}
              date={form.date}
              minDate={today}
              maxDate={lastDate}
              onDateChange={(date) => update({ date })}
            />
            <DayReservationsPanel date={form.date} />
            {form.exceptionType === 'OPEN' && (
              <VacancyPreviewPanel
                date={form.date}
                startTime={form.startTime}
                endTime={form.endTime}
                blockedTimes={unsellableTimes(slots)}
              />
            )}
          </aside>
        </div>
      )}
      {tab === 'list' && (
        <ScheduleExceptionList
          className={motion.step}
          exceptions={exceptions}
          businessHours={businessHours}
          editingId={editingId}
          leavingId={leavingId}
          disabled={isBusy || leavingId !== null}
          onSelectDate={handleSelectDate}
          onEdit={handleEdit}
          onDelete={handleDelete}
        />
      )}
    </div>
  );
}
