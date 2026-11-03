import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';

/**
 * 매장 가입 승인 상태 안내 팝업. 가입 신청 직후와 승인 전·거절 매장의 로그인 시도에 띄운다.
 *
 * @param {string} title   팝업 제목
 * @param {string[]} lines 안내 문구 (한 줄씩 표시)
 * @param {() => void} onClose 확인·닫기
 */
export default function ShopApprovalDialog({ title, lines, onClose }) {
  return (
    <Dialog title={title} onClose={onClose}>
      {lines.map((line) => (
        <p key={line}>{line}</p>
      ))}
      <Button variant="primary" wide onClick={onClose}>
        확인
      </Button>
    </Dialog>
  );
}
