import { useEffect } from 'react';
import { useExploreStore } from '@/store/useExploreStore';
import styles from './HomeIntro.module.css';

/**
 * 홈 첫 줄. 서비스 소개와 앞으로 7일(서버 기본 기간) 빈자리 현황을 실제 조회 값으로 보여준다.
 * 최대 할인은 사장님이 연 시간의 요약에 타임세일과 임박 카드를 더해 아래 카드보다 낮게 보이지 않게 한다.
 * 숫자를 받지 못하면 소개 문구만 남긴다.
 */
export default function HomeIntro() {
  const shops = useExploreStore((s) => s.summaryShops);
  const status = useExploreStore((s) => s.summaryShopsStatus);
  const saleShops = useExploreStore((s) => s.saleShops);
  const imminentVacancies = useExploreStore((s) => s.imminentVacancies);
  const loadSummaryShops = useExploreStore((s) => s.loadSummaryShops);
  const loadSaleShops = useExploreStore((s) => s.loadSaleShops);

  useEffect(() => {
    loadSummaryShops();
    loadSaleShops();
  }, [loadSummaryShops, loadSaleShops]);

  const vacancyCount = shops.reduce(
    // 초보 시술만 있는 매장은 vacancyCount 에 이미 초보 시술 빈자리가 담겨 있다
    (sum, shop) => sum + shop.vacancyCount + (shop.modelOnly ? 0 : shop.modelVacancyCount),
    0,
  );
  const maxDiscount = Math.max(
    0,
    ...[...shops, ...saleShops].map((shop) => shop.maxDiscountRate),
    ...imminentVacancies.map((vacancy) => vacancy.discount),
  );
  const stats = [
    { label: '빈자리 매장', value: `${shops.length}곳` },
    { label: '이번 주 빈자리', value: `${vacancyCount}개` },
    { label: '최대 할인', value: `${maxDiscount}%` },
  ];

  return (
    <section className={styles.root} aria-label="서비스 소개">
      <div>
        <h1>
          취소된 자리, <em>마감 전에 싸게</em> 잡으세요
        </h1>
        <p>흩어진 뷰티샵의 취소·임박 빈자리를 한곳에 모았어요.</p>
      </div>
      {status === 'succeeded' && shops.length > 0 && (
        <dl className={styles.stats}>
          {stats.map((stat) => (
            <div key={stat.label}>
              <dt>{stat.label}</dt>
              <dd>{stat.value}</dd>
            </div>
          ))}
        </dl>
      )}
    </section>
  );
}
