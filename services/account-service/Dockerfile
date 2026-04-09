FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw .
COPY pom.xml .
COPY services/account-service/pom.xml services/account-service/pom.xml
COPY services/account-service/src/ services/account-service/src/
RUN chmod +x mvnw && ./mvnw package -DskipTests --no-transfer-progress -pl services/account-service -am

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/services/account-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
