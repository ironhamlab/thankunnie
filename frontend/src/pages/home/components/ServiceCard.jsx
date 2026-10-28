import { generatePath, Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { money } from '@/utils/demoData';
import Asset from '@/components/common/Asset';
import styles from './ServiceCard.module.css';

export default function ServiceCard({ service }) {
  const day = ['오늘', '내일', '모레'][service.day];
  return (
    <article className={styles.serviceCard}>
      <div className={styles.servicePhoto}>
        <Asset name={service.image} alt={service.shop + ' ' + service.category + ' 시술'} />
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
            <Asset name={service.id === 'nail' ? '8-3-8.svg' : '8-3-11.svg'} />
            {service.id === 'nail' ? '40분 뒤 시작!' : day + ' ' + service.time + ' 시작'}
          </span>
        )}
      </div>
      <div className={styles.serviceBody}>
        <div className={`b-row ${styles.serviceMeta}`}>
          <span>{service.shop}</span>
          <small>
            <Asset name="8-3-9.svg" />
            {service.location}
          </small>
        </div>
        <h3>{service.name}</h3>
        <div className={`b-flex ${styles.serviceTime}`}>
          <span className={styles.duration}>
            <Asset name="8-3-10.svg" />
            {service.duration}분 소요
          </span>
          <strong className={service.discount ? 'b-pink' : ''}>
            {day} {service.time}
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
            to={generatePath(ROUTES.SERVICE_DETAIL, { id: service.id })}
          >
            빈자리 확인
          </Link>
        </div>
      </div>
    </article>
  );
}
