import { useEffect } from 'react';
import { useScheduleStore } from '@/store/useScheduleStore';
import BusinessHoursPanel from '@/pages/owner/components/BusinessHoursPanel';
import Button from '@/components/common/Button';

/**
 * 사장님 요일별 영업시간 관리 (B-F2). 특정 날짜의 시간대 열기·닫기는 빈자리 열기·닫기 화면에서 한다.
 */
export default function SchedulePage() {
  const loadSchedule = useScheduleStore((state) => state.loadSchedule);
  const status = useScheduleStore((state) => state.status);
  const error = useScheduleStore((state) => state.error);
  const businessHours = useScheduleStore((state) => state.businessHours);

  useEffect(() => {
    loadSchedule();
  }, [loadSchedule]);

  return (
    <div className="b-stack">
      <div className="b-title-row">
        <h1>영업시간</h1>
        <p className="b-muted">
          요일마다 문 여는 시간을 정합니다. 고객은 이 시간 안에서 시술 소요 시간만큼 비어 있는
          시각을 예약합니다.
        </p>
      </div>
      {(status === 'idle' || status === 'loading') && (
        <p role="status">영업시간을 불러오는 중입니다.</p>
      )}
      {status === 'failed' && (
        <div role="alert">
          <p>{error.message}</p>
          <Button onClick={loadSchedule}>다시 불러오기</Button>
        </div>
      )}
      {status === 'succeeded' && <BusinessHoursPanel hours={businessHours} />}
    </div>
  );
}
