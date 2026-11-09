import SlidingOptions from './SlidingOptions';

const TABS = [
  ['form', '빈자리 열기·닫기'],
  ['list', '등록한 일정'],
];

/**
 * 빈자리 열기·닫기 화면의 등록·목록 탭. 수정 중이면 등록 탭 이름을 바꿔 지금 하는 일을 알린다.
 */
export default function VacancyTabs({ tab, listCount, editing, onChange }) {
  const options = TABS.map(([key, label]) => ({
    key,
    label:
      key === 'form' && editing
        ? '일정 수정'
        : `${label}${key === 'list' ? ` ${listCount}건` : ''}`,
  }));
  return (
    <SlidingOptions
      tabs
      label="빈자리 열기·닫기 화면"
      options={options}
      selectedKey={tab}
      onSelect={onChange}
    />
  );
}
