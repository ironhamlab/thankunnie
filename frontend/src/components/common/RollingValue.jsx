import { useState } from 'react';
import styles from './RollingValue.module.css';

const toNumber = (value) => Number(value.replace(/\D/g, ''));

function RollingDigit({ digit, direction }) {
  const [current, setCurrent] = useState(digit);
  const [leaving, setLeaving] = useState(null);
  const [hasChanged, setHasChanged] = useState(false);

  if (digit !== current) {
    setLeaving(current);
    setCurrent(digit);
    setHasChanged(true);
  }

  const enterClass = hasChanged ? leaving !== null && styles.enter : styles.intro;

  return (
    <span className={`${styles.digit} ${direction === 'down' ? styles.down : styles.up}`}>
      {leaving !== null && (
        <span
          key={`leave-${leaving}`}
          className={styles.leave}
          onAnimationEnd={() => setLeaving(null)}
        >
          {leaving}
        </span>
      )}
      <span key={current} className={enterClass || undefined}>
        {current}
      </span>
    </span>
  );
}

/**
 * 값이 바뀌면 바뀐 자리의 숫자만 굴려 보여준다. 줄어들면 위로, 늘어나면 아래로 움직인다.
 * 화면 읽기 도구는 굴러가는 글자 대신 값 전체를 한 번에 읽는다.
 *
 * @param value 표시할 글자. 숫자 뒤에 단위가 붙는다 (예: 256개)
 */
export default function RollingValue({ value }) {
  const [current, setCurrent] = useState(value);
  const [direction, setDirection] = useState('down');

  if (value !== current) {
    setDirection(toNumber(value) < toNumber(current) ? 'down' : 'up');
    setCurrent(value);
  }

  const characters = [...value];
  return (
    <>
      <span className={styles.srOnly}>{value}</span>
      <span aria-hidden="true">
        {/* 자릿수가 달라져도 같은 자리끼리 맞도록 오른쪽부터 센 위치를 key 로 쓴다 */}
        {characters.map((character, index) => {
          const position = characters.length - index;
          return /\d/.test(character) ? (
            <RollingDigit key={position} digit={character} direction={direction} />
          ) : (
            <span key={position}>{character}</span>
          );
        })}
      </span>
    </>
  );
}
