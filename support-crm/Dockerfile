# Stage 1: Build the JAR package using Java 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY support-crm/pom.xml .
COPY support-crm/src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal runtime image with Java 21 JRE
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]