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
COPY services/spring-boot-admin/pom.xml services/spring-boot-admin/pom.xml
COPY services/recovery-engine/pom.xml services/recovery-engine/pom.xml

# Copy source code for the service and its dependencies
COPY services/common-core/src services/common-core/src
COPY services/notification-worker/src services/notification-worker/src

RUN chmod +x mvnw && ./mvnw package -DskipTests --no-transfer-progress -pl services/notification-worker -am

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/services/notification-worker/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
