# Stage 2 implementation checkpoint

All 17 Stage 1 features have connected implementations in both CLI and Swing GUI.
The selected meaningful code commits cover the minimum for each reporting period.
The weekly commit summary records implementation, tests and supporting repository
changes. Documentation/configuration changes and merge copies are distinguished
from independent meaningful code work.

**Submission remains unfinished:** remaining feature video links, live checks,
actual AI usage/cost figures and final human report/code review are pending.
F01–F13 recordings are linked from the README; F14–F17 links remain pending.
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

GroqAdapter defaults to Groq-hosted
`openai/gpt-oss-20b` on a quota-limited Free plan. ConfiguredLLMClient reloads the
explicit provider choice per request; OpenAIAdapter remains optional. Shared HTTP
translation keeps provider keys isolated and does not automatically fall back after
errors. This is documented as a Stage 1 provider change, with official references
in README and the report. On November 8 UTC (November 7 Toronto), the user configured a private local key
and reported successful CLI `ai verify` output from Groq. Further live feature checks
and recordings are still pending.

## Verification and corrected defects

The desktop-enabled suite passes 80 checks, including actual Swing-window/file-picker
verification and native AI consent/review dialogs. A headless run skips the two native tests. Important fixes cover stale
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

The desktop uses a navy sidebar, consistent typography,
rounded form/result cards, clear primary actions, readable multiline inputs and
buttons that wrap instead of squeezing their labels. Jobs & matching separates
saved-job tools from posting entry. CareerTheme holds presentation helpers while
the views continue using the shared controller. The built-in Swing theme adds no
dependency. Native checks cover all nine workspaces, file-picker cancellation,
button labels and multiline input sizing at 900×650 and 1280×860, and a displayed
65% deterministic ranking. The legacy Period 1 verification fixture now uses the
same Toronto calendar as application-date validation.

## Saved-record and posting editor refinements

Newly saved resumes, jobs, question sets and interviews become selected automatically.
Ordinary refreshes and cancelled imports retain explicit choices. Edit posting loads
the selected job fields and updates by ID, preserving application references even
after a title/company change. New posting clears edit mode. Conflicting identities
are rejected, and description changes invalidate stale analysis. Three additional
GUI regression checks cover these workflows; the current desktop-enabled suite passes 80 checks.

## Profile editor clear action

Clear profile appears beside Save profile and Reload profile. It empties all six
editor fields and focuses Name, preparing a new entry. The last saved profile stays
available through Reload; saving valid new details replaces the single active profile.

The shared GUI/controller persistence check exercises save, clear, reload, a new
candidate save and loading that candidate through the CLI controller. The native
window check confirms all three profile action labels fit and remain visible.
The current desktop-enabled suite passes 80 checks with no failures, errors or skips.
Supplied GUI confirmation also covers F01 save/reload and blank-name validation
on the local Windows copy. Remaining live workflows and recordings stay pending.

## AI source references and corrective generation

F10 cover letters, F11 questions, F13 answer feedback and F16 company summaries
now cite numbered passages from their own retrieved sources. The application
resolves those references to original saved text. Feedback IDs are restricted to
the particular answered question; company URL references are restricted to supplied
sources. Exact quotation-based responses remain supported when their evidence
matches, and saved record formats remain compatible.

F10/F11/F13/F16/F17 permit at most one corrective request after invalid generated
content, including malformed JSON. Each feature retains its own retrieval, schema
and validation branch. Provider/configuration failures, including HTTP 429, are
returned without retry. Unknown references, invalid counts/scores, duplicate
questions and excessive study budgets remain rejected.

The verification includes a complete F01–F17 replay and actual GUI generation,
consent, evidence review and saving for F10/F11/F13/F16/F17. These use controlled,
fallible provider responses; live Groq checks remain pending on the configured local
copy. See the [feature verification record](feature-verification.md).

## Submission records

- [Feature demonstration videos](../README.md#feature-demonstrations)
- [Report source](stage2-report-working-draft.md)
- [Weekly commit summary](weekly-commit-summary.md)
- [Implemented UML](../Diagrams/stage2_class_diagram.png)
- [Feature-specific sequences](../Diagrams/stage2_ai_workflows.md)

Keep actual model/token/money usage from the development-tool and provider billing
records. Those values are not exposed to this workspace and must not be guessed.
Recordings must show real GUI/CLI behavior and live AI where applicable. The report
is deliberately labeled DRAFT until these remaining fields and checks are complete.
