import {
  getDevTestUser,
  isDevTestUserEnabled,
  setDevTestUser,
  TEST_USERS,
} from '@/utils/devTestUser';
import styles from './DevTestUserSwitch.module.css';

/**
 * 개발용 테스트 계정 전환 스위치. 개발 서버에서만 보이며, 바꾸면 새 계정으로 화면을 다시 불러온다.
 */
export default function DevTestUserSwitch() {
  if (!isDevTestUserEnabled) {
    return null;
  }

  const current = getDevTestUser() ?? '';

  const handleChange = (event) => {
    setDevTestUser(event.target.value);
    window.location.reload();
  };

  return (
    <div className={styles.devTestUserSwitch}>
      <label>
        <span className={styles.label}>테스트 계정</span>
        <select value={current} onChange={handleChange} aria-label="테스트 계정 전환">
          {TEST_USERS.map((user) => (
            <option key={user.value || 'anonymous'} value={user.value}>
              {user.label}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}
