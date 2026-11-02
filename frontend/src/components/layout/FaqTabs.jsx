import { useState } from 'react';
import Button from '@/components/common/Button';
import FaqList from '@/components/common/FaqList';
import {
  FAQ_GROUPS,
  KAKAO_OPEN_CHAT_URL,
  SHOP_FAQ_GROUPS,
} from '@/constants/customerSupport';
import styles from './FaqTabs.module.css';

const TABS = [
  ['customer', '고객용', FAQ_GROUPS],
  ['shop', '매장용', SHOP_FAQ_GROUPS],
];

/**
 * 자주 묻는 질문 창의 고객용·매장용 탭. 가입 전에도 볼 수 있게 푸터에서 연다.
 */
export default function FaqTabs() {
  const [tab, setTab] = useState(TABS[0][0]);
  const groups = TABS.find(([key]) => key === tab)[2];

  return (
    <div className={styles.root}>
      <div className={`b-options b-tabs ${styles.tabs}`} role="group" aria-label="질문 대상">
        {TABS.map(([key, label]) => (
          <button
            key={key}
            type="button"
            aria-pressed={tab === key}
            className={tab === key ? 'is-selected' : ''}
            onClick={() => setTab(key)}
          >
            {label}
          </button>
        ))}
      </div>
      {/* 탭을 바꾸면 펼쳐 둔 답을 접은 상태로 시작한다 */}
      <FaqList key={tab} groups={groups} />
      <p>찾는 내용이 없으면 카카오톡으로 문의해 주세요.</p>
      <Button as="a" wide href={KAKAO_OPEN_CHAT_URL} target="_blank" rel="noreferrer">
        카카오톡 문의하기
      </Button>
    </div>
  );
}
