# AI Job Search and Career Agent — EECS3311

A Java application with a Swing GUI and interactive CLI for candidate profiles,
resumes, jobs, applications, writing, interviews, company research and career plans.
Both interfaces share local JSON records and application services. The implemented
patterns are MVC, Facade, Adapter, Strategy and Observer.

## 1. Prerequisites

- **Java JDK 17 or newer**; Java 21 is tested. Use a full desktop JDK, such as
  [Eclipse Temurin](https://adoptium.net/temurin/releases/), for the GUI.
- **Apache Maven 3.9 or newer**. Follow the
  [Maven installation instructions](https://maven.apache.org/install.html) and ensure
  `java`, `javac` and `mvn` are available on your PATH.
- Internet access for the first dependency download and live AI requests.
- A Groq API key for AI features. Manual profile, parsing, requirements, matching,
  tracking and deadline workflows can be used without a key.

Check the installations:

```sh
java -version
javac -version
mvn -version
```

Download and extract the repository. Open a terminal in the **project root**, the
folder containing `pom.xml`, `src` and `.env.example`. Run the commands below there.

## 2. Dependency installation and build

Maven downloads the libraries and plugins declared in [pom.xml](pom.xml). No manual
JAR installation or separate database server is needed.

| Dependency | Version | Purpose |
|---|---|---|
| Apache PDFBox | 3.0.5 | PDF resume parsing |
| Apache POI | 5.4.1 | DOCX resume parsing |
| Log4j Core | 2.24.3 | Runtime logging support |
| JUnit Jupiter | 5.12.2 | Automated verification |

Build the application and run its tests:

```sh
mvn package
```

This builds **`target/career-agent.jar`**, an executable JAR containing the application
and its runtime dependencies. After building, the CLI runs with Java 17+ directly;
Maven and an IDE are not needed to launch the JAR. Keep the `examples/` folder when
using the shipped sample files.

To run tests separately, use `mvn test`. Tests use injected model responses/local
HTTP endpoints and do not need a real API key. Two optional native-window checks
run when `DISPLAY` is configured and a desktop Java runtime is available; otherwise
they are skipped. The suite now contains 95 checks, including eight standalone CLI
checks and seven comparison-grounding checks: the current headless run passes 93 and skips the two native-window checks.
The earlier desktop-enabled run passed the original 80 checks, including an
end-to-end F01–F17 workflow and native AI consent/review dialogs. See the
[feature verification record](docs/feature-verification.md) for coverage and limits.

## 3. Local configuration

The default provider is **Groq**, using its hosted `openai/gpt-oss-20b` model.
Groq offers a quota-limited Free plan; limits depend on your account.

1. Create an API key at <https://console.groq.com/keys>.
2. Copy `.env.example` to a new file named **`.env`** in the project root. Keep an
   existing private `.env` when updating the project.
3. Edit that new `.env` locally, replacing the key placeholder:

   ```dotenv
   LLM_PROVIDER=groq
   GROQ_API_KEY=YOUR_LOCAL_API_KEY_HERE
   GROQ_MODEL=openai/gpt-oss-20b
   ```

Real keys belong only in private configuration. Keep placeholders in `.env.example`
and keep credentials out of GitHub, source code, screenshots and recordings.
`.env`, local secret files and `data/` are ignored by Git.

Environment variables override `.env`. Provider settings are reloaded for each
request. The default model uses Groq's endpoint and Groq key, despite its `openai/`
name. There is no automatic provider fallback, and live requests use account quotas.

<details>
<summary>Optional OpenAI configuration</summary>

To select OpenAI instead, set these values in private `.env`:

```dotenv
LLM_PROVIDER=openai
OPENAI_API_KEY=YOUR_LOCAL_API_KEY_HERE
OPENAI_MODEL=gpt-4.1-mini
```

OpenAI API billing is separate from a ChatGPT subscription. Only the selected
provider's key is used.

</details>

## 4. GUI and CLI launch commands

Complete the build first. Both interfaces save records in local `data/`, which is
created automatically. Run **one interface at a time** because they share these
files. Records survive an application restart.

When updating from a ZIP, close the application first. Keep your existing private
`.env` and `data/` folder, and copy both into the new project root before launching
if you extract to a new folder. Existing saved records remain compatible.

### CLI

```sh
java -jar target/career-agent.jar
```

`mvn exec:java` remains available as a source-development launch command.
At `career-agent>`, enter `help` to list commands or `exit` to close the CLI.
To check AI setup, enter these commands one at a time **inside the CLI**:

```text
ai status
ai verify
```

Status hides the key value. Verification asks for consent; enter `yes` to make a
small live provider request without candidate/job records.

### Standalone options and task files

```sh
java -jar target/career-agent.jar --help
java -jar target/career-agent.jar --input examples/cli-demo.json --data-dir data/cli-demo
```

The second command works without an API key. Use a fresh `data/cli-demo` directory
for the first run: the example creates its own profile and `J01` posting, saves
manually reviewed Java/SQL/Git requirements, displays **Java matched / SQL missing**,
and ranks the job at **65.0%**. Repeating it in that same example directory updates
the same profile/posting. Use the ordinary `data/` directory for the interactive
GUI/CLI records; `--data-dir` lets an automated run use separate records.

| Option | Supported behavior |
|---|---|
| No `--input` | Interactive commands and answers from standard input |
| `--input FILE` or `-input FILE` | Execute a UTF-8 JSON task file |
| `--input -` | Read one complete JSON task from standard input, ending at EOF; no prompts |
| `--data-dir DIR` | Read/write records in that directory; default `data` |
| `--allow-ai` | Explicit consent to send relevant saved records for batch AI actions |
| `--help` or `-h` | Print options and input format without creating records or contacting AI |

A single request has an action and optional input object:

```json
{"action":"profile.show","input":{}}
```

A multi-step task uses `{"requests":[...]}`, as demonstrated in
[cli-demo.json](examples/cli-demo.json). **Input values are strings**, including
counts, dates and comma-separated IDs/selections. Actions are the controller names
used by both interfaces: for example `profile.update`, `job.import`,
`job.requirements`, `job.gaps`, `job.match`, `resume.preview`, `resume.import`,
`interview.questions` and `career.plan`. Interactive `help` lists the equivalent
feature commands. At most 100 requests and 1 MiB of UTF-8 task input are accepted.
Relative file paths resolve from the terminal's current working directory.

For a live AI explanation, first complete the offline example, configure the
private `.env` as described above, and run:

```sh
java -jar target/career-agent.jar --input examples/cli-ai-task.json --data-dir data/cli-demo --allow-ai
```

This retrieves the saved profile, job and reviewed requirements and sends them to
the selected provider for F06 interpretation. Review the explanation against the
fixed Java/SQL gaps. The live wording can vary. The JSON output has a top-level
`ok` and a `results` array containing each action, its `ok` and its textual `output`.
Batch mode never asks interactive questions. AI consent does not approve generated
skills, requirements or documents; their existing confirmation/selection rules still
apply. Use the interactive interface for hands-on preview and review.

Exit codes are **0** for success, **1** for a failed feature/provider request and
**2** for invalid options, unreadable files or invalid task JSON. A batch stops on
the first failed feature request; earlier successful changes remain saved. Task
shape and AI consent are checked before any batch request runs. Usage/input errors
are JSON on standard error; executed-request results are JSON on standard output.
There is no rollback of already completed requests. Never place API keys in task
files or command-line arguments.

F06/F07/F08 can preserve their deterministic result when optional AI interpretation
fails. In that case the request succeeds with an explicit AI-unavailable warning
inside `output`; inspect the returned facts and warning together.

### GUI

```sh
mvn exec:java "-Dexec.mainClass=ca.yorku.careeragent.ui.JobSearchGUI"
```

The same built JAR also contains the GUI and its dependencies:

```sh
java -cp target/career-agent.jar ca.yorku.careeragent.ui.JobSearchGUI
```

Use the sidebar for Skills profile, Resumes, Jobs & matching, Applications & deadlines,
Writing assistant, Interview preparation, Company research, Career plan and AI setup.
AI setup also provides status and live verification. Close the window to exit.

## 5. Example usage

### Feature demonstrations

Each link opens a separate feature recording at **00:00**. All seventeen features
are indexed below; the recordings collectively show the GUI and CLI.

| Feature | Demonstration | Start |
|---|---|---|
| F01 — Candidate Profile Management | [F01 link](https://youtu.be/a_7-OBFc5FA?t=0) | 00:00 |
| F02 — Resume Import | [F02 link](https://youtu.be/rag1yg8JqYM?t=0) | 00:00 |
| F03 — Resume Skill Extraction | [F03 link](https://youtu.be/uXXMgsYg68I?t=0) | 00:00 |
| F04 — Job Import | [F04 link](https://youtu.be/dseg56TGSqo?t=0) | 00:00 |
| F05 — Job Description Analysis | [F05 link](https://youtu.be/cmS2x1T1Tc8?t=0) | 00:00 |
| F06 — Skill Gap Identification | [F06 link](https://youtu.be/94zQE5TPGRs?t=0) | 00:00 |
| F07 — Job Matching | [F07 link](https://youtu.be/2SiS0G3_02M?t=0) | 00:00 |
| F08 — Job Comparison | [F08 link](https://youtu.be/_R_zGnLrsEs?t=0) | 00:00 |
| F09 — Customized Resume Suggestions | [F09 link](https://youtu.be/xvG0E3s51CQ?t=0) | 00:00 |
| F10 — Cover Letter Drafting | [F10 link](https://youtu.be/LDz5NG0jGC4?t=0) | 00:00 |
| F11 — Interview Question Generation | [F11 link](https://youtu.be/WMgN_87MDAQ?t=0) | 00:00 |
| F12 — Mock Interview | [F12 link](https://youtu.be/4k0ZOYJNH1Y?t=0) | 00:00 |
| F13 — Interview Answer Feedback | [F13 link](https://youtu.be/RddPFQT3lUM?t=0) | 00:00 |
| F14 — Application Tracking | [F14 link](https://youtu.be/nMa3M06CLVc?t=0) | 00:00 |
| F15 — Deadline Tracking | [F15 link](https://youtu.be/dCIjHg0C6sQ?t=0) | 00:00 |
| F16 — Company Research Notes | [F16 link](https://youtu.be/71LjEAsCVNE?t=0) | 00:00 |
| F17 — Career Development Plan | [F17 link](https://youtu.be/QQYZjPArcyo?t=0) | 00:00 |

### CLI: profile, resume and matching

Use fictional data for this example. Lines beginning `career-agent>` are commands;
other lines show answers to prompts. Press Enter for a blank optional response.

```text
career-agent> profile edit
Name: Demo Candidate
Highest education level: BSc Computer Science
Experience: Java coursework and a task-tracker project
Skills (comma-separated): Java, Git
Interests (comma-separated): Software development
Preferred job location: Toronto
```

Import the included resume, review the preview and confirm saving:

```text
career-agent> resume import examples/demo-resume.pdf
Save this resume? [yes/no]: yes
```

Create a posting:

```text
career-agent> job import
Job title: Junior Developer
Company: Example Company
Job description: Java and SQL are required. Git is preferred.
Salary (optional):
Location: Toronto
Deadline YYYY-MM-DD (optional):
career-agent> job list
```

Use the actual ID shown by `job list`; the example below assumes `J01`. Enter
reviewed requirements manually to try matching without an API request:

```text
career-agent> job requirements
Job id: J01
Reviewed required skills (comma-separated): Java, SQL
Reviewed preferred skills (comma-separated): Git
career-agent> job gaps
Job id: J01
career-agent> job match
Strategy [weighted/required]: weighted
```

With those inputs, Java is matched, SQL is missing and the weighted score is 65%.
Use `resume list`, `job list`, `document list`, `interview question-sets` and
`interview list` to find saved IDs. Copy the complete ID before the first `|`.
For multiline input, finish with a line containing only `.`.

### GUI: equivalent workflow

1. In **Skills profile**, enter the candidate details and click **Save profile**.
   **Clear profile** empties the editor; **Reload profile** restores the last save.
   Saving new details replaces the single active profile.
2. In **Resumes**, click **Import PDF / DOCX…**, choose `examples/demo-resume.pdf`,
   review and confirm. A sample DOCX is also included in `examples/`.
3. In **Jobs & matching → Add / update posting**, enter the example job and save.
   Use **New posting** for another job or **Edit posting** to update a saved job
   while retaining its ID and linked applications.
4. Under **Saved jobs**, select the posting. Use **Manual requirements…** with
   required `Java, SQL` and preferred `Git`, then **Skill gaps** or **Rank jobs**.
5. With an API key configured, use **Extract skills…** or **Analyze…**, review
   source evidence and approve selected numbered proposals. In **Writing assistant**,
   select a resume/job, generate a draft, review or edit it, then approve saving.

Additional workflows are listed by `help`:

| Task | Starting commands |
|---|---|
| AI extraction and explanations | `resume extract-skills`, `job analyze`, `job gaps explain`, `job match explain`, `job compare` |
| Reviewed writing | `resume suggest`, `letter draft`, `document list`, `document show` |
| Interviews | `interview questions`, `interview start`, `interview answer`, `interview feedback` |
| Tracking and deadlines | `application add`, `application update`, `application list`, `deadlines list`, `deadlines check` |
| Research and planning | `company notes`, `company summarize`, `career plan` |

AI requests ask for consent before sending relevant saved records. Review generated
claims before approval. Cover letters, interview questions, answer feedback and
company summaries cite saved passages by reference; the app attaches their original
text. These workflows and career planning may make one additional corrective request
using the same sources if generated content fails validation. Provider and
configuration failures, including rate limits, are returned without an automatic retry.
F08 computes numeric salary order and comparison statements from saved records.
The model selects verified fact IDs to highlight; the application renders their
original statements. Plain numeric salaries can be ordered; ranges, currencies
and pay-period strings remain as recorded without an inferred ordering.
Company summaries use supplied notes/sources; the app does
not browse websites or automatically submit applications.

For implementation status and limitations, see the
[implementation checkpoint](docs/stage2-progress.md). Additional records:
[Stage 2 implementation report](stage2-report.pdf),
[commit summary](docs/weekly-commit-summary.md),
[implemented UML](Diagrams/stage2_class_diagram.png).
