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

To run tests separately, use `mvn test`. Tests use injected model responses/local
HTTP endpoints and do not need a real API key. Two optional native-window checks
run when `DISPLAY` is configured and a desktop Java runtime is available; otherwise
they are skipped. The recorded desktop-enabled run passed all 80 checks, including
an end-to-end F01–F17 workflow and native AI consent/review dialogs. See the
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
mvn exec:java
```

At `career-agent>`, enter `help` to list commands or `exit` to close the CLI.
To check AI setup, enter these commands one at a time **inside the CLI**:

```text
ai status
ai verify
```

Status hides the key value. Verification asks for consent; enter `yes` to make a
small live provider request without candidate/job records.

### GUI

```sh
mvn exec:java "-Dexec.mainClass=ca.yorku.careeragent.ui.JobSearchGUI"
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
Company summaries use supplied notes/sources; the app does
not browse websites or automatically submit applications.

For implementation status and limitations, see the
[implementation checkpoint](docs/stage2-progress.md). Additional records:
[Stage 2 implementation report](stage2-report.pdf),
[commit summary](docs/weekly-commit-summary.md),
[implemented UML](Diagrams/stage2_class_diagram.png).
