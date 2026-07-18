FROM eclipse-temurin:25-jdk-noble AS builder

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:25-jre-noble

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system simulator \
    && useradd --system --gid simulator --home-dir /app --shell /usr/sbin/nologin simulator

WORKDIR /app

COPY --from=builder --chown=simulator:simulator /workspace/build/libs/*.jar app.jar

ENV SERVER_PORT=8080

EXPOSE 8080

USER simulator

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl --fail --silent "http://localhost:${SERVER_PORT}/actuator/health" > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]