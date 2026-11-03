import styles from './LegalDocument.module.css';

/**
 * 이용약관·개인정보처리방침 본문.
 *
 * @param content 조항 목록 ({ sections: [{ heading, paragraphs, items }] })
 */
export default function LegalDocument({ content }) {
  return (
    <div className={styles.root}>
      {content.sections.map((section) => (
        <section key={section.heading}>
          <h3>{section.heading}</h3>
          {section.paragraphs?.map((paragraph) => (
            <p key={paragraph}>{paragraph}</p>
          ))}
          {section.items && (
            <ul>
              {section.items.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          )}
        </section>
      ))}
    </div>
  );
}
