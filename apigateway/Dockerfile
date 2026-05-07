FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY apigateway/gradle apigateway/gradle
COPY apigateway/src apigateway/src
COPY apigateway/build.gradle apigateway/settings.gradle apigateway/gradlew apigateway/

RUN chmod +x apigateway/gradlew
RUN cd apigateway && ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jdk
WORKDIR /app

COPY --from=build /workspace/apigateway/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
