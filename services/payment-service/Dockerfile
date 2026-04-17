FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw .
COPY pom.xml .
# Copy all poms first to satisfy Maven multi-module validation
COPY services/common-core/pom.xml services/common-core/pom.xml
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/payment-service/pom.xml services/payment-service/pom.xml
COPY services/account-service/pom.xml services/account-service/pom.xml
COPY services/notification-worker/pom.xml services/notification-worker/pom.xml

# Copy source code for the service and its dependencies
COPY services/common-core/src services/common-core/src
COPY services/payment-service/src services/payment-service/src

RUN chmod +x mvnw && ./mvnw package -DskipTests --no-transfer-progress -pl services/payment-service -am

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/services/payment-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
