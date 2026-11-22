# Feature verification

The suite contains **95 tests** after adding eight standalone CLI checks and seven comparison-grounding checks. The
current headless Maven package run passes **93**, with no failures/errors and two
native-window checks skipped. The earlier desktop-enabled run passed the original
**80 tests with no failures, errors or skips**.
It includes an F01–F17 workflow replay using the shipped demo PDF, real local
persistence, CLI input and application restarts. Two native-window tests exercise
the Swing desktop; one specifically opens and approves the AI consent/review dialogs.

AI test responses are injected or served by local HTTP fixtures. They deliberately
include unsupported quotations, malformed JSON, unknown source references and
over-budget plans before valid responses. These checks establish application
behavior under those conditions; they do not establish live Groq output quality,
API access or available account quota. Live provider verification remains a local
check before recording the final demonstrations.

## Coverage

Every row is exercised by
[FeatureWorkflowTest](../src/test/java/ca/yorku/careeragent/FeatureWorkflowTest.java).
The focused tests below add validation and failure coverage.

| Feature | Verified behavior | Focused checks |
|---|---|---|
| F01 Profile | Save/reload; approved skills; clear editor and replacement profile | JobSearchGUITest: blank fields, clear/reload, persisted data shared with CLI |
| F02 Resume import | Actual demo PDF parsing, preview/cancel/save and immutable versions | ResumeImportTest: PDF/DOCX parsing, missing profile, unusable content and file validation |
| F03 Skill extraction | Grounded proposals; approval merges only selected skills | SkillExtractionTest: unsupported evidence, whole skill tokens and invalid selections |
| F04 Job import | Create/edit/deduplicate postings and retain IDs | JobSearchGUITest: new posting, identity collisions and linked applications |
| F05 Job analysis | Review required/preferred skills before saving | JobAnalysisTest: stale descriptions, categories and invalid selections |
| F06 Skill gaps | Java matched, SQL missing and AI explanation | AssessmentTest: deterministic results and unavailable/unsupported AI explanations |
| F07 Matching | Weighted 65%/35%; required-only 50%; unreviewed jobs excluded | AssessmentTest and JobSearchGUITest: policy selection and deterministic scores |
| F08 Comparison | Java computes salary order and comparison facts; AI selects validated highlights | AssessmentTest and ComparisonGroundingTest: reversed salary claims with real citations, extra prose, malformed/unknown/duplicate IDs, numeric formatting, ranges/pay units, missing fields, selected policies, four-job selection and provider failures |
| F09 Resume suggestions | Evidence-backed separate advice document and human approval | WritingTest: unsupported evidence, cancelled review and stale source fingerprints |
| F10 Cover letter | Resolve original candidate passages; correct invalid response once; review/save | WritingTest: Unicode/long lines, unknown IDs, invalid legacy quotations and provider failures |
| F11 Interview questions | Resolve original job passages; correct malformed JSON; save valid set only | InterviewTest: exact count, category spelling, both kinds, duplicates, missing rubrics and unknown IDs |
| F12 Mock interview | Actual CLI answer/pause/restart/resume/skip/finish; saved state recovered | InterviewTest and JobSearchGUITest: blank answers, paused-state errors and premature finish |
| F13 Answer feedback | Resolve evidence from the correct saved answer; score and feedback persist | InterviewTest: skipped/duplicate answers, 0–100 score bounds, wrong-answer source IDs and retained earlier feedback |
| F14 Applications | SAVED → APPLIED → INTERVIEW with retained history | HardeningTest and JobSearchGUITest: backward transitions, invalid IDs and future dates |
| F15 Deadlines | Upcoming/unknown dates; submitted jobs excluded; Observer delivery deduplicated | DeadlineManagerTest: Toronto dates, reminder window and notification lifecycle |
| F16 Company notes | Save/load supplied notes; resolve original excerpts and URLs; reviewed summary | ResearchPlanTest: unsafe URLs, unknown references, stale notes and provider failures |
| F17 Career plan | Retrieve approved profile/gaps/advice; correct invalid generation within limits; review/save | ResearchPlanTest: malformed JSON, time/cost totals, unknown gaps, vague goals and stale inputs |

## Native desktop and provider boundary

- [DesktopSmokeTest](../src/test/java/ca/yorku/careeragent/DesktopSmokeTest.java)
  checks all nine workspaces, native file-picker cancellation, responsive controls,
  profile actions and displayed deterministic ranking.
- [DesktopAIWorkflowTest](../src/test/java/ca/yorku/careeragent/DesktopAIWorkflowTest.java)
  invokes F10, F11, F13, F16 and F17 using actual Swing buttons and modal dialogs,
  checks source evidence previews, approves documents and verifies persisted results.
- GroqAdapterTest and OpenAIAdapterTest use local HTTP servers to check request
  translation, provider configuration, malformed/incomplete responses and errors
  without using real credentials or paid requests.

The corrected generation workflows, including F08, make at most two provider requests for one
action: the initial request and one correction after invalid generated content.
Provider/configuration failures, including HTTP 429, are returned without automatic
retry or provider switching. Invalid form inputs are checked before model access.
Unknown sources remain rejected; valid older quotation-based responses and existing
saved record formats remain supported.

## Standalone CLI verification

`CommandLineTest` verifies the shipped task file against empty persistent storage,
UTF-8/BOM input on standard input, help without side effects, complete shape/consent
validation before execution, fail-fast execution, invalid options/missing files,
malformed JSON and the 1 MiB input bound.

The actual packaged `target/career-agent.jar` was also launched in a separate JVM
from an isolated working directory. Checks covered interactive standard input,
JSON file/stdin modes, expected Java/SQL gaps and 65% matching, help, missing-file
and invalid-option exit codes, AI-consent rejection, and real PDF/DOCX imports.
The parser checks establish that the executable JAR contains its runtime libraries.
These checks do not make a live provider request or substitute for the paired
GUI-to-CLI video demonstration.

Run the standard suite with **mvn test**. Without a configured desktop display, the
two native-window tests are skipped; the remaining 93 tests can run headlessly.
Test fixtures belong only to test source code. The application still requires a
configured live provider for its AI operations.

## Grounded job comparison

F08 builds a catalog of statements from the selected saved postings and matching
policy. Numeric salary order, compatibility and deadline relations are computed in
Java. The LLM selects distinct catalog IDs to prioritize; only the corresponding
application-generated statements are displayed. Free-form claims and unknown IDs
are rejected, with at most one corrective response request. Provider failures are
not retried, and the recorded table and salary comparisons remain available.

Regression checks reproduce salaries 60,000 versus 75,000 with an AI response that
reverses their order while citing a real excerpt and invents city affordability.
Neither claim reaches the displayed highlights. Additional cases cover equal and
reversed salaries, comma/decimal formatting, unknown values, salary ranges and
entries containing currencies/pay units, missing analyses, both matching policies and
selected subsets of up to four jobs. Bare numeric amounts are ordered; currencies,
ranges and pay-period strings remain uninterpreted. No external living-cost claims
are generated.
