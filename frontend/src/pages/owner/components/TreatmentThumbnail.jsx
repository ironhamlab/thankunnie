import RepresentativeImage from './RepresentativeImage';
import styles from './TreatmentThumbnail.module.css';

export default function TreatmentThumbnail({ imageUrl, name }) {
  return (
    <RepresentativeImage
      key={imageUrl ?? 'default'}
      imageUrl={imageUrl}
      name={name}
      className={styles.image}
      width="64"
      height="64"
      loading="lazy"
    />
  );
}
