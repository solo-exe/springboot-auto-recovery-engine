# Build stage
FROM maven:3.9.6-eclipse-temurin-21-jammy AS build
WORKDIR /app

# Copy root pom and module poms first to cache dependencies
COPY pom.xml .
COPY services/common-core/pom.xml services/common-core/
COPY services/api-gateway/pom.xml services/api-gateway/
COPY services/payment-service/pom.xml services/payment-service/
COPY services/account-service/pom.xml services/account-service/
COPY services/notification-worker/pom.xml services/notification-worker/
COPY services/spring-boot-admin/pom.xml services/spring-boot-admin/
COPY services/recovery-engine/pom.xml services/recovery-engine/

# Fetch dependencies
RUN mvn dependency:go-offline -pl services/recovery-engine -am

# Copy source code
COPY services/common-core/src services/common-core/src
COPY services/recovery-engine/src services/recovery-engine/src

# Build the application
RUN mvn package -DskipTests -pl services/recovery-engine -am

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /app/services/recovery-engine/target/*.jar app.jar

EXPOSE 8087

ENTRYPOINT ["java", "-jar", "app.jar"]
