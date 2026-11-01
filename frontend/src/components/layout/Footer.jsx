import { useState } from 'react';
import { Link } from 'react-router-dom';
import Dialog from '@/components/common/Dialog';
import { ROUTES } from '@/constants/routes';
import Button from '@/components/common/Button';
import styles from './Footer.module.css';

const FOOTER_NOTICES = ['이용약관', '개인정보처리방침', '고객센터', '입점 문의'];

/**
 * 고객 화면 공통 푸터
 */
export default function Footer() {
  const [notice, setNotice] = useState('');

  return (
    <>
      <footer className={`b-footer ${styles.root}`}>
        <div className="b-container b-row">
          <div>
            <Link className="b-brand" to={ROUTES.HOME}>
              땡잡은 언니
            </Link>
            <p>
              © 2025 땡잡은 언니. 본 서비스는 테스트 결제 환경으로, 결제한 금액은 이후 자동으로
              취소됩니다.
            </p>
          </div>
          <div className={styles.links}>
            {FOOTER_NOTICES.map((label) => (
              <button key={label} onClick={() => setNotice(label)}>
                {label}
              </button>
            ))}
            <Link to={ROUTES.OWNER}>사장님 센터</Link>
          </div>
        </div>
      </footer>
      {notice && (
        <Dialog title={notice} onClose={() => setNotice('')}>
          <p>
            현재는 Figma 디자인 시연 화면입니다. 실제 서비스의 {notice} 내용은 운영 정책 확정 후
            연결됩니다.
          </p>
          <Button variant="primary" onClick={() => setNotice('')}>
            닫기
          </Button>
        </Dialog>
      )}
    </>
  );
}
