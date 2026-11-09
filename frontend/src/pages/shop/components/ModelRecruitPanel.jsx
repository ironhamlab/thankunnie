import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import styles from '../ShopDetailPage.module.css';

/**
 * 초보 시술 모집 안내 확인 단계. 고른 초보 시술의 참여 조건과 촬영 안내를 보여준다.
 * 두 값은 초보 시술 등록 시 필수지만, 비어 오면 안내 문구로 대신한다.
 * 아래 확인 체크를 해야 다음 단계(날짜·시간 선택)로 갈 수 있다.
 *
 * @param agreed        안내 사항 확인 체크 여부
 * @param onAgreeChange 체크를 바꾸면 체크 여부를 받는 콜백
 * @param onNext        다음 단계로 가는 콜백
 */
export default function ModelRecruitPanel({ treatment, agreed, onAgreeChange, onNext }) {
  return (
    <Panel as="section" padded aria-label="초보 시술 모집 안내">
      <h2 className="b-panel-heading">초보 시술 모집 안내</h2>
      {treatment.description && <p className={styles.intro}>{treatment.description}</p>}
      <div className={`b-highlight ${styles.modelNotice}`}>
        <h3>참여 조건</h3>
        <p>{treatment.modelCondition || '등록된 참여 조건이 없습니다.'}</p>
      </div>
      <div className={`b-highlight ${styles.modelNotice}`}>
        <h3>촬영 안내</h3>
        <p>{treatment.photoNotice || '등록된 촬영 안내가 없습니다.'}</p>
      </div>
      <label className={styles.agree}>
        <input
          type="checkbox"
          checked={agreed}
          onChange={(event) => onAgreeChange(event.target.checked)}
        />
        <span>
          <strong>[필수] 위 참여 조건과 촬영 안내를 모두 확인했습니다</strong>
          <small>참여 조건을 충족하지 않으면 매장에서 시술이 취소될 수 있습니다.</small>
        </span>
      </label>
      <Button
        type="button"
        variant="primary"
        wide
        className={styles.next}
        disabled={!agreed}
        onClick={onNext}
      >
        다음: 날짜·시간 선택
      </Button>
    </Panel>
  );
}
