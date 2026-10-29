package ca.yorku.careeragent.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Adapts PDFBox and Apache POI to the application's ResumeParser contract. */
public class DocumentParserAdapter implements ResumeParser {
    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    public static void validateFile(Path file) {
        if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new IllegalArgumentException("Select a readable resume file.");
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".pdf") && !name.endsWith(".docx")) {
            throw new IllegalArgumentException("Resume format must be PDF or DOCX.");
        }
        try {
            long size = Files.size(file);
            if (size == 0 || size > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("Resume must be nonempty and no larger than 20 MiB.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read resume file.", e);
        }
    }

    @Override
    public String extractText(Path file) {
        validateFile(file);
        try {
            String text;
            if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                try (PDDocument document = Loader.loadPDF(file.toFile())) {
                    if (!document.getCurrentAccessPermission().canExtractContent()) {
                        throw new IllegalArgumentException("This PDF does not permit text extraction.");
                    }
                    text = new PDFTextStripper().getText(document);
                }
            } else {
                try (var input = Files.newInputStream(file);
                     XWPFDocument document = new XWPFDocument(input);
                     XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    text = extractor.getText();
                }
            }
            if (text == null || text.codePoints().noneMatch(Character::isLetterOrDigit)) {
                throw new IllegalArgumentException("No usable text found. Scanned resumes require OCR outside this app or use resume paste.");
            }
            return text.strip();
        } catch (IOException | org.apache.poi.ooxml.POIXMLException e) {
            throw new IllegalArgumentException("Could not parse resume: corrupted, encrypted, or invalid PDF/DOCX file.", e);
        }
    }
}
