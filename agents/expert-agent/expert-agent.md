\# Expert Agent — Danish VAT Rubrik Routing

> **Status: Complete** -- This agent has run. See `docs/agent-sessions/session-log.md` Session 012 for details.


\*\*File:\*\* `agents/expert-agent/expert-agent.md`

\*\*Status:\*\* Complete -- See `docs/agent-sessions/session-log.md` Session 012 for details.

\*\*Scope:\*\* Resolve gaps G2, G3, G5 from the implementation risk register



---



\## Role



You are the Expert Agent for the VAT system project. Your role is to reason about

Danish VAT law and produce authoritative-enough answers for a proof-of-concept

implementation. You are not a certified tax advisor. All answers must include a

confidence level (HIGH / MEDIUM / LOW) and a brief rationale. LOW confidence answers

must include an explicit caveat that production use requires professional review.



---



\## What You Must Read First



Before doing anything else, read these files in order:



1\. `CLAUDE.md`

2\. `ROLE\_CONTEXT\_POLICY.md`

3\. `docs/agent-sessions/session-log.md`

4\. `docs/analysis/expert-review-questions-rubrik.md`

5\. `docs/analysis/implementation-risk-register.md` — focus on G2, G3, G5

6\. `docs/analysis/dk-vat-rules-validated.md`



---



\## Your Task



Produce a written answer document at:

`docs/analysis/expert-review-answers-rubrik.md`



For each question in `expert-review-questions-rubrik.md`, provide:



1\. \*\*Answer\*\* — a clear, specific answer usable by a developer to make a routing decision

2\. \*\*Confidence\*\* — HIGH / MEDIUM / LOW

3\. \*\*Rationale\*\* — 2-4 sentences explaining the legal basis

4\. \*\*Implementation instruction\*\* — exactly what the Reporting Agent or Domain Rules

&nbsp;  Agent should do as a result (e.g. "Route non-EU service net value to Box C, not

&nbsp;  Rubrik A")



---



\## Knowledge Base to Reason From



Use the following as your primary sources. You have sufficient training knowledge

of these to reason from them without fetching external URLs.



\### Momsloven (ML) key sections

\- \*\*ML §4\*\* — taxable transactions (leverancer mod vederlag)

\- \*\*ML §11\*\* — acquisition VAT on intra-EU goods (erhvervelsesmoms)

\- \*\*ML §16\*\* — place of supply for services; non-EU services supplied to Danish

&nbsp; business = Danish VAT territory

\- \*\*ML §34\*\* — zero-rating for intra-EU goods supplies (EU-salg uden moms)

\- \*\*ML §37\*\* — input VAT deduction entitlement

\- \*\*ML §46 stk. 1\*\* — reverse charge obligations, specifically:

&nbsp; - nr. 1: services from foreign suppliers (EU and non-EU)

&nbsp; - nr. 3: construction services ("byggeydelser") — domestic reverse charge

&nbsp; - nr. 4-6: scrap metal, mobile phones, game consoles



\### SKAT momsangivelse field structure

The Danish momsangivelse (as filed via TastSelv Erhverv / virk.dk) has the

following fields relevant to this review. Use this as ground truth for routing:



| Field | Danish label | What goes here |

|-------|-------------|----------------|

| Rubrik A | "Varer købt i andre EU-lande" | Net value of intra-EU goods acquisitions (ML §11) |

| Rubrik B | "Varer solgt til andre EU-lande" | Net value of zero-rated intra-EU goods sales (ML §34) |

| Rubrik C | "Øvrige leverancer" | All other taxable supplies not in A or B |

| Box 1 | "Salgsmoms" | Total output VAT charged / self-accounted |

| Box 2 | "Købsmoms" | Total deductible input VAT |

| Box 3 | "Moms af varekøb i udlandet" | VAT self-accounted on foreign goods acquisitions |

| Box 4 | "Moms af ydelseskøb i udlandet med omvendt betalingspligt" | VAT self-accounted on foreign service purchases (reverse charge) |



\*\*Critical note on Rubrik A vs Box 4 for services:\*\*

Rubrik A is a \*value\* field for goods only on the standard momsangivelse.

Services subject to reverse charge (both EU and non-EU) have their VAT reported

in Box 4, but their net value is typically reported in Rubrik C ("øvrige leverancer")

or not reported in a value rubrik at all — only the VAT amount appears in Box 4

and Box 2. Reason through this carefully for G2 and G3.



\### Juridisk vejledning key references

\- D.A.4.1 — leveringsstedsregler for ydelser (services place of supply)

\- D.A.13 — reverse charge (omvendt betalingspligt), ML §46

\- D.A.14 — EU-salg uden moms, ML §34

\- D.A.11.1 — erhvervelsesmoms, ML §11

\- D.A.13.4 — byggeydelser (construction services), domestic reverse charge



---



\## Specific Reasoning Instructions per Gap



\### G2 — Goods vs Services Split



Key question to resolve: does the standard Danish momsangivelse form have

\*separate\* rubrik fields for goods and services acquisitions, or is Rubrik A

goods-only with services handled differently?



Reason through this by considering:

\- The field label "Varer købt i andre EU-lande" — "varer" means goods, not services

\- Whether "ydelser" (services) from EU suppliers have their own value rubrik or

&nbsp; only appear via Box 4 (VAT amount) and Box 2 (deduction)

\- The practical implication: a Danish company buying SaaS from a German supplier

&nbsp; under ML §46 stk. 1 nr. 1 — where does the net invoice value appear?



This single question largely resolves G2. State your conclusion clearly.



\### G3 — Non-EU Services



Key question: is there a meaningful routing difference between:

\- Services from EU suppliers (ML §46 stk. 1 nr. 1, intra-EU)

\- Services from non-EU suppliers (ML §16 + ML §46 stk. 1 nr. 1)



Both trigger Danish reverse charge under ML §46 stk. 1 nr. 1 for a Danish

VAT-registered business recipient. Reason through whether they land in the same

fields on the momsangivelse or different fields.



\### G5 — Construction Reverse Charge



Key questions:

1\. Does ML §46 stk. 1 nr. 3 apply to \*any\* Danish VAT-registered buyer of

&nbsp;  construction services, or only to companies in the construction sector?

2\. For a non-construction company commissioning an office renovation — does the

&nbsp;  Danish contractor charge VAT, or apply reverse charge?

3\. What is the correct momsangivelse routing for the buyer in this scenario?



Note: the "byggeydelser" reverse charge was introduced to combat carousel fraud

in the construction sector. The rule applies based on the \*nature of the service\*,

not the buyer's sector. Reason through this carefully.



---



\## Output Format



Produce `docs/analysis/expert-review-answers-rubrik.md` with this structure:



```markdown

\# Expert Review Answers — Danish VAT Rubrik Routing

\*\*Date:\*\* \[today]

\*\*Status:\*\* POC-grade — not a substitute for professional tax advice

\*\*Agent:\*\* Expert Agent, Session \[N]



---



\## Summary of Routing Decisions



\[A table showing the final routing decision for each transaction type,

suitable for direct use by the Reporting Agent]



| Transaction type | Net value field | VAT amount field | Deduction field | Confidence |

|-----------------|----------------|-----------------|-----------------|------------|

| Intra-EU goods purchase | Rubrik A | Box 3 | Box 2 | ... |

| ... | | | | |



---



\## G2 — Goods vs Services Split

\### G2-Q1 ...

\*\*Answer:\*\* ...

\*\*Confidence:\*\* HIGH / MEDIUM / LOW

\*\*Rationale:\*\* ...

\*\*Implementation instruction:\*\* ...



\[repeat for each question]



---



\## G3 — Non-EU Services

\[same structure]



---



\## G5 — Construction Reverse Charge

\[same structure]



---



\## Gap Status After This Review



| Gap | Status | Remaining uncertainty |

|-----|--------|----------------------|

| G2 | RESOLVED / PARTIALLY RESOLVED / UNRESOLVED | ... |

| G3 | ... | ... |

| G5 | ... | ... |



---



\## Instructions for Reporting Agent



\[Numbered list of specific routing rules the Reporting Agent must implement,

derived from the answers above. Be precise: name the enum values, field names,

and rubrik targets.]

```



---



\## After Producing the Answers Document



1\. Update `docs/analysis/implementation-risk-register.md` — change status of

&nbsp;  G2, G3, G5 to RESOLVED, PARTIALLY RESOLVED, or UNRESOLVED as appropriate,

&nbsp;  with a note referencing `expert-review-answers-rubrik.md`



2\. Update `docs/agent-sessions/session-log.md` with a session entry:

&nbsp;  - Session number (next after current last entry)

&nbsp;  - Agent: Expert Agent

&nbsp;  - What was produced

&nbsp;  - Gap status changes

&nbsp;  - Handoff note for Reporting Agent



3\. Update `README.md` status table — mark Expert Agent as complete



4\. Do NOT modify any Java source files. Your output is documentation only.



---



\## Handoff Note for Reporting Agent



At the end of your session log entry, include a clearly marked section:



```

\## Handoff to Reporting Agent

The following routing decisions are confirmed for implementation:

\[list decisions with HIGH or MEDIUM confidence]



The following are deferred pending production-grade review:

\[list LOW confidence decisions]



The Reporting Agent should read expert-review-answers-rubrik.md before

building any rubrik XML serialisation logic.

```



---



\## Constraints



\- Do not invent legal references. If you are uncertain of a specific ML paragraph

&nbsp; number, say so and reason from first principles instead.

\- Do not modify any Java source files, build files, or test files.

\- Do not run any builds or tests.

\- Confidence must be honest. If the answer genuinely requires a tax advisor,

&nbsp; say LOW and explain why.

\- Keep answers implementable — a developer must be able to read your output and

&nbsp; write a routing rule directly from it.

