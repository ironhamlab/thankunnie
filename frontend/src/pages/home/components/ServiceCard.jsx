import { Link } from 'react-router-dom';
import { money } from '@/utils/demoData';
import ClockIcon from '@/components/icons/ClockIcon';
import LocationIcon from '@/components/icons/LocationIcon';
import styles from './ServiceCard.module.css';

/**
 * 빈자리 카드. service 는 탐색 API 응답을 toVacancyCard 로 바꾼 값이다.
 */
export default function ServiceCard({ service }) {
  return (
    <article className={styles.root}>
      <div className={styles.photo}>
        <img src={service.image} alt={service.shop + ' ' + service.category + ' 시술'} />
        <span
          className={
            'b-urgency ' +
            (service.discount ? 'b-sale' : '') +
            (service.urgency === '임박 특가' ? ` ${styles.soon}` : '')
          }
        >
          {service.discount > 0 && <i />}
          {service.urgency}
        </span>
        {service.discount > 0 && (
          <span className={styles.startTag}>
            <ClockIcon size={16} />
            {service.day + ' ' + service.time + ' 시작'}
          </span>
        )}
      </div>
      <div className={styles.body}>
        <div className={`b-row ${styles.meta}`}>
          <span>{service.shop}</span>
          <small>
            <LocationIcon size={16} />
            {service.location}
          </small>
        </div>
        <h3>{service.name}</h3>
        <div className={`b-flex ${styles.time}`}>
          <span className={styles.duration}>
            <ClockIcon size={16} />
            {service.duration}분 소요
          </span>
          <strong className={service.discount ? 'b-pink' : ''}>
            {service.day} {service.time}
          </strong>
        </div>
        <div className={`b-row ${styles.priceRow}`}>
          <div>
            <small className="b-muted">
              {service.discount ? <del>{money(service.original)}</del> : '정상가 (할인 미적용)'}
            </small>
            <div className={styles.price}>
              {service.discount > 0 && (
                <strong className={service.urgency === '임박 특가' ? styles.amber : 'b-pink'}>
                  {service.discount}%
                </strong>
              )}
              <strong>{money(service.price)}</strong>
            </div>
          </div>
          <Link
            className={'b-button b-small ' + (service.discount ? 'b-primary' : 'b-soft')}
            to={service.detailPath}
          >
            빈자리 확인
          </Link>
        </div>
      </div>
    </article>
  );
}
