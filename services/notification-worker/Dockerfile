FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw .
COPY pom.xml .
COPY services/notification-worker/pom.xml services/notification-worker/pom.xml
COPY services/notification-worker/src/ services/notification-worker/src/
RUN chmod +x mvnw && ./mvnw package -DskipTests --no-transfer-progress -pl services/notification-worker -am

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/services/notification-worker/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
