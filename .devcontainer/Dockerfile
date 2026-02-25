FROM eclipse-temurin:21-jdk-jammy

RUN apt-get update -qq && \
    apt-get install -y -qq git curl unzip nano && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
