import { useEffect } from 'react';
import { useExploreStore } from '@/store/useExploreStore';
import RollingValue from './RollingValue';
import styles from './HomeIntro.module.css';

// 빈자리는 예약과 시간 경과로 계속 줄어드므로 주기적으로 다시 받는다
const REFRESH_INTERVAL_MS = 30_000;

/**
 * 홈 첫 줄. 서비스 소개와 앞으로 7일(서버 기본 기간) 빈자리 현황을 실제 조회 값으로 보여준다.
 * 최대 할인은 매장이 연 시간의 요약에 타임세일과 임박 카드를 더해 아래 카드보다 낮게 보이지 않게 한다.
 * 숫자는 화면을 보는 동안 다시 받아 갱신하고, 받지 못하면 소개 문구만 남긴다.
 */
export default function HomeIntro() {
  const shops = useExploreStore((s) => s.summaryShops);
  const status = useExploreStore((s) => s.summaryShopsStatus);
  const saleShops = useExploreStore((s) => s.saleShops);
  const imminentVacancies = useExploreStore((s) => s.imminentVacancies);
  const loadSummaryShops = useExploreStore((s) => s.loadSummaryShops);
  const loadSaleShops = useExploreStore((s) => s.loadSaleShops);

  useEffect(() => {
    const refresh = () => {
      loadSummaryShops();
      loadSaleShops();
    };
    // 탭을 보고 있지 않을 때는 요청하지 않고, 돌아오면 바로 받는다
    const refreshIfVisible = () => {
      if (document.visibilityState === 'visible') {
        refresh();
      }
    };
    refresh();
    const timer = setInterval(refreshIfVisible, REFRESH_INTERVAL_MS);
    document.addEventListener('visibilitychange', refreshIfVisible);
    return () => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', refreshIfVisible);
    };
  }, [loadSummaryShops, loadSaleShops]);

  // 매장마다 일반·초보 시술을 합쳐 겹치지 않는 시작 시각을 이번 주 일요일까지 센 값(totalVacancyCount)을 더한다
  const vacancyCount = shops.reduce((sum, shop) => sum + shop.totalVacancyCount, 0);
  const maxDiscount = Math.max(
    0,
    ...[...shops, ...saleShops].map((shop) => shop.maxDiscountRate),
    ...imminentVacancies.map((vacancy) => vacancy.discount),
  );
  const isFirstLoading = !shops.length && (status === 'idle' || status === 'loading');
  const stats = [
    { label: '빈자리 매장', value: `${shops.length}곳` },
    { label: '이번 주 빈자리', value: `${vacancyCount}개` },
    { label: '최대 할인', value: `${maxDiscount}%` },
  ];

  return (
    <section className={styles.root} aria-label="서비스 소개">
      <div>
        <h1>
          언니, <em>지금 이 자리</em> 놓치면 후회해
        </h1>
        <p>흩어진 뷰티샵의 빈자리와 할인을, 마감 전에 한곳에서.</p>
      </div>
      {/* 첫 응답 전에도 칸을 먼저 보여 소개 문구와 함께 뜨게 하고, 숫자는 받는 대로 채운다.
          다시 받는 동안에는 이전 숫자를 유지해, 바뀐 자리만 굴러가게 한다 */}
      {(shops.length > 0 || isFirstLoading) && (
        <dl className={styles.stats}>
          {stats.map((stat) => (
            <div key={stat.label}>
              <dt>{stat.label}</dt>
              <dd>{isFirstLoading ? '–' : <RollingValue value={stat.value} />}</dd>
            </div>
          ))}
        </dl>
      )}
    </section>
  );
}
