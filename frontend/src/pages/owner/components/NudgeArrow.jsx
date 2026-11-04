import styles from './NudgeArrow.module.css';

/**
 * 링크·버튼 글자 뒤의 화살표. 마우스를 올리면 갈 방향으로 까딱인다.
 */
export default function NudgeArrow() {
  return (
    <span className={styles.arrow} aria-hidden="true">
      →
    </span>
  );
}
