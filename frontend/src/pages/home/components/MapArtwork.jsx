import Asset from '@/components/common/Asset';
import styles from './MapArtwork.module.css';

export default function MapArtwork() {
  return (
    <div className={styles.mapArtwork}>
      <Asset name="18-3548-35.svg" alt="강남역 일대 예시 지도" />
      <Asset name="18-3548-36.svg" className={`b-map-vector ${styles.mapVector0}`} />
      <Asset name="18-3548-37.svg" className={`b-map-vector ${styles.mapVector1}`} />
      <Asset name="18-3548-38.svg" className={`b-map-vector ${styles.mapVector2}`} />
      <Asset name="18-3548-39.svg" className={`b-map-vector ${styles.mapVector3}`} />
      <Asset name="18-3548-40.svg" className={`b-map-vector ${styles.mapVector4}`} />
      <Asset name="18-3548-41.svg" className={`b-map-vector ${styles.mapVector5}`} />
      <Asset name="18-3548-42.svg" className={`b-map-vector ${styles.mapVector6}`} />
      <Asset name="18-3548-43.svg" className={`b-map-vector ${styles.mapVector7}`} />
      <Asset name="18-3548-44.svg" className={`b-map-vector ${styles.mapVector8}`} />
      <Asset name="18-3548-45.svg" className={`b-map-vector ${styles.mapVector9}`} />
      <Asset name="18-3548-46.svg" className={`b-map-vector ${styles.mapVector10}`} />
      <Asset name="18-3548-47.svg" className={`b-map-vector ${styles.mapVector11}`} />
      <Asset name="18-3548-48.svg" className={`b-map-vector ${styles.mapVector12}`} />
      <Asset name="18-3548-49.svg" className={`b-map-vector ${styles.mapVector13}`} />
      <Asset name="18-3548-50.svg" className={`b-map-vector ${styles.mapVector14}`} />
      <Asset name="18-3548-51.svg" className={`b-map-vector ${styles.mapVector15}`} />
      <Asset name="18-3548-52.svg" className={`b-map-vector ${styles.mapVector16}`} />
      <Asset name="18-3548-53.svg" className={`b-map-vector ${styles.mapVector17}`} />
      <span className={`${styles.mapRoad} ${styles.mapGangnam}`}>강남대로</span>
      <span className={`${styles.mapRoad} ${styles.mapTeheran}`}>테헤란로</span>
      <span className={`${styles.mapRoad} ${styles.mapSapyeong}`}>사평대로</span>
      <span className={`${styles.mapRoad} ${styles.mapSinnonhyeon}`}>
        신논현역 (9호선·신분당선)
      </span>
      <span className={`${styles.mapRoad} ${styles.mapStation}`}>강남역 (2호선·신분당선)</span>
    </div>
  );
}
