# Build Stage
FROM maven:3.8-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY .env .
RUN mvn clean package

# Run Stage
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/fix.acceptor-1.0-SNAPSHOT.jar app.jar
COPY --from=build /app/src/main/resources/acceptor.cfg .
COPY --from=build /app/.env .
# Ensure necessary directories exist
RUN mkdir -p log data store

ENTRYPOINT ["java", "-jar", "app.jar"]
