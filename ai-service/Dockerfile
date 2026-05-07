FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY common/gradle common/gradle
COPY common/src common/src
COPY common/build.gradle common/settings.gradle common/gradlew common/

RUN chmod +x common/gradlew
RUN cd common && ./gradlew publishToMavenLocal -x test --no-daemon

COPY ai-service/gradle ai-service/gradle
COPY ai-service/src ai-service/src
COPY ai-service/build.gradle ai-service/settings.gradle ai-service/gradlew ai-service/

RUN chmod +x ai-service/gradlew
RUN cd ai-service && ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jdk
WORKDIR /app

COPY --from=build /workspace/ai-service/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
