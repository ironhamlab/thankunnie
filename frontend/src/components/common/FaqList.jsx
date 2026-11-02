import styles from './FaqList.module.css';

/**
 * 자주 묻는 질문. 분야별로 묶어 보여주고, 질문을 누르면 답이 펼쳐진다.
 *
 * @param groups 분야별 질문 목록 ({ title, items: [{ question, answer }] })
 */
export default function FaqList({ groups }) {
  return (
    <div className={styles.root}>
      {groups.map((group) => (
        <section key={group.title}>
          <h3>{group.title}</h3>
          {group.items.map((item) => (
            <details key={item.question} className={styles.item}>
              <summary>{item.question}</summary>
              <p>{item.answer}</p>
            </details>
          ))}
        </section>
      ))}
    </div>
  );
}
