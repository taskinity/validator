# 🐳 DOCKERFILE - Apache Camel Groovy Validator
# Multi-stage build for optimized production image

# ═══════════════════════════════════════════════════════════════
# 🏗️ BUILD STAGE - Prepare dependencies and compile
# ═══════════════════════════════════════════════════════════════
FROM openjdk:11-jdk-slim AS builder

LABEL maintainer="Tom Sapletta <tom@sapletta.com>"
LABEL description="Apache Camel Groovy Validator - Build Stage"

# Install build dependencies
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install Groovy
ENV GROOVY_VERSION=4.0.15
RUN wget -q "https://archive.apache.org/dist/groovy/${GROOVY_VERSION}/distribution/apache-groovy-binary-${GROOVY_VERSION}.zip" \
    && unzip -q "apache-groovy-binary-${GROOVY_VERSION}.zip" \
    && mv "groovy-${GROOVY_VERSION}" /opt/groovy \
    && rm "apache-groovy-binary-${GROOVY_VERSION}.zip"

ENV GROOVY_HOME=/opt/groovy
ENV PATH="${GROOVY_HOME}/bin:${PATH}"

# Create app directory
WORKDIR /app

# Copy source files
COPY *.groovy ./
COPY *.properties ./
COPY .env.example .env

# Pre-download dependencies by running a simple validation
RUN mkdir -p data/{input,output,error} logs \
    && echo "Testing dependency download..." \
    && timeout 30 groovy -e "@Grab('org.apache.camel:camel-core:3.20.0'); println 'Dependencies downloaded'" || true

# ═══════════════════════════════════════════════════════════════
# 🚀 RUNTIME STAGE - Minimal production image
# ═══════════════════════════════════════════════════════════════
FROM openjdk:11-jre-slim AS runtime

LABEL maintainer="Tom Sapletta <tom@sapletta.com>"
LABEL description="Apache Camel Groovy Validator"
LABEL version="1.0.0"

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    wget \
    curl \
    unzip \
    dumb-init \
    && rm -rf /var/lib/apt/lists/*

# Install Groovy (runtime only)
ENV GROOVY_VERSION=4.0.15
RUN wget -q "https://archive.apache.org/dist/groovy/${GROOVY_VERSION}/distribution/apache-groovy-binary-${GROOVY_VERSION}.zip" \
    && unzip -q "apache-groovy-binary-${GROOVY_VERSION}.zip" \
    && mv "groovy-${GROOVY_VERSION}" /opt/groovy \
    && rm "apache-groovy-binary-${GROOVY_VERSION}.zip" \
    && rm -rf /opt/groovy/doc /opt/groovy/src

ENV GROOVY_HOME=/opt/groovy
ENV PATH="${GROOVY_HOME}/bin:${PATH}"

# Create non-root user for security
RUN groupadd -r camel && useradd -r -g camel -d /app -s /bin/bash camel

# Set working directory
WORKDIR /app

# Copy application files from builder
COPY --from=builder --chown=camel:camel /app/*.groovy ./
COPY --from=builder --chown=camel:camel /app/*.properties ./
COPY --from=builder --chown=camel:camel /app/.env ./

# Create required directories
RUN mkdir -p data/{input,output,error,archive} logs \
    && chown -R camel:camel /app

# Copy Groovy dependencies from builder (if cached)
COPY --from=builder --chown=camel:camel /root/.groovy /home/camel/.groovy || true

# Health check script
COPY --chown=camel:camel <<EOF /app/healthcheck.sh
#!/bin/bash
curl -f http://localhost:9090/health > /dev/null 2>&1
exit \$?
EOF

RUN chmod +x /app/healthcheck.sh

# Switch to non-root user
USER camel

# Environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:+UseStringDeduplication"
ENV GROOVY_OPTS="-Dfile.encoding=UTF-8"
ENV CAMEL_OPTS=""

# Application configuration
ENV APP_NAME="CamelGroovyValidator"
ENV APP_VERSION="1.0.0"
ENV ENVIRONMENT="production"

# Default directories
ENV INPUT_DIR="/app/data/input"
ENV OUTPUT_DIR="/app/data/output"
ENV ERROR_DIR="/app/data/error"

# Ports
ENV HAWTIO_PORT=8080
ENV HEALTH_PORT=9090

# Expose ports
EXPOSE 8080 9090

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD /app/healthcheck.sh

# Volume mounts
VOLUME ["/app/data", "/app/logs"]

# Entry point with proper signal handling
ENTRYPOINT ["dumb-init", "--"]

# Default command
CMD ["sh", "-c", "echo 'Starting Camel Groovy Validator...' && groovy $JAVA_OPTS $GROOVY_OPTS run.groovy"]

# ═══════════════════════════════════════════════════════════════
# 🏷️ LABELS FOR METADATA
# ═══════════════════════════════════════════════════════════════
LABEL org.opencontainers.image.title="Apache Camel Groovy Validator"
LABEL org.opencontainers.image.description="Endpoint validation system for Apache Camel with Groovy support"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.authors="Tom Sapletta <tom@sapletta.com>"
LABEL org.opencontainers.image.url="https://tom.sapletta.com"
LABEL org.opencontainers.image.source="https://github.com/tom-sapletta-com/camel-groovy-validator"
LABEL org.opencontainers.image.licenses="MIT"

# Technical labels
LABEL org.opencontainers.image.base.name="openjdk:11-jre-slim"
LABEL camel.version="3.20.0"
LABEL groovy.version="4.0.15"
LABEL java.version="11"

# Build information (set during build)
# docker build --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
#              --build-arg VCS_REF=$(git rev-parse --short HEAD) \
#              -t camel-groovy-validator .
ARG BUILD_DATE
ARG VCS_REF
LABEL org.opencontainers.image.created=$BUILD_DATE
LABEL org.opencontainers.image.revision=$VCS_REF

# ═══════════════════════════════════════════════════════════════
# 📝 BUILD INSTRUCTIONS
# ═══════════════════════════════════════════════════════════════

# Basic build:
# docker build -t camel-groovy-validator .

# Advanced build with metadata:
# docker build \
#   --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
#   --build-arg VCS_REF=$(git rev-parse --short HEAD) \
#   -t camel-groovy-validator:latest \
#   -t camel-groovy-validator:1.0.0 .

# Run container:
# docker run -d \
#   --name camel-validator \
#   -p 8080:8080 \
#   -p 9090:9090 \
#   -v $(pwd)/data:/app/data \
#   -v $(pwd)/logs:/app/logs \
#   -e ENVIRONMENT=production \
#   camel-groovy-validator

# Run with custom configuration:
# docker run -d \
#   --name camel-validator \
#   -p 8080:8080 \
#   -p 9090:9090 \
#   -v $(pwd)/data:/app/data \
#   -v $(pwd)/logs:/app/logs \
#   -v $(pwd)/.env:/app/.env \
#   camel-groovy-validator

# Development mode with live reload:
# docker run -it --rm \
#   --name camel-validator-dev \
#   -p 8080:8080 \
#   -p 9090:9090 \
#   -v $(pwd):/app \
#   -e DEVELOPMENT_MODE=true \
#   camel-groovy-validator

# ═══════════════════════════════════════════════════════════════
# 🔧 OPTIMIZATION NOTES
# ═══════════════════════════════════════════════════════════════

# 1. Multi-stage build reduces final image size
# 2. Non-root user improves security
# 3. Health check enables container orchestration
# 4. Volume mounts for data persistence
# 5. Proper signal handling with dumb-init
# 6. Resource limits via JAVA_OPTS
# 7. Metadata labels for image management