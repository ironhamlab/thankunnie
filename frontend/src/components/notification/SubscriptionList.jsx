import { useEffect, useRef, useState } from 'react';
import { generatePath, Link } from 'react-router-dom';
import { regionNameOf } from '@/constants/regions';
import { ROUTES } from '@/constants/routes';
import { useNotificationStore } from '@/store/useNotificationStore';
import { isPendingFavorite, shopLabel } from '@/utils/notificationRules';
import { datePart, timePart } from '@/utils/scheduleDate';
import Alert from '@/components/common/Alert';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import Panel from '@/components/common/Panel';
import ClockIcon from '@/components/icons/ClockIcon';
import InfoIcon from '@/components/icons/InfoIcon';
import SubscriptionEditDialog from './SubscriptionEditDialog';
import styles from './SubscriptionList.module.css';

const LEAVE_MS = 320;
const reduceMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const monthDay = (dateTime) => {
  const [, month, day] = datePart(dateTime).split('-');
  return `${month}/${day}`;
};

const formatHope = (startTime, endTime) =>
  `${monthDay(startTime)} ${timePart(startTime)} ~ ${monthDay(endTime)} ${timePart(endTime)}`;

/**
 * 내 알림 구독 목록. 서버가 준 해제하지 않은 구독만 최근 등록 순으로 보여주고, 조건 수정과 해제를 한다.
 *
 * @param {boolean} [embedded] 구독 화면 탭 안에 넣을 때 바깥 여백을 빼기 위해 사용한다
 */
export default function SubscriptionList({ embedded = false }) {
  const subscriptions = useNotificationStore((state) => state.subscriptions);
  const listStatus = useNotificationStore((state) => state.listStatus);
  const listError = useNotificationStore((state) => state.listError);
  const categories = useNotificationStore((state) => state.categories);
  const shopNames = useNotificationStore((state) => state.shopNames);
  const saving = useNotificationStore((state) => state.saving);
  const loadSubscriptions = useNotificationStore((state) => state.loadSubscriptions);
  const loadCategories = useNotificationStore((state) => state.loadCategories);
  const loadShopNames = useNotificationStore((state) => state.loadShopNames);
  const removeSubscription = useNotificationStore((state) => state.removeSubscription);
  const [editing, setEditing] = useState(null);
  // 해제 애니메이션 중. 서버에서 먼저 빠져도 카드가 잠깐 남도록 스냅샷을 둔다
  const [leaving, setLeaving] = useState(null);
  const [error, setError] = useState(null);
  const [savedMessage, setSavedMessage] = useState(null);
  const leaveTimer = useRef(null);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      window.clearTimeout(leaveTimer.current);
    };
  }, []);

  useEffect(() => {
    loadSubscriptions();
    loadCategories();
  }, [loadSubscriptions, loadCategories]);

  const storeShopIds = subscriptions
    .filter((item) => item.subscriptionType === 'STORE')
    .map((item) => Number(item.shopId))
    .filter((id, index, list) => list.indexOf(id) === index)
    .join(',');

  useEffect(() => {
    if (!storeShopIds) return;
    loadShopNames(storeShopIds.split(','));
  }, [storeShopIds, loadShopNames]);

  const isLoading = listStatus === 'idle' || listStatus === 'loading';
  const isFailed = listStatus === 'failed';
  // 희망 시간이 없는 업장 찜은 알림 전 찜 탭에서 본다
  const alertSubscriptions = subscriptions.filter((item) => !isPendingFavorite(item));
  const isEmpty = listStatus === 'succeeded' && alertSubscriptions.length === 0;
  // 실패하면 카드를 그리지 않는다. 조회 중에는 이전에 받은 목록을 유지한다
  const baseItems = isFailed ? [] : alertSubscriptions;
  const items =
    leaving && !baseItems.some((item) => item.subscriptionId === leaving.id)
      ? [...baseItems.slice(0, leaving.index), leaving.item, ...baseItems.slice(leaving.index)]
      : baseItems;

  const categoryNameOf = (categoryId) =>
    categories.find((category) => Number(category.categoryId) === Number(categoryId))?.name ??
    `카테고리 ${categoryId}`;

  const shopNameOf = (shopId) => shopLabel(shopId, shopNames[String(Number(shopId))]);

  const titleOf = (item) =>
    item.subscriptionType === 'STORE'
      ? shopNameOf(item.shopId)
      : `${categoryNameOf(item.categoryId)} · ${regionNameOf(item.regionCode)}`;

  const waitLeave = (ms) =>
    new Promise((resolve) => {
      leaveTimer.current = window.setTimeout(resolve, ms);
    });

  // 해제는 바로 보내고, 카드 사라짐 애니메이션은 그와 따로 맞춘다
  const handleRemove = async (subscriptionId) => {
    if (leaving != null || saving) return;
    setError(null);
    setSavedMessage(null);

    const index = items.findIndex((item) => item.subscriptionId === subscriptionId);
    const item = items[index];
    if (!item) return;

    const animate = !reduceMotion();
    if (animate) setLeaving({ id: subscriptionId, item, index });

    const [result] = await Promise.all([
      removeSubscription(subscriptionId),
      animate ? waitLeave(LEAVE_MS) : Promise.resolve(),
    ]);

    if (!mounted.current) return;

    if (!result.ok) {
      window.clearTimeout(leaveTimer.current);
      setLeaving(null);
      setError(result.error.message);
      return;
    }
    setLeaving(null);
  };

  const savedTitleOf = (subscription, request) =>
    subscription.subscriptionType === 'STORE'
      ? shopNameOf(request.shopId)
      : `${categoryNameOf(request.categoryId)} · ${regionNameOf(request.regionCode)}`;

  const handleSaved = (subscription, request) => {
    setError(null);
    setSavedMessage(
      `${savedTitleOf(subscription, request)} · ${formatHope(request.startTime, request.endTime)} 으로 수정했어요`,
    );
  };

  return (
    <div className={embedded ? styles.embedded : `b-container b-page ${styles.root}`}>
      <header className={styles.intro}>
        <h1>내 알림 구독</h1>
        <p className={styles.lead}>해제하지 않은 구독만 최근 등록 순으로 보여요.</p>
      </header>

      {savedMessage && (
        <Alert className={styles.result} tone="success" role="status">
          <span className={styles.resultText}>{savedMessage}</span>
          <button
            type="button"
            className={styles.resultClose}
            aria-label="닫기"
            onClick={() => setSavedMessage(null)}
          >
            ✕
          </button>
        </Alert>
      )}
      {error && (
        <Alert className={styles.error} tone="error" role="alert">
          {error}
        </Alert>
      )}
      {isLoading && <p role="status">알림 구독을 불러오는 중입니다.</p>}
      {isFailed && (
        <Alert tone="error" role="alert">
          {listError?.message ?? '알림 구독을 불러오지 못했습니다.'}
        </Alert>
      )}
      {isEmpty && (
        <EmptyState>
          <h2>등록된 알림 구독이 없어요</h2>
          <p>
            희망 시간을 정하면, 그 시간에 빈자리가 났을 때 가입한 휴대폰 번호로 문자를 받을 수
            있어요.
          </p>
        </EmptyState>
      )}

      <div className={styles.list}>
        {items.map((item) => {
          const isStore = item.subscriptionType === 'STORE';
          return (
            <Panel
              as="article"
              padded
              className={leaving?.id === item.subscriptionId ? styles.cardLeaving : styles.card}
              key={item.subscriptionId}
            >
              <div className={styles.head}>
                <Badge className={isStore ? styles.storeBadge : styles.categoryBadge}>
                  {isStore ? '업장' : '카테고리·지역'}
                </Badge>
                {isStore ? (
                  <h2 className={styles.title}>
                    <Link
                      className={styles.titleLink}
                      to={generatePath(ROUTES.SHOP_DETAIL, { shopId: String(item.shopId) })}
                    >
                      {titleOf(item)}
                    </Link>
                  </h2>
                ) : (
                  <h2 className={styles.title}>{titleOf(item)}</h2>
                )}
                <Badge tone="success">알림 받는 중</Badge>
              </div>
              <p className={styles.period}>
                <ClockIcon size={16} />
                희망 시간 {formatHope(item.startTime, item.endTime)}
              </p>
              <div className={styles.actions}>
                <Button
                  type="button"
                  size="small"
                  className={styles.editButton}
                  disabled={saving || leaving != null}
                  onClick={() => setEditing(item)}
                >
                  조건 수정
                </Button>
                <Button
                  type="button"
                  variant="primary"
                  size="small"
                  disabled={saving || leaving != null}
                  onClick={() => handleRemove(item.subscriptionId)}
                >
                  구독 해제
                </Button>
              </div>
            </Panel>
          );
        })}
      </div>

      {items.length > 0 && (
        <p className={styles.note}>
          <InfoIcon size={16} />
          업장 구독은 희망 시간만, 시술·지역 구독은 시술·지역과 희망 시간만 수정됩니다. 해제한 구독과
          희망 종료가 지난 구독은 문자를 받지 않습니다.
        </p>
      )}

      {editing && (
        <SubscriptionEditDialog
          subscription={editing}
          categories={categories}
          onClose={() => setEditing(null)}
          onSaved={(request) => handleSaved(editing, request)}
        />
      )}
    </div>
  );
}
