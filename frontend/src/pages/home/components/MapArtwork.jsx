import Asset from '@/components/common/Asset';
import styles from './MapArtwork.module.css';

export default function MapArtwork() {
  return (
    <div className={styles.root}>
      <Asset name="18-3548-35.svg" alt="강남역 일대 예시 지도" />
      <Asset name="18-3548-36.svg" className={`b-map-vector ${styles.vector0}`} />
      <Asset name="18-3548-37.svg" className={`b-map-vector ${styles.vector1}`} />
      <Asset name="18-3548-38.svg" className={`b-map-vector ${styles.vector2}`} />
      <Asset name="18-3548-39.svg" className={`b-map-vector ${styles.vector3}`} />
      <Asset name="18-3548-40.svg" className={`b-map-vector ${styles.vector4}`} />
      <Asset name="18-3548-41.svg" className={`b-map-vector ${styles.vector5}`} />
      <Asset name="18-3548-42.svg" className={`b-map-vector ${styles.vector6}`} />
      <Asset name="18-3548-43.svg" className={`b-map-vector ${styles.vector7}`} />
      <Asset name="18-3548-44.svg" className={`b-map-vector ${styles.vector8}`} />
      <Asset name="18-3548-45.svg" className={`b-map-vector ${styles.vector9}`} />
      <Asset name="18-3548-46.svg" className={`b-map-vector ${styles.vector10}`} />
      <Asset name="18-3548-47.svg" className={`b-map-vector ${styles.vector11}`} />
      <Asset name="18-3548-48.svg" className={`b-map-vector ${styles.vector12}`} />
      <Asset name="18-3548-49.svg" className={`b-map-vector ${styles.vector13}`} />
      <Asset name="18-3548-50.svg" className={`b-map-vector ${styles.vector14}`} />
      <Asset name="18-3548-51.svg" className={`b-map-vector ${styles.vector15}`} />
      <Asset name="18-3548-52.svg" className={`b-map-vector ${styles.vector16}`} />
      <Asset name="18-3548-53.svg" className={`b-map-vector ${styles.vector17}`} />
      <span className={`${styles.road} ${styles.gangnam}`}>강남대로</span>
      <span className={`${styles.road} ${styles.teheran}`}>테헤란로</span>
      <span className={`${styles.road} ${styles.sapyeong}`}>사평대로</span>
      <span className={`${styles.road} ${styles.sinnonhyeon}`}>신논현역 (9호선·신분당선)</span>
      <span className={`${styles.road} ${styles.station}`}>강남역 (2호선·신분당선)</span>
    </div>
  );
}
