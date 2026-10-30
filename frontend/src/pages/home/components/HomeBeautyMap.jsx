import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { money } from '@/utils/demoData';
import { useExploreStore } from '@/store/useExploreStore';
import { dayLabel, shopPagePath } from '@/utils/exploreVacancy';
import { datePart, timePart } from '@/utils/scheduleDate';
import KakaoMap from '@/components/map/KakaoMap';
import Button from '@/components/common/Button';
import styles from './HomeBeautyMap.module.css';

// 위쪽 도구 막대·오른쪽 확대 버튼·아래쪽 미리보기에 핀이 가리지 않게 범위를 맞출 때 여백을 넓게 둔다. 핀 글자가 길어 좌우도 띄운다
const MAP_BOUNDS_PADDING = { top: 100, right: 130, bottom: 130, left: 110 };

/**
 * 홈 지도. 빈자리가 있는 매장마다 핀 하나(최저 판매가)를 찍는다 (C-02, 오늘부터 7일).
 */
export default function HomeBeautyMap() {
  const shops = useExploreStore((s) => s.shops);
  const shopsStatus = useExploreStore((s) => s.shopsStatus);
  const loadShops = useExploreStore((s) => s.loadShops);
  const [selectedId, setSelectedId] = useState(null);
  const [saleOnly, setSaleOnly] = useState(false);
  const selected = shops.find((shop) => shop.shopId === selectedId) || shops[0];

  useEffect(() => {
    loadShops({ priceTier: saleOnly ? 'IMMINENT' : undefined });
  }, [saleOnly, loadShops]);

  const markers = useMemo(
    () =>
      shops.map((shop) => ({
        id: shop.shopId,
        lat: shop.latitude,
        lng: shop.longitude,
        label:
          shop.maxDiscountRate > 0
            ? `${money(shop.minSalePrice)} (${shop.maxDiscountRate}%↓)`
            : money(shop.minSalePrice),
        ariaLabel: `${shop.shopName} ${money(shop.minSalePrice)}부터`,
      })),
    [shops],
  );
  return (
    <div className={styles.section}>
      <div className={styles.heading}>
        <h2>내 주변 실시간 빈자리 지도</h2>
        <span>● 빈자리 있는 매장 {shops.length}곳</span>
        <button
          onClick={() => {
            setSelectedId(null);
            setSaleOnly(false);
          }}
        >
          ⌖ 지도 초기화
        </button>
      </div>
      <div className={styles.root}>
        <KakaoMap
          markers={markers}
          selectedId={selected?.shopId}
          onSelect={setSelectedId}
          markerClassName={`${styles.pin} ${styles.kakaoPin}`}
          boundsPadding={MAP_BOUNDS_PADDING}
          fallback={
            <small className={styles.disclaimer}>
              지도를 불러오지 못했어요 · 목록에서 확인해 주세요
            </small>
          }
        />
        <div className={styles.toolbar}>
          <span>⌖ 오늘부터 7일 안의 빈자리</span>
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
                {shopsStatus === 'loading'
                  ? '빈자리를 찾는 중이에요'
                  : shopsStatus === 'failed'
                    ? '빈자리를 불러오지 못했어요'
                    : '지금 열린 빈자리가 없어요'}
              </strong>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
