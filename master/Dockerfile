# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
ARG COMMON_VERSION=0.0.1-SNAPSHOT

COPY master/gradle master/gradle
COPY master/src master/src
COPY master/build.gradle master/settings.gradle master/gradlew master/

RUN chmod +x master/gradlew
RUN --mount=type=secret,id=gpr_user \
    --mount=type=secret,id=gpr_token \
    mkdir -p /root/.gradle && \
    printf "gpr.user=%s\ngpr.token=%s\n" "$(cat /run/secrets/gpr_user)" "$(cat /run/secrets/gpr_token)" > /root/.gradle/gradle.properties && \
    cd master && ./gradlew bootJar -x test -Pcommon.version=${COMMON_VERSION} --no-daemon && \
    rm -f /root/.gradle/gradle.properties

FROM eclipse-temurin:17-jdk
WORKDIR /app

COPY --from=build /workspace/master/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
