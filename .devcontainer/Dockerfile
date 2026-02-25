FROM eclipse-temurin:21-jdk-jammy

# Install base tools
RUN apt-get update -qq && \
    apt-get install -y -qq \
      git \
      curl \
      unzip \
      nano \
      ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Install Node.js 20 using official binary (more reliable than nodesource script)
RUN curl -fsSL https://nodejs.org/dist/v20.11.0/node-v20.11.0-linux-x64.tar.gz \
    | tar -xz -C /usr/local --strip-components=1

# Verify Node.js installed
RUN node --version && npm --version

# Install Claude Code
RUN npm install -g @anthropic-ai/claude-code

# Verify Claude Code installed
RUN claude --version || true

WORKDIR /workspace
