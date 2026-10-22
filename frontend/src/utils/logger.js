/**
 * 개발 모드에서만 출력하는 로거. 운영 빌드에서는 아무것도 출력하지 않는다.
 * 디버깅용 console.log 대신 사용한다.
 */
const isDev = import.meta.env.DEV;

export const logger = {
  debug: (...args) => {
    if (isDev) console.debug(...args);
  },
  info: (...args) => {
    if (isDev) console.info(...args);
  },
  warn: (...args) => {
    if (isDev) console.warn(...args);
  },
  error: (...args) => {
    if (isDev) console.error(...args);
  },
};
