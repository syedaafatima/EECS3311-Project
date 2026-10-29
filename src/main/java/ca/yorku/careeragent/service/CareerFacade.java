package ca.yorku.careeragent.service;

import ca.yorku.careeragent.domain.Application;
import ca.yorku.careeragent.domain.CandidateProfile;
import ca.yorku.careeragent.domain.Job;
import ca.yorku.careeragent.domain.Resume;
import ca.yorku.careeragent.parser.DocumentParserAdapter;
import ca.yorku.careeragent.parser.ResumeParser;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.strategy.RequiredSkillMatching;
import ca.yorku.careeragent.strategy.WeightedMatching;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Facade that coordinates application workflows from the Stage 1 design. */
public class CareerFacade {
    private final Repository repository;
    private final ToolManager toolManager;
    private final ResumeParser parser;

    public CareerFacade(Repository repository) {
        this(repository, new ToolManager(repository));
    }

    public CareerFacade(Repository repository, ToolManager toolManager) {
        this(repository, toolManager, new DocumentParserAdapter());
    }

    public CareerFacade(Repository repository, ToolManager toolManager, ResumeParser parser) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.toolManager = java.util.Objects.requireNonNull(toolManager);
        this.parser = java.util.Objects.requireNonNull(parser);
    }

    public CandidateProfile updateProfile(String name, String educationLevel, String experience,
            Set<String> skills, Set<String> interests, String locationPreference) {
        CandidateProfile profile = repository.loadProfile().orElseGet(CandidateProfile::new);
        profile.updateProfile(name, educationLevel, experience, skills, interests, locationPreference);
        repository.save(profile);
        return profile;
    }

    public Optional<CandidateProfile> getProfile() {
        return repository.loadProfile();
    }

    /** Extract an immutable preview; nothing is saved until importResume(preview). */
    public Resume previewResume(Path file) {
        requireProfile();
        DocumentParserAdapter.validateFile(file);
        Path snapshot = null;
        try {
            // Parse and checksum the same bytes, even if the selected file changes later.
            byte[] bytes;
            try (var input = Files.newInputStream(file)) {
                bytes = input.readNBytes((int) DocumentParserAdapter.MAX_FILE_BYTES + 1);
            }
            if (bytes.length == 0 || bytes.length > DocumentParserAdapter.MAX_FILE_BYTES) {
                throw new IllegalArgumentException("Resume must be nonempty and no larger than 20 MiB.");
            }
            String suffix = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")
                    ? ".pdf" : ".docx";
            snapshot = Files.createTempFile("career-resume-", suffix);
            Files.write(snapshot, bytes);
            return checkedResume(file.toAbsolutePath().normalize().toString(), parser.extractText(snapshot), bytes);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read resume file.", e);
        } finally {
            if (snapshot != null) {
                try { Files.deleteIfExists(snapshot); }
                catch (IOException e) { snapshot.toFile().deleteOnExit(); }
            }
        }
    }

    /** Manual text alternative for scanned documents; still requires review and confirmation. */
    public Resume previewResumeText(String text) {
        requireProfile();
        String value = java.util.Objects.requireNonNullElse(text, "");
        if (value.length() > DocumentParserAdapter.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Pasted resume text is too large.");
        }
        return checkedResume("manual-text", value, value.strip().getBytes(StandardCharsets.UTF_8));
    }

    private Resume checkedResume(String source, String text, byte[] bytes) {
        try {
            String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            Resume resume = new Resume("R-" + UUID.randomUUID(), source, text, checksum);
            if (!resume.hasText()) {
                throw new IllegalArgumentException("Resume has no usable text. Use resume paste for scanned resumes.");
            }
            return resume;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    /** Save exactly the preview the user reviewed, without reparsing a potentially changed file. */
    public Resume importResume(Resume reviewedResume) {
        requireProfile();
        if (reviewedResume == null || !reviewedResume.hasText()) {
            throw new IllegalArgumentException("Preview a resume with usable text before saving.");
        }
        repository.save(reviewedResume);
        return reviewedResume;
    }

    public List<Resume> listResumes() { return repository.listResumes(); }

    private void requireProfile() {
        if (repository.loadProfile().isEmpty()) {
            throw new IllegalArgumentException("Create a candidate profile before importing a resume.");
        }
    }

    public Job importJob(String title, String company, String description,
            String salary, String location, LocalDate deadline) {
        Optional<Job> duplicate = repository.findJobByTitleAndCompany(title, company);
        Job job;
        if (duplicate.isPresent()) {
            job = duplicate.get();
            job.updateDetails(title, company, description, salary, location, deadline);
        } else {
            job = new Job(repository.nextJobId(), title, company, description, salary, location, deadline);
        }
        repository.save(job);
        return job;
    }

    public List<Job> listJobs() {
        return repository.listJobs();
    }

    public Application trackApplication(String jobId, LocalDate submissionDate, Application.Status requestedStatus) {
        repository.findJob(jobId).orElseThrow(() -> new IllegalArgumentException("Unknown job: " + jobId));
        Optional<Application> existing = repository.findApplicationByJobId(jobId);
        Application application;
        if (existing.isPresent()) {
            application = existing.get();
            application.updateStatus(requestedStatus);
        } else {
            application = new Application(repository.nextApplicationId(), jobId, submissionDate, requestedStatus);
        }
        repository.save(application);
        return application;
    }

    public Application updateApplicationStatus(String applicationId, Application.Status status) {
        Application application = repository.findApplication(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown application: " + applicationId));
        application.updateStatus(status);
        repository.save(application);
        return application;
    }

    public List<Application> listApplications() {
        return repository.listApplications();
    }

    public Map<String, List<String>> identifyGaps(String jobId) {
        return toolManager.compareSkills(jobId);
    }

    public Map<Job, Double> matchJobs(String strategyName) {
        if ("required".equalsIgnoreCase(strategyName)) {
            toolManager.setMatchingStrategy(new RequiredSkillMatching());
        } else if ("weighted".equalsIgnoreCase(strategyName) || strategyName == null || strategyName.isBlank()) {
            toolManager.setMatchingStrategy(new WeightedMatching());
        } else {
            throw new IllegalArgumentException("Matching strategy must be 'weighted' or 'required'.");
        }

        List<Map.Entry<Job, Double>> scored = repository.listJobs().stream()
                .filter(Job::isAnalyzed)
                .map(job -> Map.entry(job, toolManager.calculateMatch(job.getId())))
                .sorted(Map.Entry.<Job, Double>comparingByValue(Comparator.reverseOrder()))
                .toList();

        if (scored.isEmpty()) {
            throw new IllegalArgumentException("No analyzed jobs are available for matching.");
        }
        Map<Job, Double> ranked = new LinkedHashMap<>();
        scored.forEach(entry -> ranked.put(entry.getKey(), entry.getValue()));
        return ranked;
    }
}