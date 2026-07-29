FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./

RUN mkdir -p \
    backend/api-service \
    backend/worker-service \
    backend/shared

COPY backend/worker-service/build.gradle backend/worker-service/build.gradle
COPY backend/shared/build.gradle backend/shared/build.gradle

RUN chmod +x gradlew \
    && ./gradlew :worker-service:dependencies --no-daemon

COPY backend/worker-service backend/worker-service
COPY backend/shared backend/shared

RUN ./gradlew :worker-service:bootJar --no-daemon


FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S slotforge \
    && adduser -S slotforge -G slotforge

COPY --from=build \
    --chown=slotforge:slotforge \
    /workspace/backend/worker-service/build/libs/*.jar \
    /app/app.jar

USER slotforge

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]