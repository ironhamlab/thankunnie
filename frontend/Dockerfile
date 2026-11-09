FROM node:22-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
# 카카오맵 JavaScript 키. 브라우저에 공개되는 값이며 카카오 콘솔에 등록한 도메인에서만 동작한다
ARG VITE_KAKAO_MAP_APP_KEY=
ENV VITE_KAKAO_MAP_APP_KEY=${VITE_KAKAO_MAP_APP_KEY}
ARG VITE_PORTONE_IMP_CODE
ENV VITE_PORTONE_IMP_CODE=${VITE_PORTONE_IMP_CODE}

RUN npm run build

FROM nginx:1.28-alpine

ENV TZ=Asia/Seoul

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]