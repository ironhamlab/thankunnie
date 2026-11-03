import { useState } from 'react';
import { Link } from 'react-router-dom';
import Dialog from '@/components/common/Dialog';
import LegalDocument from '@/components/common/LegalDocument';
import { KAKAO_OPEN_CHAT_URL } from '@/constants/customerSupport';
import { PRIVACY_POLICY, TERMS_OF_SERVICE } from '@/constants/legalDocuments';
import { ROUTES } from '@/constants/routes';
import Button from '@/components/common/Button';
import FaqTabs from './FaqTabs';
import styles from './Footer.module.css';

const FAQ = '자주 묻는 질문';
const CUSTOMER_CENTER = '고객센터';
const LEGAL_DOCUMENTS = [TERMS_OF_SERVICE, PRIVACY_POLICY];
const FOOTER_NOTICES = [FAQ, CUSTOMER_CENTER, ...LEGAL_DOCUMENTS.map((doc) => doc.title)];

/**
 * 고객 화면 공통 푸터
 */
export default function Footer() {
  const [notice, setNotice] = useState('');
  const legalDocument = LEGAL_DOCUMENTS.find((doc) => doc.title === notice);

  return (
    <>
      <footer className={`b-footer ${styles.root}`}>
        <div className="b-container b-row">
          <div>
            <Link className="b-brand" to={ROUTES.HOME}>
              땡잡은 언니
            </Link>
            <p>© 2026 땡잡은 언니</p>
          </div>
          <div className={styles.links}>
            {FOOTER_NOTICES.map((label) => (
              <button key={label} onClick={() => setNotice(label)}>
                {label}
              </button>
            ))}
          </div>
        </div>
      </footer>
      {notice === FAQ && (
        <Dialog title={FAQ} onClose={() => setNotice('')}>
          <FaqTabs />
        </Dialog>
      )}
      {notice === CUSTOMER_CENTER && (
        <Dialog title={CUSTOMER_CENTER} onClose={() => setNotice('')}>
          <p>궁금하신 점이나 문의 사항이 있으시면 카카오톡으로 편하게 문의해 주세요.</p>
          <Button
            as="a"
            variant="primary"
            wide
            href={KAKAO_OPEN_CHAT_URL}
            target="_blank"
            rel="noreferrer"
          >
            카카오톡 문의하기
          </Button>
        </Dialog>
      )}
      {legalDocument && (
        <Dialog title={legalDocument.title} onClose={() => setNotice('')}>
          <LegalDocument content={legalDocument} />
        </Dialog>
      )}
    </>
  );
}
