# Multi-stage Dockerfile for a Spring Boot (Maven) application using Java 21
# Build stage
FROM eclipse-temurin:21-jdk as build
WORKDIR /workspace

# Copy project files and the Maven wrapper, then run the wrapper to build
COPY mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./
COPY src ./src

# Make the wrapper executable and run it to build the project (skip tests by default)
RUN chmod +x ./mvnw \
    && ./mvnw -B -DskipTests package

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the Spring Boot fat jar from build stage
COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
