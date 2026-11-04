import Panel from '@/components/common/Panel';
import styles from './AuthCard.module.css';

/**
 * 로그인·가입 화면 공통 틀. 가운데 정렬된 패널에 제목·설명과 본문을 둔다.
 *
 * @param {'narrow'|'wide'} [width] narrow: 로그인·고객 가입, wide: 매장 가입
 */
export default function AuthCard({ title, description, width = 'narrow', children }) {
  return (
    <div className="b-container b-page">
      <Panel as="section" padded className={`${styles.root} ${styles[width]}`}>
        <header className={styles.heading}>
          <h1>{title}</h1>
          {description && <p className="b-muted">{description}</p>}
        </header>
        {children}
      </Panel>
    </div>
  );
}
