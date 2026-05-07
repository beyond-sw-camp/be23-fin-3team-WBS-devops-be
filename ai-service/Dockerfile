# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

ARG COMMON_VERSION=0.0.1-SNAPSHOT

COPY ai-service/gradle ai-service/gradle
COPY ai-service/src ai-service/src
COPY ai-service/build.gradle ai-service/settings.gradle ai-service/gradlew ai-service/

RUN chmod +x ai-service/gradlew
RUN --mount=type=secret,id=gpr_user \
    --mount=type=secret,id=gpr_token \
    mkdir -p /root/.gradle && \
    printf "gpr.user=%s\n" "$(cat /run/secrets/gpr_user)" > /root/.gradle/gradle.properties && \
    printf "gpr.token=%s\n" "$(cat /run/secrets/gpr_token)" >> /root/.gradle/gradle.properties && \
    cd ai-service && \
    ./gradlew bootJar -x test -Pcommon.version=${COMMON_VERSION} --no-daemon && \
    rm -f /root/.gradle/gradle.properties

FROM eclipse-temurin:17-jdk
WORKDIR /app

COPY --from=build /workspace/ai-service/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
