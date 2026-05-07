# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY stock/gradle stock/gradle
COPY stock/src stock/src
COPY stock/build.gradle stock/settings.gradle stock/gradlew stock/

RUN chmod +x stock/gradlew
RUN --mount=type=secret,id=gpr_user \
    --mount=type=secret,id=gpr_token \
    mkdir -p /root/.gradle && \
    printf "gpr.user=%s\ngpr.token=%s\n" "$(cat /run/secrets/gpr_user)" "$(cat /run/secrets/gpr_token)" > /root/.gradle/gradle.properties && \
    cd stock && ./gradlew bootJar -x test --no-daemon && \
    rm -f /root/.gradle/gradle.properties

FROM eclipse-temurin:17-jdk
WORKDIR /app

COPY --from=build /workspace/stock/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
