import Alert from '@/components/common/Alert';
import motion from './OwnerMotion.module.css';

/**
 * 빈자리 열기·닫기 화면 위쪽 안내. 저장·삭제 결과와, 변경은 됐는데 목록을 다시 못 받은 경우를 알린다.
 */
export default function VacancyNotices({ refreshFailed, notice }) {
  return (
    <>
      {refreshFailed && (
        <Alert as="p" tone="error" role="alert" className={motion.enter}>
          변경은 완료됐지만 목록을 새로 불러오지 못했습니다. 페이지를 새로고침해 주세요.
        </Alert>
      )}
      {notice && (
        <Alert
          key={notice.text}
          as="p"
          tone={notice.tone}
          role={notice.tone === 'success' ? 'status' : 'alert'}
          className={motion.enter}
        >
          {notice.text}
        </Alert>
      )}
    </>
  );
}
