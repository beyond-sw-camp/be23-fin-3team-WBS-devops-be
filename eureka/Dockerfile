FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY eureka/gradle eureka/gradle
COPY eureka/src eureka/src
COPY eureka/build.gradle eureka/settings.gradle eureka/gradlew eureka/

RUN chmod +x eureka/gradlew
RUN cd eureka && ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jdk
WORKDIR /app

COPY --from=build /workspace/eureka/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8761

ENTRYPOINT ["java", "-jar", "app.jar"]
