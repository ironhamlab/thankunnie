import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useExploreStore } from '@/store/useExploreStore';
import { dayLabel, shopPagePath } from '@/utils/exploreVacancy';
import { datePart, timePart } from '@/utils/scheduleDate';
import ExploreMap from '@/components/map/ExploreMap';
import Button from '@/components/common/Button';
import styles from './HomeBeautyMap.module.css';

/**
 * 홈 지도. 지도에 보이는 범위에서 빈자리가 있는 매장을 찾아(C-02, 오늘부터 7일) 핀으로 찍는다.
 * 지도를 움직이면 그 범위로 다시 찾고, 축소하면 가까운 핀을 묶는다. 지도를 못 쓰면 범위 없이 찾는다.
 */
export default function HomeBeautyMap() {
  const shops = useExploreStore((s) => s.shops);
  const shopsStatus = useExploreStore((s) => s.shopsStatus);
  const loadShops = useExploreStore((s) => s.loadShops);
  const [selectedId, setSelectedId] = useState(null);
  const [saleOnly, setSaleOnly] = useState(false);
  const [bounds, setBounds] = useState(null);
  const [mapUnavailable, setMapUnavailable] = useState(false);
  // 지도 초기화를 누를 때마다 바뀌어 지도를 처음 위치로 돌린다
  const [resetSignal, setResetSignal] = useState(0);
  const selected = shops.find((shop) => String(shop.shopId) === selectedId) || shops[0];

  // 지도가 첫 범위를 알려 주기 전에는 조회하지 않는다
  useEffect(() => {
    if (!bounds && !mapUnavailable) return;
    loadShops({ priceTier: saleOnly ? 'IMMINENT' : undefined, ...(mapUnavailable ? {} : bounds) });
  }, [saleOnly, bounds, mapUnavailable, loadShops]);

  const handleUnavailable = useCallback(() => setMapUnavailable(true), []);
  return (
    <div className={styles.section}>
      <div className={styles.heading}>
        <h2>내 주변 실시간 빈자리 지도</h2>
        <span>● 이 지역 빈자리 매장 {shops.length}곳</span>
        <button
          onClick={() => {
            setSelectedId(null);
            setSaleOnly(false);
            setResetSignal((value) => value + 1);
          }}
        >
          ⌖ 지도 초기화
        </button>
        <Link className={styles.mapLink} to={ROUTES.MAP}>
          지도 크게 보기 ›
        </Link>
      </div>
      <div className={styles.root}>
        {mapUnavailable ? (
          <small className={styles.disclaimer}>
            지도를 불러오지 못했어요 · 목록에서 확인해 주세요
          </small>
        ) : (
          <ExploreMap
            shops={shops}
            // 미리보기는 첫 매장을 보여주지만, 지도에는 사용자가 직접 고른 매장만 넘긴다.
            // 첫 매장을 넘기면 이전에 본 지역 결과가 남아 있을 때 지도가 그쪽으로 옮겨 간다
            selectedId={selectedId}
            onSelect={setSelectedId}
            onBoundsChange={setBounds}
            onUnavailable={handleUnavailable}
            resetSignal={resetSignal}
          />
        )}
        <div className={styles.toolbar}>
          <span>⌖ 지도를 움직이면 그 지역을 다시 찾아요</span>
          <div>
            <button aria-pressed={!saleOnly} onClick={() => setSaleOnly(false)}>
              전체
            </button>
            <button aria-pressed={saleOnly} onClick={() => setSaleOnly(true)}>
              타임세일만
            </button>
          </div>
        </div>
        <div className={styles.preview}>
          <span className={styles.storeIcon} aria-hidden="true">
            ▤
          </span>
          {selected ? (
            <>
              <div>
                <strong>{selected.shopName}</strong>
                <span>
                  {dayLabel(datePart(selected.earliestStartsAt))}{' '}
                  {timePart(selected.earliestStartsAt)}부터
                </span>
                <p>
                  {selected.address} · 빈자리 {selected.vacancyCount}개
                </p>
              </div>
              <Button
                as={Link}
                variant="primary"
                to={shopPagePath(selected.shopId, { date: datePart(selected.earliestStartsAt) })}
              >
                빈자리 보기
              </Button>
            </>
          ) : (
            <div>
              <strong>
                {shopsStatus === 'loading' || shopsStatus === 'idle'
                  ? '빈자리를 찾는 중이에요'
                  : shopsStatus === 'failed'
                    ? '빈자리를 불러오지 못했어요'
                    : '이 지역에는 지금 열린 빈자리가 없어요. 지도를 옮기거나 축소해 보세요'}
              </strong>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
