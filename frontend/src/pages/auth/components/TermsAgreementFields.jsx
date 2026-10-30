import { useId, useState } from 'react';
import { TERMS_CONTENT } from '@/constants/terms';
import styles from './TermsAgreementFields.module.css';

/**
 * 회원가입 약관 동의 체크박스. 전체 동의와 약관별 동의, 약관별 본문 펼쳐 보기를 제공한다.
 *
 * @param {{ code: string, title: string, required: boolean }[]} terms 화면에 보일 약관 (서버 기준 필수 여부)
 * @param {string[]} agreed 동의한 약관 코드
 * @param {(codes: string[]) => void} onChange 동의 목록이 바뀌면 호출
 * @param {string} [error] 필수 약관 누락 등 오류 문구
 */
export default function TermsAgreementFields({ terms, agreed, onChange, error }) {
  const [openCode, setOpenCode] = useState(null);
  const errorId = useId();
  const allAgreed = terms.length > 0 && terms.every((item) => agreed.includes(item.code));

  const handleAllChange = (event) => {
    onChange(event.target.checked ? terms.map((item) => item.code) : []);
  };

  const handleItemChange = (code, checked) => {
    onChange(checked ? [...agreed, code] : agreed.filter((item) => item !== code));
  };

  return (
    <fieldset
      className={styles.root}
      aria-invalid={Boolean(error)}
      aria-describedby={error ? errorId : undefined}
    >
      <legend>약관 동의</legend>
      <label className={styles.all}>
        <input type="checkbox" checked={allAgreed} onChange={handleAllChange} />
        <span>전체 동의 (선택 항목 포함)</span>
      </label>
      <ul className={styles.list}>
        {terms.map((item) => {
          const content = TERMS_CONTENT[item.code];
          const isOpen = openCode === item.code;
          const bodyId = `terms-body-${item.code}`;
          return (
            <li key={item.code} className={styles.item}>
              <div className={styles.itemRow}>
                <label className={styles.itemLabel}>
                  <input
                    type="checkbox"
                    checked={agreed.includes(item.code)}
                    onChange={(event) => handleItemChange(item.code, event.target.checked)}
                  />
                  <span>
                    <strong className={item.required ? styles.isRequired : styles.isOptional}>
                      [{item.required ? '필수' : '선택'}]
                    </strong>{' '}
                    {item.title}
                  </span>
                </label>
                {content && (
                  <button
                    type="button"
                    className={styles.toggle}
                    aria-expanded={isOpen}
                    aria-controls={bodyId}
                    onClick={() => setOpenCode(isOpen ? null : item.code)}
                  >
                    {isOpen ? '닫기' : '보기'}
                  </button>
                )}
              </div>
              {content && isOpen && (
                <p id={bodyId} className={styles.body}>
                  {content.body}
                </p>
              )}
            </li>
          );
        })}
      </ul>
      {error && (
        <small id={errorId} className={styles.error}>
          {error}
        </small>
      )}
    </fieldset>
  );
}
