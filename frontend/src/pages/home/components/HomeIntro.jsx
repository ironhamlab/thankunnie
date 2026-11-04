import { useEffect } from 'react';
import useAutoRefresh from '@/hooks/useAutoRefresh';
import { useExploreStore } from '@/store/useExploreStore';
import RollingValue from '@/components/common/RollingValue';
import styles from './HomeIntro.module.css';

/**
 * 홈 첫 줄. 서비스 소개와 앞으로 7일(서버 기본 기간) 빈자리 현황을 실제 조회 값으로 보여준다.
 * 최대 할인은 아래 임박 카드 중 최대값이다. 카드가 예약 확정 등으로 빠지면 남은 카드 기준으로 내려간다.
 * 숫자는 화면을 보는 동안 다시 받아 갱신하고, 받지 못하면 소개 문구만 남긴다.
 */
export default function HomeIntro() {
  const shops = useExploreStore((s) => s.summaryShops);
  const status = useExploreStore((s) => s.summaryShopsStatus);
  const imminentVacancies = useExploreStore((s) => s.imminentVacancies);
  const imminentStatus = useExploreStore((s) => s.imminentStatus);
  const loadSummaryShops = useExploreStore((s) => s.loadSummaryShops);

  useEffect(() => {
    loadSummaryShops();
  }, [loadSummaryShops]);
  // 빈자리는 예약과 시간 경과로 계속 줄어들므로 아래 임박 카드와 같은 주기로 다시 받는다
  useAutoRefresh(loadSummaryShops);

  // 매장마다 일반·초보 시술을 합쳐 겹치지 않는 시작 시각을 이번 주 일요일까지 센 값(totalVacancyCount)을 더한다
  const vacancyCount = shops.reduce((sum, shop) => sum + shop.totalVacancyCount, 0);
  const maxDiscount = Math.max(0, ...imminentVacancies.map((vacancy) => vacancy.discount));
  const isFirstLoading = !shops.length && (status === 'idle' || status === 'loading');
  // 임박 카드를 받기 전에 0% 를 보였다가 숫자가 굴러가지 않게 한다
  const isDiscountLoading =
    !imminentVacancies.length && (imminentStatus === 'idle' || imminentStatus === 'loading');
  const stats = [
    { label: '빈자리 매장', value: `${shops.length}곳`, loading: isFirstLoading },
    { label: '이번 주 빈자리', value: `${vacancyCount}개`, loading: isFirstLoading },
    { label: '최대 할인', value: `${maxDiscount}%`, loading: isFirstLoading || isDiscountLoading },
  ];

  return (
    <section className={styles.root} aria-label="서비스 소개">
      <div>
        <h1 className={styles.title}>
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
              <dd>{stat.loading ? '–' : <RollingValue value={stat.value} />}</dd>
            </div>
          ))}
        </dl>
      )}
    </section>
  );
}
