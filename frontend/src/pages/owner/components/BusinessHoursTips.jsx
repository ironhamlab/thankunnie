import { Link } from 'react-router-dom';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import { ROUTES } from '@/constants/routes';
import styles from './BusinessHoursTips.module.css';

// 영업시간과 빈자리 열기·닫기의 차이를 매장이 자주 헷갈려 저장 화면 옆에서 알려준다
const TIPS = [
  '하루만 연장하거나 쉬는 날은 빈자리 열기·닫기에서 그날만 바꿔 주세요.',
  '예약이 있는 시간이 빠지도록 줄이면 저장되지 않아요.',
];

/**
 * 영업시간 화면 옆 안내. 영업시간과 빈자리의 관계를 알려주고 빈자리 열기·닫기로 보낸다.
 */
export default function BusinessHoursTips() {
  return (
    <Panel as="section" padded className={styles.root} aria-labelledby="business-hours-tips">
      <h2 id="business-hours-tips">알아두세요</h2>
      <ol className={styles.tips}>
        {TIPS.map((tip) => (
          <li key={tip}>{tip}</li>
        ))}
      </ol>
      <Button as={Link} wide to={ROUTES.OWNER_VACANCIES}>
        빈자리 열기·닫기로 가기
      </Button>
    </Panel>
  );
}
