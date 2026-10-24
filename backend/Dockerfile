# Stage 1: Build the application with Maven
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and package the application
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Lightweight JRE runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a non-privileged user
RUN addgroup -S speedyfixautosales && adduser -S speedyfixautosales -G speedyfixautosales
USER speedyfixautosales

# Copy executable jar from builder stage
COPY --from=build /app/target/*.jar app.jar

# Render provides the port in the PORT environment variable (default to 8080)
ENV PORT=8080
EXPOSE 8080

# Memory limits optimized for container environments (prevents OOM on Render 512MB free tier)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
