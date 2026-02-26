# Tax Core MCP Server (Starter)

Minimal MCP server scaffold for work use, implemented in TypeScript over stdio transport.

## What it provides
- `health_check` tool
- `get_business_analyst_context_index` tool (lists `docs/analysis` Markdown source files)
- `get_business_analyst_context_bundle` tool (loads latest `docs/analysis` docs at runtime)
- `get_architect_context_index` tool (lists `docs/adr` Markdown source files)
- `get_architect_context_bundle` tool (loads latest `docs/adr` docs at runtime)
- `get_role_context_index` tool (lists role-scoped Markdown context files across all roles)
- `get_role_context_bundle` tool (loads role-scoped Markdown context with optional path filtering)
- `add_numbers` tool
- `create_vat_claim_stub` tool (draft Tax Core claim payload)
- `validate_dk_vat_filing` tool (field validation + derived VAT result)
- `evaluate_dk_vat_filing_obligation` tool (obligation and cadence decision)

## Why the context tools matter
- Business analyst context tools read `docs/analysis/**/*.md` on each call.
- Architect context tools read `docs/adr/**/*.md` on each call.
- Role context tools support all roles and enforce role-specific source boundaries.

This means document updates are automatically reflected without changing server code.

## Prerequisites
- Node.js 18+ (you have Node 24)

## Setup
```bash
cd mcp-server
npm install
```

## Run
```bash
npm run dev
```

## Build and run
```bash
npm run build
npm start
```

## How to run tests
No automated test suite is configured yet. Add tests and a `npm test` script before production use.

## Environment variables
| Variable | Required | Default | Description |
|---|---|---|---|
| None | No | - | This server currently has no runtime environment variables |

## Docker
Build the image:
```bash
docker build -t vat-system/mcp-server:local -f mcp-server/Dockerfile .
```

Run the container:
```bash
docker run --rm -p 3000:3000 vat-system/mcp-server:local
```

## Kubernetes
Kubernetes manifests live in `infrastructure/k8s/mcp-server/`:
- `deployment.yaml`
- `service.yaml`
- `configmap.yaml`
- `hpa.yaml`

## Suggested next-session startup sequence for BA mode
1. Call `get_business_analyst_context_index`.
2. Call `get_business_analyst_context_bundle` with `includeContent=true`.
3. Perform analysis using the returned document set as current source of truth.

## Suggested next-session startup sequence for Architect mode
1. Call `get_architect_context_index`.
2. Call `get_architect_context_bundle` with `includeContent=true`.
3. Perform architecture design using the returned document set as current source of truth.

## Tool notes
- `get_business_analyst_context_bundle` inputs:
  - `includeContent` (default: `true`)
  - `maxCharsPerFile` (default: `20000`)
  - `paths` (optional subset of files)

- `get_architect_context_bundle` inputs:
  - `includeContent` (default: `true`)
  - `maxCharsPerFile` (default: `20000`)
  - `paths` (optional subset of files)

Context-scope best practice:
- For role-based work, pass explicit `paths` to bundle tools and avoid loading entire document sets unless explicitly needed.

- `validate_dk_vat_filing` checks:
  - CVR format
  - date validity and period order
  - non-negative value constraints for VAT/rubrik fields
  - zero filing consistency
  - cross-field warnings (for example, abroad VAT with empty Rubrik A)
  - derived `netVatAmount`, `resultType`, `claimAmount`

- `evaluate_dk_vat_filing_obligation` returns:
  - filing required or not
  - cadence (`monthly`, `quarterly`, `half_yearly`)
  - return type (`regular` or `zero`)
  - compliance status (`submitted`, `due`, `overdue`)
  - risk flags

## Example MCP client config (stdio)
Use the absolute path to your built entrypoint if your client requires it.

```json
{
  "mcpServers": {
    "tax-core": {
      "command": "node",
      "args": [
        "C:/Users/tbi/OneDrive - Netcompany/Documents/Projects/VATRI/Codex/mcp-server/dist/index.js"
      ]
    }
  }
}
```
