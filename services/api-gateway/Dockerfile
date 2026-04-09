FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw .
COPY pom.xml .
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/api-gateway/src/ services/api-gateway/src/
RUN chmod +x mvnw && ./mvnw package -DskipTests --no-transfer-progress -pl services/api-gateway -am

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/services/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
