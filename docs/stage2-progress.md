# Stage 2 implementation checkpoint — November 1, 2026 (Toronto)

This is a development record for the next four code commits, not the final Stage 2
submission report. The original Stage 1 PDF remains the design baseline.

## Feature accounting

| Stage 1 ID | Current behavior and status | Main files / methods |
|---|---|---|
| F01 Profile | Implemented, deterministic; GUI/CLI validate and save shared profile | AgentController, CareerFacade.updateProfile, CandidateProfile, Repository |
| F02 Resume import | Implemented, deterministic; PDF/DOCX or manual text, preview then confirm, saved versions | DocumentParserAdapter.extractText, CareerFacade.previewResume / importResume, Resume |
| F03 Resume skills | Implemented, AI-based with deterministic evidence checks; select proposals and merge approved skills | CareerAgent.extractSkills, ToolManager.readResume / readProfile, SkillProposal, CareerFacade.approveSkills |
| F04 Job import | Implemented, deterministic; title/company duplicate updates; GUI explicitly labels update behavior | CareerFacade.importJob, Job.updateDetails, Repository |
| F05 Job analysis | Implemented, AI-based with deterministic evidence checks; reviewed skills/education/experience and uncertain statements | CareerAgent.analyzeJob, ToolManager.readJob, JobAnalysis, Requirement, CareerFacade.approveJobAnalysis, Job.updateReviewedAnalysis |
| F06 Gaps | Partial; deterministic matched/missing/uncertain lists work; AI explanation remains outstanding | ToolManager.compareSkills, CareerFacade.identifyGaps |
| F07 Matching | Partial; selectable required/weighted scores and ranking work; AI explanations remain outstanding | MatchingStrategy, RequiredSkillMatching, WeightedMatching, CareerFacade.matchJobs |
| F08 Comparison | Not implemented | Planned |
| F09 Resume suggestions | Not implemented | Planned |
| F10 Cover letters | Not implemented | Planned |
| F11 Interview questions | Not implemented | Planned |
| F12 Mock interviews | Not implemented | Planned |
| F13 Interview feedback | Not implemented | Planned |
| F14 Applications | Implemented core deterministic tracking/history and transition checks; richer date rules still need review | CareerFacade.trackApplication / updateApplicationStatus, Application, Repository |
| F15 Deadlines | Implemented, deterministic; Toronto calendar dates, overdue/unknown listing, active-view reminders | DeadlineManager.listDeadlines / checkDeadlines, DeadlineObserver.onDeadline, DeadlineReminder |
| F16 Company research | Not implemented | Planned |
| F17 Career plan | Not implemented | Planned |

Both `JobSearchGUI` and `JobSearchCLI` expose these current workflows through the
same `AgentController` and `CareerFacade`. This checkpoint does **not** claim the
final minimum of ten completed features or the entire Stage 2 submission is ready.
Every feature still needs a labeled demonstration video/segment and a direct link
in the final report; no videos were fabricated or recorded in this checkpoint.

## Changes from Stage 1 and professor feedback

- **Generic agent loop → feature-specific bounded tasks.** Instead of implementing
  the planned `executeTask(goal, context)`/dynamic `executeTool`, `CareerAgent`
  has separate `extractSkills(resumeId)` and `analyzeJob(jobId)` paths. F03 retrieves
  a profile and immutable saved resume, validates the skills schema, rejects
  unsupported evidence/partial tokens, and merges reviewed selections. F05 retrieves
  a saved posting, validates requirement categories, preserves supporting quotations,
  and rejects approval if the description changed. This follows the professor's
  refinement and makes each feature's validation and errors inspectable.
- **Multi-step agent scope.** Retrieval, LLM extraction/analysis, deterministic
  evidence validation, review, and persistence are connected workflows. This is
  bounded retrieval/tool use; autonomous planning, model-selected tools, run-memory
  persistence, and the remaining grouped AI tasks are not implemented or claimed.
- **Profile ownership → repository records.** This implementation uses one local
  candidate profile and independent versioned resumes/jobs/applications, rather
  than collections owned directly by CandidateProfile. Repository persistence and
  facade coordination serve the same single-candidate workflows; multiuser support
  is outside the current implementation.
- **Job analysis now retains evidence.** `Requirement` and `JobAnalysis` are new
  immutable value types supporting F05's review. Job stores reviewed skill sets
  for existing Strategy code and categorized evidence for display. A description
  change invalidates previous analysis; metadata-only edits retain it.
- **Concrete Observer contract.** `DeadlineManager.subscribe(DeadlineObserver)`,
  `unsubscribe(DeadlineObserver)`, `listDeadlines()`, `checkDeadlines()` use an
  injected Clock; views receive `onDeadline(DeadlineReminder)`. CLI checks on each
  prompt; GUI checks every minute while open. This replaces the abstract planned
  `checkDeadlines(now)`/`notifyObservers(application)` signatures. Reminders are
  in-process and deduplicated per local day, not operating-system notifications.
- **Concrete Adapter contracts.** `ResumeParser.extractText(Path): String` is
  implemented by DocumentParserAdapter; `LLMClient.complete(String, String): String`
  is implemented by OpenAIAdapter. These signatures appear in updated diagrams.
- **Relation labels.** The refined Stage 1 source/export replaces R-number labels
  with role names. `Diagrams/stage2_class_diagram.*` shows only implemented classes
  and relationships. `Diagrams/stage2_ai_workflows.md` separates F03/F05 retrieval,
  validation, missing-input, failure, stale-preview, approval and cancellation
  branches; `Diagrams/stage2_deadlines.md` documents actual Observer lifecycle.
  Other original Stage 1 diagrams remain planned-design references.

## Five implemented patterns

| Pattern | Participants and actual design problem |
|---|---|
| MVC | JobSearchGUI / JobSearchCLI (views), AgentController (input routing and pending review state), CareerFacade / domain / Repository (application model). Both views use the same rules and storage. |
| Facade | CareerFacade coordinates parser, agent, tools, deadline subject and repository; views do not call provider or persistence libraries directly. |
| Adapter | LLMClient / OpenAIAdapter and ResumeParser / DocumentParserAdapter isolate external HTTP/document-library formats. Both adapter families count as one distinct pattern. |
| Strategy | MatchingStrategy, WeightedMatching, RequiredSkillMatching, ToolManager context. Policy choice changes scores without changing feature routing. |
| Observer | DeadlineManager subject, DeadlineObserver contract, JobSearchGUI / JobSearchCLI concrete observers. Notifications reach subscribed views without UI dependencies in deadline rules. |

## Verification and limitations

- A fresh-output `mvn package` build passes all 33 tests (zero failures/errors).
  Automated checks cover file import, persistence/restart, provider HTTP request
  shape/configuration precedence, error redaction, invalid JSON, evidence rejection,
  selective review/cancellation, stale analysis, matching/gaps, deadline timezone and
  subscription rules, GUI form persistence and event-thread responsiveness.
- Tests use injected LLM responses and a local HTTP server. A live OpenAI call has
  not been made: the developer must configure a local key and verify model access
  before recording AI demonstrations. Evidence matching cannot establish whether a
  sentence actually requires a skill; human review is still necessary.
- GUI forms are exercised and rendered headlessly in this environment. A desktop
  window, file picker, and modal approval dialogs must also be tried on a desktop
  when making the GUI demonstration recordings.
- CLI and GUI share `data/` but simultaneous independent processes do not have
  database transactions or file locking; run one interface at a time.
- Local changes must be pushed to the public GitHub repository during the relevant
  reporting period. New commits use their actual dates; no dates/history are altered.
  Use `git log --date=iso-strict --format='%h %ad %s'` and GitHub commit URLs in the
  final weekly summary after upload. Four local commits alone do not establish
  that the GitHub weekly-push requirement has been met.

## AI collaboration record for this checkpoint

Tool: OpenAI Codex. Tasks: repository/design review, F03 OpenAI Adapter and reviewed
skill extraction, F05 job-analysis validation, F15 Observer deadlines, shared Swing
GUI, tests and diagram refinements. Representative user guidance: follow Stage 2
according to Stage 1; remove meaningless relation labels; give grouped AI features
feature-specific retrieval/validation paths. The human must review, understand,
run, and validate these changes. Exact model identifier, total token usage and
monetary cost are not exposed to this workspace; obtain them from the session's
usage/billing record for the final report rather than guessing or reporting zero.
Application runtime model is configurable (`gpt-4.1-mini` by default); this is distinct
from the model used by the development tool.
