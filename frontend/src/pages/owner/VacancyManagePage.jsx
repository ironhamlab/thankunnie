import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { VACANCY_POLICY as P } from '@/constants/vacancyPolicy';
import { useScheduleStore } from '@/store/useScheduleStore';
import { useTreatmentStore } from '@/store/useTreatmentStore';
import useDaySlots from '@/hooks/useDaySlots';
import { addDays, todayKst, toKstDateTime } from '@/utils/scheduleDate';
import { formatDayLabel } from '@/utils/scheduleSlots';
import { formOf, minStartOf, newForm } from '@/utils/scheduleForm';
import AvailableTimesPanel from '@/pages/owner/components/AvailableTimesPanel';
import DayReservationsPanel from '@/pages/owner/components/DayReservationsPanel';
import { EXCEPTION_TYPES } from '@/constants/scheduleException';
import ScheduleExceptionForm from '@/pages/owner/components/ScheduleExceptionForm';
import ScheduleExceptionList from '@/pages/owner/components/ScheduleExceptionList';
import VacancyPreviewPanel from '@/pages/owner/components/VacancyPreviewPanel';
import VacancyTabs from '@/pages/owner/components/VacancyTabs';
import Alert from '@/components/common/Alert';
import styles from './VacancyManagePage.module.css';

/**
 * 빈자리 열기·닫기 (B-F3). 시간대를 열면 그 안에서 할 수 있는 시술이 고객 빈자리 목록에 노출되고,
 * 닫으면 예약을 받지 않는다. 왼쪽에서 입력하고, 오른쪽에서 그날 예약 가능 시간과(열기면) 고객 화면을 미리 본다.
 * 아래 목록에서 등록한 일정을 수정·삭제한다.
 */
export default function VacancyManagePage() {
  const [params, setParams] = useSearchParams();
  // 등록과 목록을 탭으로 나눈다. 새로고침해도 보던 탭이 남게 주소에 둔다
  const tab = params.get('tab') === 'list' ? 'list' : 'form';
  const changeTab = (next) => setParams(next === 'list' ? { tab: 'list' } : {}, { replace: true });
  const exceptions = useScheduleStore((state) => state.exceptions);
  const refreshFailed = useScheduleStore((state) => state.refreshFailed);
  const saveException = useScheduleStore((state) => state.saveException);
  const deleteException = useScheduleStore((state) => state.deleteException);
  // 변경·목록 새로고침 중에는 늦게 끝난 조회가 최신 목록을 덮지 않게 변경 버튼을 막는다
  const isBusy = useScheduleStore(
    (state) => state.mutationStatus === 'loading' || state.isRefreshing,
  );
  const loadActiveTreatments = useTreatmentStore((state) => state.loadActiveTreatments);
  const treatments = useTreatmentStore((state) => state.activeTreatments);
  const treatmentStatus = useTreatmentStore((state) => state.selectionStatus);
  const treatmentError = useTreatmentStore((state) => state.selectionError);
  const [form, setForm] = useState(() =>
    newForm(params.get('type') === 'CLOSE' ? 'CLOSE' : 'OPEN'),
  );
  const [editingId, setEditingId] = useState(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [notice, setNotice] = useState(null);
  const { slots, ready } = useDaySlots(form.date);
  const today = todayKst();
  const lastDate = addDays(today, P.BOOKING_MAX_DAYS - 1);
  // 닫았거나 예약이 있는 칸은 열어도 팔 수 없어 미리보기에서 뺀다
  const blockedTimes = new Set(
    slots.filter((slot) => ['CLOSED', 'BOOKED'].includes(slot.status)).map((slot) => slot.time),
  );

  useEffect(() => {
    // 실패는 store 의 selectionStatus·selectionError 로 미리보기에 보여준다
    loadActiveTreatments().catch(() => {});
  }, [loadActiveTreatments]);

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
    try {
      await saveException(editingId, {
        exceptionType: form.exceptionType,
        startsAt: toKstDateTime(form.date, form.startTime),
        endsAt: toKstDateTime(form.date, form.endTime),
        reason: form.reason.trim() || null,
      });
      const label = `${formatDayLabel(form.date)} ${form.startTime}~${form.endTime}`;
      setNotice({
        tone: 'success',
        text: editingId
          ? `${label} 일정을 수정했습니다.`
          : `${label} ${EXCEPTION_TYPES[form.exceptionType].verb}를 저장했습니다.`,
      });
      resetForm();
    } catch (error) {
      setErrorMessage(error.message);
    }
  };

  const handleEdit = (exception) => {
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
    try {
      await deleteException(exception.exceptionId);
      setNotice({ tone: 'success', text: '일정을 삭제했습니다.' });
      if (exception.exceptionId === editingId) {
        resetForm();
      }
    } catch (failure) {
      setNotice({ tone: 'error', text: failure.message });
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
      {refreshFailed && (
        <Alert as="p" tone="error" role="alert">
          변경은 완료됐지만 목록을 새로 불러오지 못했습니다. 페이지를 새로고침해 주세요.
        </Alert>
      )}
      {notice && (
        <Alert as="p" tone={notice.tone} role={notice.tone === 'success' ? 'status' : 'alert'}>
          {notice.text}
        </Alert>
      )}
      <VacancyTabs
        tab={tab}
        listCount={exceptions.length}
        editing={Boolean(editingId)}
        onChange={changeTab}
      />
      {tab === 'form' && (
        <div className={styles.columns}>
          <ScheduleExceptionForm
            form={form}
            editingId={editingId}
            slots={slots}
            ready={ready}
            exceptions={exceptions}
            minStart={minStartOf(form.exceptionType, form.date)}
            minDate={today}
            maxDate={lastDate}
            isSaving={isBusy}
            errorMessage={errorMessage}
            onChange={update}
            onSubmit={handleSubmit}
            onCancelEdit={resetForm}
            onDeleteClose={deleteException}
          />
          <aside className={styles.side}>
            <AvailableTimesPanel
              date={form.date}
              minDate={today}
              maxDate={lastDate}
              onDateChange={(date) => update({ date })}
            />
            <DayReservationsPanel date={form.date} />
            {form.exceptionType === 'OPEN' && (
              <VacancyPreviewPanel
                treatments={treatments}
                status={treatmentStatus}
                error={treatmentError}
                date={form.date}
                startTime={form.startTime}
                endTime={form.endTime}
                blockedTimes={blockedTimes}
              />
            )}
          </aside>
        </div>
      )}
      {tab === 'list' && (
        <ScheduleExceptionList
          exceptions={exceptions}
          editingId={editingId}
          disabled={isBusy}
          onSelectDate={(date) => {
            update({ date });
            changeTab('form');
          }}
          onEdit={handleEdit}
          onDelete={handleDelete}
        />
      )}
    </div>
  );
}
