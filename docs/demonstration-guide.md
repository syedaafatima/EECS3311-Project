# Stage 2 demonstration recording guide — working draft

The November 7 implementation is pushed to main. Pull the latest code before recording. This checklist is preparation, not evidence that recordings or live AI checks have already happened.

November 8 UTC (November 7 Toronto) checkpoint: the user supplied Windows CLI output showing a successful
live Groq `ai verify` response after configuring a private local key. Connection
setup is complete. Subsequent user-reported checks covered F03/F05/F06/F07;
remaining live checks and all recordings are pending. Keep
the existing private `.env`; do not overwrite it by copying the template again.

Use the actual application and your configured Groq model (or explicitly selected optional provider). Test fixtures, simulated model responses, slides and static screenshots do not substitute for running feature demonstrations. Record both GUI and CLI across your videos.

## Preparation

1. Use a demo checkout with fictional candidate/job details. Keep any existing personal data backed up privately.
2. Install a full desktop Java 17+ JDK and Maven 3.9+. Run `mvn package`.
3. Create a Groq Free-plan account and private key at https://console.groq.com/keys. Free-tier quotas apply; use short demo inputs and pace requests.
4. Copy `.env.example` to local `.env`. Set `LLM_PROVIDER=groq`, `GROQ_MODEL=openai/gpt-oss-20b` and `GROQ_API_KEY` to your private key only there. Never put it in source, GitHub, this report, a recording or this chat.
5. Run `mvn exec:java`, then `ai status` and `ai verify`. A successful connectivity check is necessary but does not replace feature-by-feature demonstrations.
6. Launch the GUI with `mvn exec:java -Dexec.mainClass=ca.yorku.careeragent.ui.JobSearchGUI`. Run one interface at a time because they share local data.
7. Choose a deadline two days after the actual recording date and use today's Toronto date for application tracking. Avoid stale fixed dates.
8. Use the list commands to obtain actual resume, document, question-set and session IDs. Do not copy sample UUIDs from tests.

Safe PDF/DOCX resume examples are in `examples/`; use them instead of private personal documents when recording.

The refreshed desktop uses sidebar navigation. In **Jobs & matching**, use
**Add / update posting** to enter a job and **Saved jobs** for analysis, ranking
and comparison. Maximize the window when recording for readable controls and results.

Safe example candidate: Demo Candidate, BSc Computer Science, Java coursework and a task-tracker project, approved skills Java and Git, software-development interests, Toronto location.

Example J01: Junior Developer at Example Company. Description: "Java and SQL are required. Git is preferred. Work with the team to build data products." Salary/location/deadline may be entered.

Example J02: QA Analyst at Sample Labs. Description: "Python and Git are required. SQL is preferred." Leave salary/location/deadline unknown to demonstrate that missing data is preserved.

## Suggested recordings

You may combine all recordings into one video, but every feature needs a labeled segment and a direct timestamped link in the report. The order below reduces repeated setup.

### Recording A — GUI setup, records, tracking and deterministic comparisons

| Feature | Show invocation/input | Show output and an alternative |
|---|---|---|
| F01 Profile | Skills profile: candidate details, Java/Git | Save and reload; show validation of a blank required field |
| F02 Resume import | Import a PDF/DOCX example with extractable text | Preview, cancel once, then confirm; show saved text/version; demonstrate pasted text for scanned resumes |
| F04 Job import | Import J01 and J02 | Show stored cards/records; update the same title/company to demonstrate duplicate handling |
| F14 Application tracking | Track J01 as SAVED using today's date | Update to APPLIED, show saved history in the running app; reject an invalid transition or future date |
| F15 Deadlines | Check a job due in two days, with another missing a deadline | Show upcoming/unknown listing and reminder; show that a submitted application has no submission reminder |
| F06 deterministic part | Compare approved skills to reviewed J01 requirements | Show matched/missing/uncertain lists; the AI explanation belongs in Recording B |
| F07 deterministic part | Rank reviewed jobs under weighted and required policies | Show numeric results and policy selection; AI explanations belong in Recording B |

For F06/F07, either do the job-analysis segment first or enter explicit reviewed requirements manually and clearly label that input as manual.

### Recording B — evidence, AI interpretations and reviewed writing/research/plans

| Feature | Show invocation/input | Show result/review and error or alternative |
|---|---|---|
| F03 Skills | Resume extraction from a saved example resume | Show live proposed skills and source excerpts; approve only a subset, including JUnit from the example resume; show the new skill in the profile |
| F05 Job analysis | Analyze saved J01 through the live model | Show required/preferred/education/experience/uncertain categories where present and supporting excerpts; selectively approve; show manual fallback as an alternative |
| F06 AI explanation | Explain gaps for J01 | Show the recorded gaps with an AI interpretation/evidence; explain that AI does not own the underlying comparison |
| F07 AI explanation | Explain the computed ranking | Show fixed scores plus model interpretation; avoid presenting the score as an objective hiring probability |
| F08 Comparison | Select exactly J01 and J02 | Show 2–4-job comparison and AI summary, preserving unknown fields; reject a one-job selection |
| F09 Resume suggestions | Select the original resume and reviewed J01 | Show candidate quotations, edit/review advice and save a separate document; reopen the original resume |
| F10 Cover letter | Select candidate/resume/job and professional tone | Show generic greeting, editable live draft, source evidence and explicit approval; reload the saved document |
| F16 Research | Save fictional company notes and a supplied URL such as https://example.com/about | Generate a live summary, show only supplied citations/excerpts, edit/approve and reload; reject a non-HTTP(S) source |
| F17 Career plan | Target J01, specific developer goal, 5 weekly hours, cost budget 0 | Show live activities, known skill gaps, allocated totals and review/save; demonstrate rejection of invalid hours; explain prior reviewed advice as memory |

For writing, distinguish advice to learn a missing skill from claiming to already possess it. Review all model-generated qualifications before approval. The app does not browse company sites or submit applications.

### Recording C — CLI interview preparation and restart behavior

1. Announce **F11 Interview Question Generation**. Run `interview questions`, select a reviewed job, request 2 questions and consent. Show technical/behavioural questions, rubrics and job evidence. Demonstrate rejecting a count outside 1–20.
2. Announce **F12 Mock Interview**. Run `interview question-sets`, copy the actual Q-ID and use `interview start`. Copy the S-ID from the result.
3. Answer the first question using `interview answer`; enter multiple lines if useful, then a line containing only `.`. Show the next question.
4. Pause with `interview pause`. Exit and restart the actual CLI; use `interview show` to demonstrate persisted answers/status, then `interview resume`.
5. Explicitly skip the second question with `interview skip`, then finish. Explain that blank answers need correction or an explicit skip.
6. Announce **F13 Interview Answer Feedback**. Run `interview feedback`, consent and show live strengths, weaknesses, improvements, score and the answer excerpt. Show that the skipped answer has no evaluation.
7. Reopen the saved session and a saved document to demonstrate persistence. Do not expose the local API key.

## Demonstration index worksheet

Replace every pending value with a real accessible URL and exact segment timestamp after recording. Unlisted videos must be accessible without an access request. Test every link while signed out or with an account equivalent to the instructor/TA.

| Feature | Video | Start timestamp | GUI/CLI | Verified accessible |
|---|---|---|---|---|
| F01 Candidate Profile Management | Pending | Pending | Pending | No |
| F02 Resume Import | Pending | Pending | Pending | No |
| F03 Resume Skill Extraction | Pending | Pending | Pending | No |
| F04 Job Import | Pending | Pending | Pending | No |
| F05 Job Description Analysis | Pending | Pending | Pending | No |
| F06 Skill Gap Identification | Pending | Pending | Pending | No |
| F07 Job Matching | Pending | Pending | Pending | No |
| F08 Job Comparison | Pending | Pending | Pending | No |
| F09 Customized Resume Suggestions | Pending | Pending | Pending | No |
| F10 Cover Letter Drafting | Pending | Pending | Pending | No |
| F11 Interview Question Generation | Pending | Pending | Pending | No |
| F12 Mock Interview | Pending | Pending | Pending | No |
| F13 Interview Answer Feedback | Pending | Pending | Pending | No |
| F14 Application Tracking | Pending | Pending | Pending | No |
| F15 Deadline Tracking | Pending | Pending | Pending | No |
| F16 Company Research Notes | Pending | Pending | Pending | No |
| F17 Career Development Plan | Pending | Pending | Pending | No |

## Final report tasks

- Finish live feature checks and human code review; connectivity verification was reported successful November 8 UTC (November 7 Toronto).
- Replace pending video fields with real timestamped URLs.
- Obtain actual development-tool model/usage/cost and provider usage/billing figures; do not invent totals.
- Review the updated implemented UML and feature-specific sequence/use-case diagrams against the final code.
- Retain the five confirmed November 7 code-commit links from the weekly summary in the final report.
- Export the completed implementation report as `stage2-report.pdf` or Word, push it and submit the public repository URL by November 10.
