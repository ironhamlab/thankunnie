import styles from './AuthField.module.css';

/**
 * 라벨·입력칸·안내·오류 문구를 묶은 가입·로그인 입력 항목.
 * select 는 as="select" 와 children(option)으로 넘긴다.
 *
 * @param {string} [error] 비어 있지 않으면 오류로 표시하고 aria-invalid 를 켠다
 * @param {string} [hint] 입력 규칙 안내
 */
export default function AuthField({
  as: Control = 'input',
  label,
  name,
  error,
  hint,
  className = '',
  ...props
}) {
  const messageId = `${name}-message`;
  const message = error || hint;

  return (
    <label className={`${styles.root} ${className}`}>
      <span>{label}</span>
      <Control
        name={name}
        aria-invalid={Boolean(error)}
        aria-describedby={message ? messageId : undefined}
        {...props}
      />
      {message && (
        <small id={messageId} className={error ? styles.error : styles.hint}>
          {message}
        </small>
      )}
    </label>
  );
}
