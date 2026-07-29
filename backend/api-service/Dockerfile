FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./

# settings.gradle declares every project, so their directories must exist.
# Only the API and shared projects need real build files and source code.
RUN mkdir -p \
    backend/api-service \
    backend/worker-service \
    backend/shared

COPY backend/api-service/build.gradle backend/api-service/build.gradle
COPY backend/shared/build.gradle backend/shared/build.gradle

RUN chmod +x gradlew \
    && ./gradlew :api-service:dependencies --no-daemon

COPY backend/api-service backend/api-service
COPY backend/shared backend/shared

RUN ./gradlew :api-service:bootJar --no-daemon


FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S slotforge \
    && adduser -S slotforge -G slotforge

COPY --from=build \
    --chown=slotforge:slotforge \
    /workspace/backend/api-service/build/libs/*.jar \
    /app/app.jar

USER slotforge

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]