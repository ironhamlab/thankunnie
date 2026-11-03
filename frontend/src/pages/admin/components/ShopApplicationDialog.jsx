import { useState } from 'react';
import {
  SHOP_REJECT_REASON_MAX_LENGTH,
  SHOP_REJECT_REASONS,
  SHOP_REVIEW_GUIDE,
} from '@/constants/shopApplication';
import { useAdminShopStore } from '@/store/useAdminShopStore';
import { formatDateTime } from '@/utils/reservationFormat';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import styles from './ShopApplicationDialog.module.css';

/**
 * 가입 신청 상세. 상태에 맞는 처리를 고르고, 실수를 줄이도록 한 번 더 확인한 뒤 저장한다.
 * 승인 대기는 승인·거절, 승인은 승인 취소, 거절은 거절 취소를 할 수 있다.
 *
 * @param application 고른 신청
 * @param onClose     창 닫기
 * @param onDone      처리 완료. 안내 문구를 넘긴다
 */
export default function ShopApplicationDialog({ application, onClose, onDone }) {
  const approve = useAdminShopStore((state) => state.approve);
  const reject = useAdminShopStore((state) => state.reject);
  const cancelApproval = useAdminShopStore((state) => state.cancelApproval);
  const cancelRejection = useAdminShopStore((state) => state.cancelRejection);
  const isSaving = useAdminShopStore((state) => state.isSaving);
  // 지금 확인 중인 처리. null 이면 상세만 보여준다
  const [action, setAction] = useState(null);
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const { shopId, name, status } = application;
  const rows = [
    ['매장명', name],
    ['업종', application.categoryName ?? '-'],
    ['주소', [application.address, application.addressDetail].filter(Boolean).join(' ')],
    ['연락처', application.phone],
    ['아이디', application.loginId],
    ['신청일', formatDateTime(application.appliedAt)],
    ['매장 소개', application.description || '-'],
  ];
  const ACTIONS = {
    approve: {
      label: '승인',
      confirm: `${name} 매장을 승인할까요? 고객에게 노출될 수 있어요.`,
      done: `${name} 가입을 승인했어요.`,
      run: () => approve(shopId),
    },
    reject: {
      label: '거절',
      submit: '거절하기',
      withReason: true,
      done: `${name} 가입을 거절했어요.`,
      run: () => reject(shopId, reason.trim()),
    },
    cancelApproval: {
      label: '승인 취소',
      submit: '승인 취소하기',
      withReason: true,
      guide: '진행 중이거나 앞으로 있을 예약이 있으면 취소할 수 없어요.',
      done: `${name} 승인을 취소했어요.`,
      run: () => cancelApproval(shopId, reason.trim()),
    },
    cancelRejection: {
      label: '거절 취소',
      confirm: `${name} 거절을 취소할까요? 승인 대기로 돌아가요.`,
      done: `${name} 거절을 취소했어요.`,
      run: () => cancelRejection(shopId),
    },
  };
  const AVAILABLE = {
    PENDING: ['reject', 'approve'],
    ACTIVE: ['cancelApproval'],
    REJECTED: ['cancelRejection'],
  };
  const current = ACTIONS[action];
  const available = AVAILABLE[status] ?? [];

  const choose = (next) => {
    setError('');
    setReason('');
    setAction(next);
  };

  const submit = async (event) => {
    event.preventDefault();
    setError('');
    try {
      await current.run();
      onDone(current.done);
    } catch (failure) {
      setError(failure.message);
    }
  };

  return (
    <Dialog title="가입 신청 상세" onClose={onClose}>
      <dl className={styles.rows}>
        {rows.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
        {status === 'REJECTED' && (
          <div>
            <dt>거절 사유</dt>
            <dd>{application.rejectReason}</dd>
          </div>
        )}
      </dl>
      {error && (
        <Alert tone="error" role="alert">
          {error}
        </Alert>
      )}
      {!current && status === 'PENDING' && (
        <small className={styles.hint}>{SHOP_REVIEW_GUIDE}</small>
      )}
      {!current && (
        <div className={styles.actions}>
          {available.map((key) => (
            <Button
              key={key}
              type="button"
              variant={key === 'approve' ? 'primary' : undefined}
              onClick={() => choose(key)}
            >
              {ACTIONS[key].label}
            </Button>
          ))}
        </div>
      )}
      {current && (
        <form className={styles.confirm} onSubmit={submit}>
          {current.confirm && <strong>{current.confirm}</strong>}
          {current.withReason && (
            <>
              <strong>{current.label} 사유</strong>
              {current.guide && <small className={styles.hint}>{current.guide}</small>}
              <div className={styles.chips}>
                {SHOP_REJECT_REASONS.map((item) => (
                  <button
                    key={item}
                    type="button"
                    aria-pressed={reason === item}
                    onClick={() => setReason(item)}
                  >
                    {item}
                  </button>
                ))}
              </div>
              <input
                aria-label={`${current.label} 사유 직접 입력`}
                placeholder="직접 입력"
                maxLength={SHOP_REJECT_REASON_MAX_LENGTH}
                value={reason}
                onChange={(event) => setReason(event.target.value)}
              />
              <small className={styles.hint}>이 사유는 매장에 그대로 보여요.</small>
            </>
          )}
          <div className={styles.actions}>
            <Button type="button" disabled={isSaving} onClick={() => choose(null)}>
              뒤로
            </Button>
            <Button
              type="submit"
              variant="primary"
              disabled={isSaving || (current.withReason && !reason.trim())}
            >
              {current.submit ?? current.label}
            </Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}
