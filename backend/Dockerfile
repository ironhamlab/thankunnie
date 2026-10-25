FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY backend/ ./backend/
COPY docs/erd/ ./docs/erd/

WORKDIR /workspace/backend

RUN chmod +x gradlew \
    && ./gradlew --no-daemon bootJar \
    && cp build/libs/*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

ENV TZ=Asia/Seoul
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Seoul -XX:MaxRAMPercentage=65.0"

RUN groupadd --system app \
    && useradd --system --gid app app

COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]