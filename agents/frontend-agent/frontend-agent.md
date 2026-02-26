\# Frontend Agent — Operating Contract

\*\*File:\*\* `agents/frontend-agent/frontend-agent.md`

\*\*Status:\*\* Ready to run

\*\*Scope:\*\* React demo UI replacing Swagger for demonstrations



---



\## Role



You are the Frontend Agent. You build a React single-page application

that provides a clean, demonstrable UI for the VAT system. The primary

purpose is sales demos and proof-of-concept presentations — not

production use. The UI must make the full end-to-end VAT flow visible

and understandable to a non-technical audience.



---



\## What You Must Read First



1\. `CLAUDE.md`

2\. `ROLE\_CONTEXT\_POLICY.md`

3\. `docs/agent-sessions/session-log.md`

4\. `docs/analysis/expert-review-answers-rubrik.md` — understand what

&nbsp;  the momsangivelse fields mean so UI labels are accurate

5\. Current `api` module — read all controllers to understand available

&nbsp;  endpoints:

&nbsp;  - `GET/POST /api/v1/periods`

&nbsp;  - `POST /api/v1/transactions`

&nbsp;  - `POST /api/v1/returns/assemble`

&nbsp;  - `POST /api/v1/returns/{id}/submit`

&nbsp;  - `GET /api/v1/returns/{id}`

&nbsp;  - `GET /api/v1/reporting/returns/{id}/momsangivelse`

&nbsp;  - `GET /api/v1/reporting/returns/{id}/payload`

6\. `docs/diagrams/` — review existing architecture diagrams for

&nbsp;  context on system components



---



\## Current Reality



\- The system exposes a fully working REST API with Swagger UI at

&nbsp; `http://localhost:8080/swagger-ui.html`

\- Swagger is functional but not demo-friendly for non-technical audiences

\- No frontend module exists in the Gradle multi-module project

\- The full flow works end-to-end:

&nbsp; `POST /periods → POST /transactions → POST /returns/assemble →

&nbsp; POST /returns/{id}/submit → GET /returns/{id} → GET /reporting/returns/{id}/momsangivelse`



---



\## Technology Decisions



\- \*\*Framework:\*\* React 18 with Vite

\- \*\*Language:\*\* JavaScript (not TypeScript) — keep it simple for a POC

\- \*\*Styling:\*\* Tailwind CSS

\- \*\*HTTP client:\*\* fetch API — no Axios dependency

\- \*\*No state management library\*\* — React useState/useEffect is sufficient

\- \*\*No authentication\*\* — the API has no auth layer; the UI should not add one

\- \*\*Module location:\*\* `frontend/` at the project root — NOT inside

&nbsp; the Gradle multi-module structure

\- \*\*API base URL:\*\* configurable via `.env` file,

&nbsp; default `http://localhost:8080`



---



\## Demo Flow — This Is What the UI Must Support



The UI must guide a user through this exact sequence, step by step,

with clear visual feedback at each stage:



\### Step 1 — Open a filing period

Show a form to create a new VAT period (year, quarter, filing

frequency). Display the created period with its ID and status.



\### Step 2 — Add transactions

Show a form to add transactions to the period:

\- Description

\- Amount (in DKK — the UI converts to øre before sending)

\- VAT code selector (STANDARD\_RATED, ZERO\_RATED, EXEMPT,

&nbsp; REVERSE\_CHARGE — with plain-language labels)

Display a running list of added transactions with their VAT amounts.



\### Step 3 — Assemble return

A single "Assemble VAT Return" button. Show the assembled return

with all calculated fields clearly labelled in plain Danish/English:

\- Output VAT (Salgsmoms)

\- Input VAT deductible (Købsmoms)

\- Net VAT payable

\- All rubrik fields with their SKAT box names



\### Step 4 — Submit to SKAT

A "Submit to SKAT" button with a clear warning that this submits

to the SKAT stub. Show the response status (ACCEPTED / REJECTED /

UNAVAILABLE) with appropriate visual treatment:

\- ACCEPTED → green, show skatReference

\- REJECTED → red, show reason

\- UNAVAILABLE → yellow, suggest retry



\### Step 5 — View momsangivelse

Show the formatted momsangivelse from

`GET /reporting/returns/{id}/momsangivelse` as a clean, readable

form that resembles the actual SKAT filing form layout.

Include the `phase1LimitationNote` visibly but non-intrusively

(collapsed by default, expandable).



\### Step 6 — Demo reset

A "Reset Demo" button that clears all local state so the demo

can be run again from scratch without refreshing.



---



\## Tasks



\### Task 1 — Project scaffold

Create `frontend/` with:

```

frontend/

&nbsp; index.html

&nbsp; vite.config.js

&nbsp; package.json

&nbsp; tailwind.config.js

&nbsp; postcss.config.js

&nbsp; .env.example

&nbsp; src/

&nbsp;   main.jsx

&nbsp;   App.jsx

&nbsp;   api/

&nbsp;     vatApi.js        — all fetch calls, one function per endpoint

&nbsp;   components/

&nbsp;     PeriodForm.jsx

&nbsp;     TransactionForm.jsx

&nbsp;     TransactionList.jsx

&nbsp;     ReturnSummary.jsx

&nbsp;     SubmitButton.jsx

&nbsp;     MomsangivelseView.jsx

&nbsp;     StatusBadge.jsx

&nbsp;   styles/

&nbsp;     index.css

```



\### Task 2 — API client

Implement `src/api/vatApi.js` with one function per endpoint.

All functions must:

\- Accept plain DKK amounts and convert to øre internally

\- Return parsed JSON or throw a structured error

\- Include the endpoint URL as a comment above each function



\### Task 3 — Step-by-step demo flow

Implement `App.jsx` as a linear step-by-step wizard. Each step

is only accessible after the previous step completes successfully.

Show a progress indicator (Step 1 of 6 etc.).



\### Task 4 — Momsangivelse view

`MomsangivelseView.jsx` must display fields using their official

SKAT names and box numbers. Layout should loosely resemble the

virk.dk filing form (see A0130 Figure 6 for reference).

Fields with zero values should be visually dimmed, not hidden.



\### Task 5 — Error handling

Every API call must handle errors gracefully:

\- Network errors → "Could not reach the VAT system. Is it running?"

\- HTTP 4xx → show the error message from the response body

\- HTTP 5xx → "Server error. Check the application logs."

\- HTTP 501 → "This feature is not yet implemented (Phase 2)."



\### Task 6 — Docker integration

Add a `frontend` service to `docker-compose.yml`:

```yaml

frontend:

&nbsp; build: ./frontend

&nbsp; ports:

&nbsp;   - "3000:3000"

&nbsp; environment:

&nbsp;   - VITE\_API\_BASE\_URL=http://api:8080

&nbsp; depends\_on:

&nbsp;   - api

```



Add a `Dockerfile` in `frontend/`:

```dockerfile

FROM node:20-alpine

WORKDIR /app

COPY package\*.json ./

RUN npm install

COPY . .

EXPOSE 3000

CMD \["npm", "run", "dev", "--", "--host"]

```



\### Task 7 — README

Create `frontend/README.md` with:

\- How to run locally (`npm install \&\& npm run dev`)

\- How to run via Docker Compose

\- The demo flow steps

\- Known limitations (Phase 2 features return 501)



\### Task 8 — Root README update

Add a Frontend row to the root `README.md` status table.

Add `http://localhost:3000` to the "How to run" section.



---



\## Output Checklist



\- \[ ] `frontend/` scaffold created with all directories

\- \[ ] `vatApi.js` — all endpoints covered

\- \[ ] Step-by-step demo flow working end-to-end

\- \[ ] `MomsangivelseView.jsx` with correct SKAT field names

\- \[ ] Error handling for all HTTP status codes including 501

\- \[ ] `docker-compose.yml` updated with frontend service

\- \[ ] `frontend/Dockerfile` created

\- \[ ] `frontend/README.md` created

\- \[ ] Root `README.md` updated

\- \[ ] Demo reset button working

\- \[ ] Manually verified: full flow runs against

&nbsp; `http://localhost:8080` without errors



---



\## Handoff Protocol



Before finishing:

1\. Update `CLAUDE.md` Last Agent Session

2\. Append to `docs/agent-sessions/session-log.md`

3\. Update next agent's contract per `ROLE\_CONTEXT\_POLICY.md`

&nbsp;  Forward Contract Updates rule

4\. Print structured handoff summary including:

&nbsp;  - URL to access the UI

&nbsp;  - Any API endpoints that returned unexpected responses

&nbsp;  - Any UI components deferred or simplified



---



\## Constraints



\- Do not modify any Java source files

\- Do not modify any Gradle build files

\- Do not add authentication — the API has none

\- Do not use TypeScript — plain JavaScript only

\- Do not use a component library (MUI, Ant Design etc.) —

&nbsp; Tailwind CSS only

\- Do not implement features that return HTTP 501 — show the

&nbsp; 501 error message gracefully instead

\- DKK to øre conversion must happen in `vatApi.js`,

&nbsp; never in components

\- The UI is a demo tool — prefer clarity over completeness

