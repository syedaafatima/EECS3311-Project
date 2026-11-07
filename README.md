# EECS 3311 - AI Job Search and Career Agent Project

EECS3311 Stage 2 implementation of the Stage 1 design.

## Implemented functionality

- Candidate profile creation and updates (F01)
- PDF/DOCX resume import with preview and confirmation (F02)
- Evidence-backed LLM resume skill extraction with selective approval (F03)
- Reviewed LLM job-description analysis and a manual fallback (F05)
- Toronto deadline tracking and active-view Observer reminders (F15)
- Swing GUI and CLI using the same controller and persistence
- Job import and duplicate updates (F04)
- Reviewed skill comparison and Strategy-based matching
- Application tracking and status history (F14)
- Local JSON persistence and a shared controller/facade CLI workflow

## F02 — Resume Import (Commit 6)

Requires Java 17 or newer and Maven 3.9+. The cloud environment has Java 21.
PDFBox 3.0.5 extracts PDF text; Apache POI 5.4.1 extracts DOCX text. Maven
resolves these dependencies from Maven Central. No API key is needed for F02.

Build and run verification:

```sh
mvn test
```

Launch the CLI from the repository root (so local data stays in `data/`):

```sh
mvn exec:java
```

In this prepared cloud environment Maven is available at
`/workspace/tools/apache-maven-3.9.9/bin/mvn`. Use it with
`-s /workspace/tools/maven-settings.xml` to use the configured cloud proxy, e.g.:

```sh
/workspace/tools/apache-maven-3.9.9/bin/mvn -s /workspace/tools/maven-settings.xml test
/workspace/tools/apache-maven-3.9.9/bin/mvn -s /workspace/tools/maven-settings.xml exec:java
```

The Maven compiler runs in-process, so it also works when the cloud image includes
the Java compiler module without a `javac` launcher. The build uses Java 17
source/target options because this image omits the symbol archive needed by
`--release`; verification here runs on Java 21.


### Verification

The current suite executes 33 tests with assertions enabled. The F02 checkpoint
introduced 15 of them, including the
existing Period 1 verification. Coverage includes PDF and DOCX extraction,
paragraphs and tables, Unicode/control-character persistence, invalid and
password-protected files, blank/oversized files, misleading file extensions,
preview cancellation and confirmation, parser replacement, saved versions,
persistence failure, long resume text loaded after restart, and interrupted CLI
input. The long-text and interrupted-input checks reproduce defects found and
fixed during review. `mvn package` builds the application JAR; use `mvn exec:java`
to launch with the required runtime dependencies.

## F03 — Evidence-backed resume skills

Copy `.env.example` to `.env` in the repository root, then replace the placeholder
with your own OpenAI API key locally. `OPENAI_MODEL` defaults to `gpt-4.1-mini`.
Environment variables override `.env`. Both files and environment variables are
read only when an AI feature is invoked; profile/import/tracking work without a key.
`.env` and local data are ignored by Git. Never put credentials in screenshots or videos.

In the CLI, create a profile, import/paste a resume, then use `resume list` to obtain
its ID. Run `resume extract-skills`, consent to sending that resume's text to OpenAI,
and enter its ID. Review the numbered proposals and their verbatim evidence; enter
only the numbers you accept. A blank selection cancels. Unsupported proposals are
removed, duplicates collapse, and approved skills merge with existing profile skills.
Use `profile edit` to enter or edit skills manually if generation fails. Source text
is limited to 60,000 characters for AI tasks; larger resumes remain available locally.

`CareerAgent` retrieves through `ToolManager`, invokes the provider-independent
`LLMClient`, checks evidence against the source, and returns proposals to the shared
controller. `OpenAIAdapter` translates the OpenAI HTTP API (Adapter); saved records
change only after candidate review through the Facade/MVC workflow. No model response
is treated as an executable tool instruction. The automated suite uses fake model
responses and a local HTTP server, never a real key or paid generation.

## F05 — Reviewed job analysis

After `job import`, run `job analyze`, consent to sending the description to OpenAI,
and select a job ID. Review and accept only the numbered requirements you want to
save. Required/preferred skills affect matching; education, experience, and vague
(`UNCERTAIN`) requirements retain their evidence but do not become scored skills.
Unsupported excerpts are discarded. `job show` displays saved evidence and unknown
salary/deadline values. `job requirements` is an explicit manual fallback.

F05 uses `CareerAgent.analyzeJob` with job-specific retrieval/schema validation,
separate from `extractSkills`' resume-specific branch. `JobAnalysis` captures the
source description; `CareerFacade.approveJobAnalysis` rejects stale previews.
Changing a posting's description invalidates its old reviewed requirements. Saved
analysis is reloaded by `Repository`, making the existing `job gaps` and `job match`
commands usable from ordinary imported jobs without modifying JSON by hand.
These checks validate literal evidence, not the model's interpretation of a sentence:
the user must still review whether a skill is actually required or merely mentioned.
F06/F07 currently provide deterministic comparisons/scores; their planned AI
explanations are still outstanding.

## F15 — Deadline tracking (Observer)

`deadlines list` displays outstanding jobs, including overdue and unknown deadlines,
sorted by date. A date is inclusive in `America/Toronto`; it remains due today until
local midnight, including daylight-saving changes. Untracked jobs and SAVED
applications are outstanding; APPLIED and later/closed statuses have fulfilled or
closed the submission task and receive no deadline reminders.

`deadlines check` notifies subscribed views for today through seven days ahead.
The CLI also checks at each prompt. Past, missing, and distant dates do not notify.
`DeadlineManager` is the Observer subject, with concrete `subscribe(DeadlineObserver)`,
`unsubscribe(DeadlineObserver)`, and `checkDeadlines()` operations;
`DeadlineObserver.onDeadline(DeadlineReminder)` is the callback. Views register only
while active. Checks deduplicate by job/deadline for the current local day in memory;
restarting the process allows a fresh reminder. `Clock` injection makes timezone and
boundary verification independent of the machine's current date. No email or external
notifications are sent.

## GUI and current Stage 2 status

Launch the Swing GUI on a desktop with Java 17+ and Maven 3.9+:

```sh
mvn compile exec:java -Dexec.mainClass=ca.yorku.careeragent.ui.JobSearchGUI
```

The default `mvn compile exec:java` starts the CLI. Run from the repository root;
both interfaces use the same local `data/` directory. Use one interface at a time.
The GUI offers Skills profile, Resumes, Jobs & matching, and Applications & deadlines
tabs. Import a resume and review its preview before saving. AI extraction/analysis
asks before sending text to OpenAI, shows source evidence, and lets you approve
only selected numbered proposals. AI work runs in a background worker. Deadline
checks run every minute while the GUI is open, and stop when it closes.

Use `.env.example` as the safe template (`cp .env.example .env` on macOS/Linux;
`Copy-Item .env.example .env` in PowerShell). Enter your real key only in `.env`.
No key is needed for manual profile/requirements editing, file parsing, matching,
tracking, or deadlines. On a machine without a desktop display, use the CLI;
GUI automated tests exercise Swing forms headlessly.

See [the implementation checkpoint](docs/stage2-progress.md) for all 17 Stage 1
features, remaining work, pattern roles, verification limitations, and the response
to Stage 1 feedback. Updated diagrams:

- [Implemented class diagram](Diagrams/stage2_class_diagram.png)
- [Feature-specific F03 and F05 sequences](Diagrams/stage2_ai_workflows.md)
- [Concrete Observer sequence](Diagrams/stage2_deadlines.md)
- [Current GUI/CLI use cases](Diagrams/stage2_use_cases.md)

The original Stage 1 class diagram source/export has refined role labels and
Observer/Adapter signatures. It describes the planned full system; the separate
Stage 2 diagram describes the implemented subset. Other original Stage 1 diagrams
are design references. The Stage 2 report and feature demonstration videos are
still required; this checkpoint is not a completed final submission.

## Period 3: explanations and job comparison (F06–F08)

`job gaps explain` and `job match explain` add an optional AI interpretation to
computed gaps/rankings. `job compare` accepts 2–4 distinct job IDs and a matching
policy; the comparison preserves unknown salary/location/deadline and unanalyzed
compatibility. Corresponding buttons are available in Jobs & matching. Scores and
skill sets are always computed locally. Provider/schema/evidence failures leave the
deterministic result available with an explicit warning. AI explanations are
interpretations requiring review, rather than verified additional job facts.
