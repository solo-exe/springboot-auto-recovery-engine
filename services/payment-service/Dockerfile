FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw .
COPY pom.xml .
COPY services/payment-service/pom.xml services/payment-service/pom.xml
COPY services/payment-service/src/ services/payment-service/src/
RUN chmod +x mvnw && ./mvnw package -DskipTests --no-transfer-progress -pl services/payment-service -am

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/services/payment-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
