import { datePart, timePart } from '@/utils/scheduleDate';
import { formatDayLabel } from '@/utils/scheduleSlots';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import EmptyState from '@/components/common/EmptyState';

const TYPE_BADGES = {
  OPEN: ['열기', 'b-green'],
  CLOSE: ['닫기', 'b-neutral'],
};

/**
 * 등록한 빈자리 열기·닫기 목록 (오늘부터 60일). 날짜나 수정을 누르면 등록 탭이 그 날짜·일정으로 열린다.
 */
export default function ScheduleExceptionList({
  exceptions,
  editingId,
  disabled,
  onSelectDate,
  onEdit,
  onDelete,
}) {
  return (
    <Panel as="section" className="b-table-panel">
      <div className="b-pad">
        <h2>
          열기·닫기 일정 <small className="b-muted">(오늘부터 60일, {exceptions.length}건)</small>
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
              const date = datePart(exception.startsAt);
              return (
                <tr
                  key={exception.exceptionId}
                  className={exception.exceptionId === editingId ? 'is-active' : ''}
                >
                  <td>
                    <Badge className={badge}>{label}</Badge>
                  </td>
                  <td>
                    <Button size="small" type="button" onClick={() => onSelectDate(date)}>
                      {formatDayLabel(date)}
                    </Button>
                  </td>
                  <td>
                    {timePart(exception.startsAt)} ~ {timePart(exception.endsAt)}
                  </td>
                  <td>{exception.reason ?? '-'}</td>
                  <td>
                    <div className="b-flex">
                      <Button
                        size="small"
                        type="button"
                        disabled={disabled}
                        onClick={() => onEdit(exception)}
                      >
                        수정
                      </Button>
                      <Button
                        size="small"
                        type="button"
                        disabled={disabled}
                        onClick={() => onDelete(exception)}
                      >
                        삭제
                      </Button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {!exceptions.length && <EmptyState as="p">등록된 열기·닫기 일정이 없습니다.</EmptyState>}
    </Panel>
  );
}
