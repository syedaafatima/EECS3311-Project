# EECS 3311 - AI Job Search and Career Agent Project

EECS3311 Stage 2 implementation of the Stage 1 design.

## Implemented functionality

- Candidate profile creation and updates (F01)
- PDF/DOCX resume import with preview and confirmation (F02)
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

### Try the feature

1. Run `profile edit` to create a profile if you do not already have one (UC02's
   profile precondition).
2. Run `resume import /absolute/path/resume.pdf` or
   `resume import "/absolute/path/my resume.docx"`. Running `resume import` alone
   prompts for the path. Uppercase `.PDF` and `.DOCX` extensions are accepted.
3. Review the extracted text. Enter `yes` to save; any other response cancels.
4. Run `resume list` to see saved IDs, then `resume show` and enter an ID to see
   its stored text. Exit and restart the CLI to check persistence.
5. Try an empty, corrupted, password-protected, or unsupported file. It should
   display an error and leave saved resumes unchanged.
6. For scanned documents, run `resume paste`, paste the resume text, and enter
   a line containing only `.`. Review and confirm as above.

Files must be readable, nonempty PDF/DOCX documents no larger than 20 MiB, and
must contain usable text (at least one letter or digit). Actual document parsers
validate content rather than trusting the filename. Password-protected files
without an empty user password and PDFs that disallow extraction are rejected.
OCR is not included. Complex layout reading order follows the parsing libraries;
review the preview before saving.

### Implementation and Stage 1 traceability

F02 / UC02 / SD01 is deterministic and uses the planned Adapter, Facade, and MVC
collaboration:

- `domain/Resume.java`: immutable resume version with `id`, `originalPath`,
  extracted `text`, SHA-256 `checksum`, and `hasText()`.
- `parser/ResumeParser.java`: Adapter target interface, `extractText(Path)`.
- `parser/DocumentParserAdapter.java`: implements the target and translates
  PDFBox's `PDFTextStripper` and POI's `XWPFWordExtractor` into that contract.
  Library-specific document handling stays in the adapter. It closes documents
  and reports format, corruption, encryption, and missing-text errors.
- `service/CareerFacade.java`: accepts an injectable `ResumeParser`, prepares a
  preview, validates it, and saves the reviewed `Resume` on confirmation through
  `importResume(Resume)`. Existing constructors and workflows remain available.
  A temporary byte snapshot ensures parsing and hashing use the same file
  contents; confirmation never rereads a changed or deleted source file.
- `controller/AgentController.java`: routes preview, paste, confirmation,
  cancellation, list, and show actions. Only one preview is pending per controller;
  failed previews clear it, and only explicit `confirmed=yes` saves it.
- `persistence/Repository.java`: saves each confirmed version as its own JSON
  record and supports `findResume` / `listResumes`. Temporary writes followed by
  a move avoid publishing partial records and preserve earlier resume versions.
- `persistence/JsonSupport.java`: escapes JSON control characters so extracted
  document text round-trips correctly.
- `ui/JobSearchCLI.java`: gathers the path or pasted text, displays the preview,
  asks for confirmation, and exposes saved versions through the shared controller.
- `pom.xml`: reproducible dependency and test configuration.
- `src/test/java/ca/yorku/careeragent/ResumeImportTest.java`: generated real
  PDF/DOCX fixtures, validation, preview/confirmation, persistence, parser
  substitution, CLI checks, and the existing Period 1 verification runner.

The preview and confirmation are separate calls to make SD01's review step
explicit. `resume.paste` implements Stage 1's manual-text alternative. UUID IDs
identify resume versions. Consistent with the existing single-profile repository,
resume versions are stored under `data/resumes/`; a second profile/ownership
system is not introduced. Stored JSON contains the extracted text, original path,
and checksum; it does not archive the original binary document. For pasted
resumes, `originalPath` is `manual-text` and the checksum covers the trimmed UTF-8
text. For file imports it covers the original document bytes. Candidate skills
are unchanged; AI skill extraction belongs to F03.

No GUI or LLM integration is introduced in this commit. A future GUI can use
`resume.preview` / `resume.paste`, display the returned text, then submit
`resume.import` with `confirmed=yes` or `resume.cancel` through the same controller.

### Verification

`mvn test` currently executes 15 tests with assertions enabled, including the
existing Period 1 verification. Coverage includes PDF and DOCX extraction,
paragraphs and tables, Unicode/control-character persistence, invalid and
password-protected files, blank/oversized files, misleading file extensions,
preview cancellation and confirmation, parser replacement, saved versions,
persistence failure, long resume text loaded after restart, and interrupted CLI
input. The long-text and interrupted-input checks reproduce defects found and
fixed during review. `mvn package` builds the application JAR; use `mvn exec:java`
to launch with the required runtime dependencies.
