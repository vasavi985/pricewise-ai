# ===================================================
# Stage 1: Build the application with Maven & Java 21 (glibc)
# ===================================================
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml first to leverage Docker layer caching for dependencies
COPY pom.xml .

# Download dependencies offline (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy application source code
COPY src ./src

# Package production executable JAR skipping unit tests
RUN mvn clean package -DskipTests -B

# Ensure a single unambiguous app.jar artifact (ignoring any .original archives)
RUN cp $(ls /app/target/*.jar | grep -v '\.original$' | head -n 1) /app/target/app.jar

# ===================================================
# Stage 2: Production Runtime with Eclipse Temurin 21 JRE (glibc / Ubuntu Jammy)
# Eliminates musl libc incompatibility with grpc-netty-shaded tcnative
# ===================================================
FROM eclipse-temurin:21-jre-jammy

# Metadata
LABEL maintainer="PriceWise AI Team"
LABEL description="PriceWise AI Spring Boot Backend for Render Web Service"

WORKDIR /app

# Create unprivileged system user for security (Debian/Ubuntu syntax)
RUN groupadd -r spring && useradd -r -g spring spring

# Copy packaged JAR from builder stage with unprivileged ownership
COPY --from=builder --chown=spring:spring /app/target/app.jar /app/app.jar

# Switch to non-root user
USER spring:spring

# Default port (Render overrides this with dynamic $PORT e.g. 10000)
ENV PORT=8080

# Production JVM memory tuning optimized for Render free tier (512MB RAM limit)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# Document exposed port
EXPOSE 8080

# Execute Spring Boot application binding explicitly to Render's dynamic PORT
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
