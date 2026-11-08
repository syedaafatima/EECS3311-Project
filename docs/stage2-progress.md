# Stage 2 implementation checkpoint — November 8 UTC / November 7 Toronto, 2026

All 17 Stage 1 features have connected implementations in both CLI and Swing GUI.
Five meaningful Period 3 code commits were pushed to main November 7 at 18:00:29 Toronto.
The five selected code commits end at `0f2b00a`; `6979590` additionally exposes persisted tracker history. Report artifacts follow these code checkpoints.

**Submission remains unfinished:** remaining live feature checks, feature videos and links,
actual AI usage/cost figures and final human report/code review are pending.
Injected provider responses test workflows; they are not claimed as live model demonstrations.

## Feature status

| ID | Feature | Mode | Invocation |
|---|---|---|---|
| F01 | Candidate Profile Management | Deterministic | profile edit / Skills profile |
| F02 | Resume Import | Deterministic | resume import / resume paste / Resumes |
| F03 | Resume Skill Extraction | AI with deterministic validation | resume extract-skills / Resumes |
| F04 | Job Import | Deterministic | job import / Jobs & matching |
| F05 | Job Description Analysis | AI with deterministic validation | job analyze / job requirements / Jobs & matching |
| F06 | Skill Gap Identification | Hybrid | job gaps / job gaps explain |
| F07 | Job Matching | Hybrid | job match / job match explain |
| F08 | Job Comparison | Hybrid | job compare / Compare jobs |
| F09 | Customized Resume Suggestions | AI with review | resume suggest / Writing assistant |
| F10 | Cover Letter Drafting | AI with review | letter draft / Writing assistant |
| F11 | Interview Question Generation | AI with validation | interview questions / Interview preparation |
| F12 | Mock Interview | Hybrid | interview start / answer / skip / pause / resume / finish |
| F13 | Interview Answer Feedback | AI with validation | interview feedback / Interview preparation |
| F14 | Application Tracking | Deterministic | application add / application update / Applications & deadlines |
| F15 | Deadline Tracking | Deterministic | deadlines list / deadlines check |
| F16 | Company Research Notes | Hybrid | company notes / company show / company summarize |
| F17 | Career Development Plan | AI with deterministic constraints and review | career plan / Career plan |

## Architecture and professor feedback

Five patterns are implemented: MVC, Facade, Adapter, Strategy and Observer. CareerFacade
coordinates feature-specific CareerAgent methods, ToolManager retrieval, InterviewService,
ResumeParser, DeadlineManager and Repository/LocalRecords. The bounded tasks retrieve,
generate, validate, review and persist; career planning also retrieves prior reviewed
advice as memory. Autonomous dynamic tool execution is not claimed.

Updated diagrams use role labels and concrete Adapter/Observer operations. AI sequences
include separate feature retrieval/validation/error branches. Original design changes
and reasons are recorded in section 4 of the report source.

## Free-provider change

The user requested a free API option. GroqAdapter now defaults to Groq-hosted
`openai/gpt-oss-20b` on a quota-limited Free plan. ConfiguredLLMClient reloads the
explicit provider choice per request; OpenAIAdapter remains optional. Shared HTTP
translation keeps provider keys isolated and does not automatically fall back after
errors. This is documented as a Stage 1 provider change, with official references
in README and the report. On November 8 UTC (November 7 Toronto), the user configured a private local key
and reported successful CLI `ai verify` output from Groq. Further live feature checks
and recordings are still pending.

## Verification and corrected defects

The desktop-enabled suite passes 61 checks, including actual Swing-window/file-picker
verification. A headless run skips that one native test. Important fixes cover stale
job analysis, cross-process source fingerprints, bracket-containing/long array values,
atomic JSON writes, invalid record IDs, future application dates, interrupted review,
provider-error redaction and displaying saved application history in the interfaces.

On November 8 UTC (November 7 Toronto), the user supplied Windows PowerShell output confirming Temurin
JDK 21.0.13 and Maven 3.10.0, followed by CLI `ai status` selecting Groq and
`ai verify` reporting: "Live LLM verification succeeded. The configured model
returned the expected JSON response." This user-reported connectivity check is
separate from the automated tests and does not establish every feature's live
output quality. No private key was shared.

Subsequent supplied transcripts/screenshots show F01 profile creation and GUI reload;
F02 PDF preview/save and rejection before a profile existed; live F03 skill extraction
with source quotations, approval of only JUnit and a persisted profile update; F04 job
import and a deadline reminder; live F05 categorized requirements and explicit approval;
F06 matched Java/missing SQL with a consistent live AI explanation; and F07 weighted
65%/required 50% scores with a consistent live explanation. These are user-reported
checks, with no video links yet. Other live workflows remain unverified.

## Desktop layout refinement

At the user's request, the desktop uses a navy sidebar, consistent typography,
rounded form/result cards, clear primary actions, readable multiline inputs and
buttons that wrap instead of squeezing their labels. Jobs & matching separates
saved-job tools from posting entry. CareerTheme holds presentation helpers while
the views continue using the shared controller. The built-in Swing theme adds no
dependency. Native checks cover all nine workspaces, file-picker cancellation,
button labels and multiline input sizing at 900×650 and 1280×860, and a displayed
65% deterministic ranking. The legacy Period 1 verification fixture now uses the
same Toronto calendar as application-date validation.

## Submission records

- [Report source](stage2-report-working-draft.md)
- [PDF draft](../stage2-report.pdf) and [editable Word draft](../stage2-report.docx)
- [Weekly commit summary](weekly-commit-summary.md)
- [Demonstration guide and pending link worksheet](demonstration-guide.md)
- [Implemented UML](../Diagrams/stage2_class_diagram.png)
- [Feature-specific sequences](../Diagrams/stage2_ai_workflows.md)

Keep actual model/token/money usage from the development-tool and provider billing
records. Those values are not exposed to this workspace and must not be guessed.
Recordings must show real GUI/CLI behavior and live AI where applicable. The report
is deliberately labeled DRAFT until these remaining fields and checks are complete.
