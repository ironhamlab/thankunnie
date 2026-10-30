import Panel from '@/components/common/Panel';
import styles from '../ShopDetailPage.module.css';

/**
 * 고른 모델 시술의 참여 조건과 촬영 안내. 두 값은 모델 시술 등록 시 필수지만, 비어 오면 안내 문구로 대신한다.
 */
export default function ModelRecruitPanel({ treatment }) {
  return (
    <Panel as="section" padded aria-label="모델 모집 안내">
      <h2 className="b-panel-heading">모델 모집 안내</h2>
      {treatment.description && <p className={styles.intro}>{treatment.description}</p>}
      <div className={`b-highlight ${styles.modelNotice}`}>
        <h3>참여 조건</h3>
        <p>{treatment.modelCondition || '등록된 참여 조건이 없습니다.'}</p>
      </div>
      <div className={`b-highlight ${styles.modelNotice}`}>
        <h3>촬영 안내</h3>
        <p>{treatment.photoNotice || '등록된 촬영 안내가 없습니다.'}</p>
      </div>
    </Panel>
  );
}
