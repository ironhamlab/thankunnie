import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { money } from '@/utils/demoData';
import { useExploreStore } from '@/store/useExploreStore';
import Button from '@/components/common/Button';
import VacancyCountdown from './VacancyCountdown';
import styles from './ImminentCarousel.module.css';

// 자동으로 넘기는 간격. 매장·시간·가격을 읽을 시간을 주려고 3초보다 조금 길게 둔다
const AUTO_PLAY_MS = 3500;
const prefersReducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
// 이 거리(px) 이상 옆으로 밀면 넘긴다. 세로 스크롤하다 살짝 흔들린 것은 넘기지 않는다
const SWIPE_MIN_PX = 40;
// 화면에 이 비율 이상 보일 때만 저절로 넘긴다. 휴대폰에서 아래 지도를 보는 동안 지도가 바뀌지 않게 한다
const VISIBLE_RATIO = 0.5;

/**
 * 홈 임박 빈자리 캐러셀. 지금부터 8시간 안에 시작하는 일반 시술 빈자리를 매장마다 1건씩, 빠른 시작순으로
 * 최대 10장 보여준다(서버가 개수·정렬을 정한다). 더 보려면 전체보기(타임세일 목록)로 넘긴다.
 * 저절로 넘기되, 마우스를 올리거나 키보드로 안의 버튼에 초점이 있으면 누르려던 카드가 바뀌지 않게 멈춘다.
 * 휴대폰에서는 옆으로 밀어 넘기고, 손을 대고 있는 동안만 멈춘다. 끝에서 넘기면 처음으로 돌아간다.
 * 기기에서 동작 줄이기를 켰으면 자동으로 넘기지 않는다.
 * 장이 바뀔 때마다(저절로 넘길 때도) onShowShop 으로 알려 지도가 지금 카드의 매장을 따라오게 한다.
 * 옆 지도를 보는 중(paused)이면 지도가 옮겨 가지 않게 자동 넘김을 멈춘다.
 * 캐러셀이 화면에서 거의 벗어나면(휴대폰에서 아래 지도로 내려 보는 중) 지도가 바뀌지 않게 자동 넘김을 멈춘다.
 *
 * @param onShowShop    지금 보여주는 임박 빈자리 카드와 { manual } 을 받는다. 직접 넘겼으면 manual 이 true 다
 * @param paused        true 면 자동으로 넘기지 않는다
 * @param advanceSignal 바뀔 때마다 기다리지 않고 바로 다음 장으로 넘긴다 (지도를 그만 만졌을 때)
 * @param showRequest   { shopId } 가 새로 들어올 때마다 그 매장 카드로 넘긴다 (지도에서 핀을 눌렀을 때)
 */
export default function ImminentCarousel({
  onShowShop,
  paused = false,
  advanceSignal = 0,
  showRequest = null,
}) {
  const vacancies = useExploreStore((s) => s.imminentVacancies);
  const status = useExploreStore((s) => s.imminentStatus);
  const loadImminentVacancies = useExploreStore((s) => s.loadImminentVacancies);
  const [index, setIndex] = useState(0);
  // 넘긴 방향. 다음 장은 오른쪽에서, 이전 장은 왼쪽에서 들어오게 한다
  const [direction, setDirection] = useState(1);
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const [touching, setTouching] = useState(false);
  const touchStartX = useRef(null);
  const rootRef = useRef(null);
  const [inView, setInView] = useState(true);
  // advanceSignal 이 바뀌면 다음 자동 넘김을 기다리지 않고 바로 한다
  const [seenSignal, setSeenSignal] = useState(advanceSignal);
  const [advanceNow, setAdvanceNow] = useState(false);
  const [seenRequest, setSeenRequest] = useState(showRequest);
  // 자동 넘김 타이머가 부모가 다시 그려질 때마다 새로 시작하지 않게 최신 콜백만 기억해 둔다
  const onShowShopRef = useRef(onShowShop);
  useEffect(() => {
    onShowShopRef.current = onShowShop;
  });

  useEffect(() => {
    loadImminentVacancies();
  }, [loadImminentVacancies]);

  const count = vacancies.length;
  // 목록이 다시 와서 개수가 줄어도 범위를 넘지 않게 한다
  const current = count ? vacancies[Math.min(index, count - 1)] : null;
  const currentIndex = current ? vacancies.indexOf(current) : 0;
  const goTo = (next, step) => {
    setDirection(step);
    setIndex(next);
    setAdvanceNow(false);
    onShowShop?.(vacancies[next], { manual: true });
  };
  const move = (step) => goTo((currentIndex + step + count) % count, step);
  const autoPlay =
    count > 1 && inView && !paused && !hovered && !focused && !touching && !prefersReducedMotion();
  // 그 순간 자동으로 넘길 수 없으면(아직 지도에 마우스가 있는 등) 평소처럼 기다렸다 넘긴다
  if (advanceSignal !== seenSignal) {
    setSeenSignal(advanceSignal);
    setAdvanceNow(autoPlay);
  }
  // 지도에서 누른 매장의 카드로 넘긴다. 지도는 이미 그 매장을 골랐으므로 다시 알리지 않는다
  if (showRequest !== seenRequest) {
    setSeenRequest(showRequest);
    const target = vacancies.findIndex(
      (card) => String(card.shopId) === String(showRequest?.shopId),
    );
    if (target >= 0 && target !== currentIndex) {
      setDirection(target < currentIndex ? -1 : 1);
      setIndex(target);
      setAdvanceNow(false);
    }
  }

  // 위에 고정된 헤더에 가린 부분은 빼고 세려고 헤더 높이를 잰다. 화면 폭이 바뀌면 헤더 높이도 바뀌어 다시 잰다
  const [headerHeight, setHeaderHeight] = useState(0);
  useEffect(() => {
    const header = document.querySelector('header');
    if (!header || !window.ResizeObserver) return undefined;
    const observer = new ResizeObserver(() => setHeaderHeight(header.offsetHeight));
    observer.observe(header);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const root = rootRef.current;
    if (!root || !window.IntersectionObserver) return undefined;
    // isIntersecting 은 조금만 걸쳐도 true 라서 보이는 비율로 본다
    const observer = new IntersectionObserver(
      ([entry]) => setInView(entry.intersectionRatio >= VISIBLE_RATIO),
      { threshold: VISIBLE_RATIO, rootMargin: `-${headerHeight}px 0px 0px 0px` },
    );
    observer.observe(root);
    return () => observer.disconnect();
  }, [headerHeight]);

  // 장이 바뀔 때마다 다시 센다. 손으로 넘긴 직후에 바로 다음 장으로 넘어가지 않게 한다
  useEffect(() => {
    if (!autoPlay) return undefined;
    const timer = window.setTimeout(
      () => {
        const next = (Math.min(index, count - 1) + 1) % count;
        setDirection(1);
        setIndex(next);
        setAdvanceNow(false);
        onShowShopRef.current?.(vacancies[next]);
      },
      advanceNow ? 0 : AUTO_PLAY_MS,
    );
    return () => window.clearTimeout(timer);
  }, [autoPlay, advanceNow, count, index, vacancies]);

  // 임박 빈자리가 없으면 스토어가 다음으로 빠른 빈자리를 대신 담아 준다
  const heading = current?.upcoming ? '다음으로 빠른 빈자리' : '임박 빈자리';
  // 타임세일은 시작 3시간 이내만 보여주므로, 지금 목록에 3시간 이내 카드가 있을 때만 그쪽으로 보낸다
  const hasLastMinute = vacancies.some(
    (card) => !card.upcoming && card.priceTier === 'LAST_MINUTE',
  );

  return (
    // 8시간 범위가 자정을 넘으면 내일 새벽 시각도 나오므로 "오늘"로 한정하지 않는다
    <section id="home-imminent" className={styles.root} aria-label={heading} ref={rootRef}>
      <div className={styles.heading}>
        <h2>
          <span aria-hidden="true">ϟ</span> {heading}
        </h2>
        <Link to={hasLastMinute ? `${ROUTES.HOME}?sale=1` : `${ROUTES.HOME}?view=list&date=all`}>
          전체보기 ›
        </Link>
      </div>
      {current ? (
        <div
          className={styles.slider}
          aria-roledescription="캐러셀"
          // 터치는 손을 떼도 마우스가 나갔다는 신호가 없어 멈춘 채로 남으므로 마우스일 때만 본다
          onPointerEnter={(event) => event.pointerType === 'mouse' && setHovered(true)}
          onPointerLeave={(event) => event.pointerType === 'mouse' && setHovered(false)}
          // 터치로 버튼을 눌러도 초점이 남으므로 키보드 초점일 때만 멈춘다
          onFocus={(event) => event.target.matches(':focus-visible') && setFocused(true)}
          onBlur={(event) => {
            if (!event.currentTarget.contains(event.relatedTarget)) setFocused(false);
          }}
          onTouchStart={(event) => {
            touchStartX.current = event.touches[0].clientX;
            setTouching(true);
          }}
          onTouchEnd={(event) => {
            const distance = event.changedTouches[0].clientX - (touchStartX.current ?? 0);
            if (Math.abs(distance) >= SWIPE_MIN_PX) move(distance < 0 ? 1 : -1);
            touchStartX.current = null;
            setTouching(false);
          }}
          onTouchCancel={() => setTouching(false)}
        >
          {/* 저절로 넘어가는 동안 화면 낭독기가 매번 읽지 않도록 멈췄을 때만 알린다 */}
          <article
            className={`${styles.card} ${direction < 0 ? styles.fromPrev : ''}`}
            aria-live={autoPlay ? 'off' : 'polite'}
            key={current.id}
          >
            <div className={styles.photo}>
              <img src={current.image} alt={`${current.shop} ${current.category} 시술`} />
              <span
                className={`${styles.urgency} ${current.urgency !== '초임박 특가' ? styles.isSoon : ''}`}
              >
                <span className={styles.urgencyDot} aria-hidden="true">
                  ●
                </span>{' '}
                {current.urgency}
              </span>
              <VacancyCountdown
                className={styles.closes}
                closesAt={current.bookingClosesAt}
                closesTime={current.closesAt}
                // 마감된 빈자리를 계속 고르지 않게 목록을 다시 받아 카드를 바꾼다
                onExpire={loadImminentVacancies}
              />
            </div>
            <div className={styles.body}>
              <div className={styles.meta}>
                <strong>{current.shop}</strong>
                <span>{current.category}</span>
              </div>
              <h3>{current.name}</h3>
              <p className={styles.time}>
                <Link className={styles.slotLink} to={current.shopPath}>
                  {current.day} <strong>{current.time}</strong>
                </Link>{' '}
                시작 · {current.duration}분 소요
              </p>
              <p className={styles.location}>⌖ {current.location}</p>
              <div className={styles.price}>
                <div>
                  {current.discount > 0 && <del>{money(current.original)}</del>}
                  <div>
                    {current.discount > 0 && <em>{current.discount}%</em>}{' '}
                    <b>{money(current.price)}</b>
                  </div>
                </div>
                {/* 이 링크가 카드 전체를 덮어, 카드 어디를 눌러도 같은 예약 화면으로 간다 */}
                <Button
                  as={Link}
                  variant="primary"
                  className={styles.cardLink}
                  to={current.shopPath}
                >
                  이 시간 예약하기
                </Button>
              </div>
            </div>
          </article>
          {count > 1 && (
            <div className={styles.controls}>
              <div className={styles.dots}>
                {vacancies.map((item, dot) => (
                  <button
                    type="button"
                    key={item.id}
                    aria-label={`${dot + 1}번째 빈자리`}
                    aria-pressed={item.id === current.id}
                    onClick={() => goTo(dot, dot < currentIndex ? -1 : 1)}
                  />
                ))}
              </div>
              <span>
                <b>{currentIndex + 1}</b> / {count}
              </span>
              <button type="button" aria-label="이전 빈자리" onClick={() => move(-1)}>
                ‹
              </button>
              <button type="button" aria-label="다음 빈자리" onClick={() => move(1)}>
                ›
              </button>
            </div>
          )}
        </div>
      ) : (
        <div className={styles.empty}>
          <strong>
            {status === 'loading' || status === 'idle'
              ? '임박 빈자리를 찾는 중이에요'
              : status === 'failed'
                ? '빈자리를 불러오지 못했어요'
                : '임박 빈자리가 아직 없어요'}
          </strong>
          {status === 'succeeded' && (
            <p>
              <Link to={`${ROUTES.HOME}?view=list`}>다른 날짜 빈자리 보기 ›</Link>
            </p>
          )}
        </div>
      )}
    </section>
  );
}
