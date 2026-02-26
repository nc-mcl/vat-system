# ─── Stage 1: Build ───────────────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files first for better layer caching
COPY mcp-server/package*.json ./mcp-server/
RUN cd mcp-server && npm ci --only=production

# Copy source and build
COPY mcp-server ./mcp-server
COPY docs/analysis ./docs/analysis
COPY docs/adr ./docs/adr
COPY docs/diagrams ./docs/diagrams
RUN cd mcp-server && npm run build

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────
FROM node:20-alpine AS runtime

WORKDIR /app/mcp-server

# Run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy built output and dependencies
COPY --from=builder /app/mcp-server/dist ./dist
COPY --from=builder /app/mcp-server/node_modules ./node_modules
COPY --from=builder /app/mcp-server/package.json ./

# Copy MCP context documents (loaded at runtime by the server)
COPY --from=builder /app/docs ./docs

USER appuser

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "require('http').get('http://localhost:3000/health', r => process.exit(r.statusCode === 200 ? 0 : 1))"

CMD ["node", "dist/index.js"]
