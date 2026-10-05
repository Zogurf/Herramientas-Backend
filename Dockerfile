# =============================================================================
# MULTI-STAGE DOCKERFILE: SPRING BOOT 3 (JAVA 21) FOR RENDER DEPLOYMENT
# Base Image: Eclipse Temurin 21 (Eclipse Adoptium)
# =============================================================================

# -----------------------------------------------------------------------------
# STAGE 1: BUILD ARCHITECTURE
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven Wrapper and POM first to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

# Copy application source code and compile production JAR
COPY src ./src
RUN ./mvnw clean package -DskipTests

# -----------------------------------------------------------------------------
# STAGE 2: PRODUCTION RUNTIME
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Create non-root user for production security compliance
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Configure JVM container memory management for Render free/paid tiers
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
ENV PORT=8080

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
