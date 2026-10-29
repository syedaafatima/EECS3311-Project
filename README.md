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
