import { generatePath, Link } from 'react-router-dom';
import { DESIGN_ROUTES as R } from '@/constants/designRoutes';
import { money } from './data';
import Asset from './Asset';

export default function ServiceCard({ service }) {
  const day = ['오늘', '내일', '모레'][service.day];
  return (
    <article className="b-service-card">
      <div className="b-service-photo">
        <Asset name={service.image} alt={service.shop + ' ' + service.category + ' 시술'} />
        <span
          className={
            'b-urgency ' +
            (service.discount ? 'b-sale' : '') +
            (service.urgency === '임박 특가' ? ' b-soon' : '')
          }
        >
          {service.discount > 0 && <i />}
          {service.urgency}
        </span>
        {service.discount > 0 && (
          <span className="b-start-tag">
            <Asset name={service.id === 'nail' ? '8-3-8.svg' : '8-3-11.svg'} />
            {service.id === 'nail' ? '40분 뒤 시작!' : day + ' ' + service.time + ' 시작'}
          </span>
        )}
      </div>
      <div className="b-service-body">
        <div className="b-row b-service-meta">
          <span>{service.shop}</span>
          <small>
            <Asset name="8-3-9.svg" />
            {service.location}
          </small>
        </div>
        <h3>{service.name}</h3>
        <div className="b-flex b-service-time">
          <span className="b-duration">
            <Asset name="8-3-10.svg" />
            {service.duration}분 소요
          </span>
          <strong className={service.discount ? 'b-pink' : ''}>
            {day} {service.time}
          </strong>
        </div>
        <div className="b-row b-price-row">
          <div>
            <small className="b-muted">
              {service.discount ? <del>{money(service.original)}</del> : '정상가 (할인 미적용)'}
            </small>
            <div className="b-price">
              {service.discount > 0 && (
                <strong className={service.urgency === '임박 특가' ? 'b-amber' : 'b-pink'}>
                  {service.discount}%
                </strong>
              )}
              <strong>{money(service.price)}</strong>
            </div>
          </div>
          <Link
            className={'b-button b-small ' + (service.discount ? 'b-primary' : 'b-soft')}
            to={generatePath(R.RESERVE, { id: service.id })}
          >
            빈자리 확인
          </Link>
        </div>
      </div>
    </article>
  );
}
