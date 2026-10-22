import styles from './Spinner.module.css';

/**
 * 로딩 표시
 */
export default function Spinner() {
  return <div className={styles.spinner} role="status" aria-label="로딩 중" />;
}
