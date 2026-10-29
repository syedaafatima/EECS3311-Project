package ca.yorku.careeragent;

import ca.yorku.careeragent.controller.AgentController;
import ca.yorku.careeragent.domain.Resume;
import ca.yorku.careeragent.parser.DocumentParserAdapter;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;
import ca.yorku.careeragent.service.ToolManager;
import ca.yorku.careeragent.ui.JobSearchCLI;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ResumeImportTest {
    @TempDir Path temp;

    private CareerFacade facade() {
        CareerFacade facade = new CareerFacade(new Repository(temp.resolve("data")));
        facade.updateProfile("Candidate", "University", "Projects", Set.of("Java"), Set.of(), "Toronto");
        return facade;
    }

    private Path pdf(String name, String text, boolean encrypted) throws Exception {
        Path path = temp.resolve(name);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            if (!text.isEmpty()) {
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(40, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            if (encrypted) doc.protect(new StandardProtectionPolicy("owner-password", "user-password", new AccessPermission()));
            doc.save(path.toFile());
        }
        return path;
    }

    private Path docx(String name, String text) throws Exception {
        Path path = temp.resolve(name);
        try (XWPFDocument doc = new XWPFDocument(); var output = Files.newOutputStream(path)) {
            doc.createParagraph().createRun().setText(text);
            var table = doc.createTable(1, 1);
            table.getRow(0).getCell(0).setText("SQL experience");
            doc.write(output);
        }
        return path;
    }

    @Test void pdfImportPreviewsThenPersistsMetadataAndTextAcrossRestart() throws Exception {
        CareerFacade facade = facade();
        Path source = pdf("Resume.PDF", "Candidate Java developer", false);
        Resume preview = facade.previewResume(source);
        assertTrue(preview.hasText());
        assertEquals(source.toAbsolutePath().toString(), preview.getOriginalPath());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))), preview.getChecksum());
        assertTrue(facade.listResumes().isEmpty());
        Files.delete(source); // Confirmation saves the reviewed snapshot, not a reread.
        facade.importResume(preview);
        Resume loaded = new Repository(temp.resolve("data")).findResume(preview.getId()).orElseThrow();
        assertEquals(preview.getText(), loaded.getText());
        assertEquals(preview.getChecksum(), loaded.getChecksum());
        assertEquals(preview.getOriginalPath(), loaded.getOriginalPath());
    }

    @Test void docxExtractsParagraphsTablesAndUnicode() throws Exception {
        CareerFacade facade = facade();
        Resume resume = facade.previewResume(docx("my resume.DOCX", "Zoë Java résumé"));
        assertTrue(resume.getText().contains("Zoë Java résumé"));
        assertTrue(resume.getText().contains("SQL experience"));
        facade.importResume(resume);
        assertEquals(resume.getText(), facade.listResumes().get(0).getText());
    }

    @Test void invalidFilesAndBlankOrEncryptedDocumentsDoNotSave() throws Exception {
        CareerFacade facade = facade();
        Resume existing = facade.importResume(facade.previewResumeText("Original candidate resume"));
        Path unsupported = Files.writeString(temp.resolve("resume.txt"), "Candidate");
        Path empty = Files.createFile(temp.resolve("empty.pdf"));
        Path corruptPdf = Files.writeString(temp.resolve("broken.pdf"), "not a PDF");
        Path corruptDocx = Files.writeString(temp.resolve("broken.docx"), "not a DOCX");
        Path oversized = temp.resolve("large.pdf");
        try (var file = new java.io.RandomAccessFile(oversized.toFile(), "rw")) {
            file.setLength(DocumentParserAdapter.MAX_FILE_BYTES + 1);
        }
        for (Path file : new Path[]{unsupported, empty, corruptPdf, corruptDocx, oversized,
                temp.resolve("missing.pdf"), temp, pdf("blank.pdf", "", false), pdf("secret.pdf", "Candidate", true)}) {
            assertThrows(IllegalArgumentException.class, () -> facade.previewResume(file), file.toString());
        }
        assertEquals(1, facade.listResumes().size());
        assertEquals(existing.getText(), facade.listResumes().get(0).getText());
    }

    @Test void blankDocxRejected() throws Exception {
        Path path = temp.resolve("blank.docx");
        try (XWPFDocument doc = new XWPFDocument(); var output = Files.newOutputStream(path)) { doc.write(output); }
        assertThrows(IllegalArgumentException.class, () -> facade().previewResume(path));
    }

    @Test void missingProfileAndInvalidTextRejected() {
        CareerFacade missing = new CareerFacade(new Repository(temp.resolve("missing-profile")));
        assertThrows(IllegalArgumentException.class, () -> missing.previewResumeText("Candidate"));
        CareerFacade facade = facade();
        for (String text : new String[]{"", " \t\n", "...", "\u200b"}) {
            assertThrows(IllegalArgumentException.class, () -> facade.previewResumeText(text));
        }
        assertThrows(IllegalArgumentException.class, () -> facade.importResume(null));
    }

    @Test void controllerRequiresConfirmationAndClearsStalePreview() throws Exception {
        CareerFacade facade = facade();
        AgentController controller = new AgentController(facade);
        Path path = docx("resume.docx", "Candidate Java");
        assertTrue(controller.submitRequest("resume.preview", Map.of("path", path.toString())).contains("not saved"));
        assertTrue(controller.submitRequest("resume.import", Map.of("confirmed", "no")).contains("cancelled"));
        assertTrue(facade.listResumes().isEmpty());
        controller.submitRequest("resume.preview", Map.of("path", path.toString()));
        assertTrue(controller.submitRequest("resume.preview", Map.of("path", "missing.pdf")).startsWith("ERROR:"));
        assertTrue(controller.submitRequest("resume.import", Map.of("confirmed", "yes")).startsWith("ERROR:"));
        controller.submitRequest("resume.paste", Map.of("text", "Manual candidate Java resume"));
        assertTrue(controller.submitRequest("resume.import", Map.of("confirmed", "yes")).startsWith("Resume saved:"));
        assertEquals(1, facade.listResumes().size());
        assertTrue(controller.submitRequest("resume.show", Map.of("resumeId", facade.listResumes().get(0).getId())).contains("Manual candidate"));
    }

    @Test void parserCanBeReplacedWithoutChangingWorkflow() throws Exception {
        facade();
        Repository repo = new Repository(temp.resolve("data"));
        CareerFacade facade = new CareerFacade(repo, new ToolManager(repo), file -> "Replacement parser text");
        assertEquals("Replacement parser text", facade.previewResume(pdf("sample.pdf", "Candidate", false)).getText());
    }

    @Test void pastedTextRoundTripsQuotesNewlinesAndBackslashesAndKeepsVersions() {
        CareerFacade facade = facade();
        String text = "Candidate \"Zoë\"\nJava\\SQL\tDeveloper\f\b";
        Resume first = facade.importResume(facade.previewResumeText(text));
        facade.importResume(facade.previewResumeText("Updated candidate"));
        assertEquals(2, facade.listResumes().size());
        assertEquals(text, new Repository(temp.resolve("data")).findResume(first.getId()).orElseThrow().getText());
    }

    @Test void persistenceFailureLeavesExistingResumeIntact() throws Exception {
        CareerFacade facade = facade();
        Resume first = facade.importResume(facade.previewResumeText("Original candidate"));
        Path blocked = temp.resolve("blocked");
        Repository repo = new Repository(blocked);
        Files.writeString(blocked.resolve("resumes"), "not a directory");
        assertThrows(IllegalStateException.class, () -> repo.save(first));
        assertEquals(first.getText(), facade.listResumes().get(0).getText());
    }

    @Test void cliHandlesPathsWithSpacesConfirmationCancellationAndPaste() throws Exception {
        CareerFacade facade = facade();
        Path path = pdf("resume with spaces.pdf", "Candidate CLI Java", false);
        String input = "resume import \"" + path + "\"\nno\nresume import " + path
                + "\nyes\nresume paste\nPasted candidate\nJava developer\n.\nyes\nresume list\nexit\n";
        PrintStream previous = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            new JobSearchCLI(new AgentController(facade), new Scanner(input)).run();
        } finally { System.setOut(previous); }
        assertEquals(2, facade.listResumes().size());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Resume import cancelled"));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Resume saved:"));
    }

    @Test void renamedDocumentsAndUnrelatedZipAreRejected() throws Exception {
        CareerFacade facade = facade();
        Path renamedPdf = temp.resolve("renamed.docx");
        Files.copy(pdf("real.pdf", "Candidate", false), renamedPdf);
        Path renamedDocx = temp.resolve("renamed.pdf");
        Files.copy(docx("real.docx", "Candidate"), renamedDocx);
        Path zip = temp.resolve("unrelated.docx");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new java.util.zip.ZipEntry("hello.txt"));
            output.write("Candidate".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        for (Path path : new Path[]{renamedPdf, renamedDocx, zip}) {
            assertThrows(IllegalArgumentException.class, () -> facade.previewResume(path));
        }
        assertTrue(facade.listResumes().isEmpty());
    }

    @Test void cliEndOfInputDoesNotConfirmPreview() throws Exception {
        CareerFacade facade = facade();
        Path path = docx("resume.docx", "Candidate");
        new JobSearchCLI(new AgentController(facade), new Scanner("resume import " + path + "\n")).run();
        assertTrue(facade.listResumes().isEmpty());
    }

    @Test void longResumeTextCanBeLoadedAfterRestart() {
        CareerFacade facade = facade();
        String text = "Java projects, SQL experience, and education\n".repeat(2000).strip();
        Resume saved = facade.importResume(facade.previewResumeText(text));
        Repository reopened = new Repository(temp.resolve("data"));
        assertEquals(text, reopened.findResume(saved.getId()).orElseThrow().getText());
        assertEquals(text, reopened.listResumes().get(0).getText());
    }

    @Test void cliEndOfInputAtResumePromptsCancelsWithoutCrashing() {
        CareerFacade facade = facade();
        for (String input : new String[]{"resume import\n", "resume show\n", "profile edit\nCandidate\n"}) {
            PrintStream previous = System.out;
            try (PrintStream capture = new PrintStream(new ByteArrayOutputStream())) {
                System.setOut(capture);
                assertDoesNotThrow(() -> new JobSearchCLI(new AgentController(facade), new Scanner(input)).run());
            } finally { System.setOut(previous); }
        }
        assertTrue(facade.listResumes().isEmpty());
        assertEquals("Candidate", facade.getProfile().orElseThrow().getName());
    }

    @Test void existingPeriod1FunctionalityStillWorks() throws Exception {
        Period1Verification.main(new String[0]);
    }
}
