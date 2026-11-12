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
    private final CareerAgent agent;
    private final InterviewService interviews;
    private final ca.yorku.careeragent.deadline.DeadlineManager deadlines;

    public CareerFacade(Repository repository) {
        this(repository, new ToolManager(repository));
    }

    public CareerFacade(Repository repository, ToolManager toolManager) {
        this(repository, toolManager, new DocumentParserAdapter());
    }

    public CareerFacade(Repository repository, ToolManager toolManager, ResumeParser parser) {
        this(repository, toolManager, parser, new ca.yorku.careeragent.llm.ConfiguredLLMClient());
    }

    public CareerFacade(Repository repository, ToolManager toolManager, ResumeParser parser,
            ca.yorku.careeragent.llm.LLMClient llm) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.toolManager = java.util.Objects.requireNonNull(toolManager);
        this.parser = java.util.Objects.requireNonNull(parser);
        this.agent = new CareerAgent(toolManager, llm);
        this.interviews = new InterviewService(repository.records(), agent);
        this.deadlines = new ca.yorku.careeragent.deadline.DeadlineManager(repository);
    }

    public void subscribeDeadlines(ca.yorku.careeragent.deadline.DeadlineObserver observer) {
        deadlines.subscribe(observer);
    }

    public void unsubscribeDeadlines(ca.yorku.careeragent.deadline.DeadlineObserver observer) {
        deadlines.unsubscribe(observer);
    }

    public List<ca.yorku.careeragent.deadline.DeadlineReminder> listDeadlines() { return deadlines.listDeadlines(); }
    public int checkDeadlines() { return deadlines.checkDeadlines().size(); }

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

    public List<ca.yorku.careeragent.domain.SkillProposal> extractSkills(String resumeId) {
        return agent.extractSkills(resumeId);
    }

    /** Merge only selected, evidence-backed proposals; existing approved skills remain intact. */
    public CandidateProfile approveSkills(List<ca.yorku.careeragent.domain.SkillProposal> proposals,
            Set<Integer> selected) {
        if (proposals == null || selected.isEmpty()) {
            throw new IllegalArgumentException("Select at least one proposed skill, or cancel.");
        }
        CandidateProfile profile = toolManager.readProfile();
        Set<String> skills = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        skills.addAll(profile.getSkills());
        for (int index : selected) {
            if (index < 1 || index > proposals.size()) throw new IllegalArgumentException("Invalid proposal number.");
            skills.add(proposals.get(index - 1).skill());
        }
        profile.updateSkills(skills);
        repository.save(profile);
        return profile;
    }

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

    /** Explicit edits retain the posting identity used by applications and reviewed documents. */
    public Job updateJob(String jobId, String title, String company, String description,
            String salary, String location, LocalDate deadline) {
        Job existing = toolManager.readJob(jobId);
        Job details = new Job(existing.getId(), title, company, description, salary, location, deadline);
        repository.findJobByTitleAndCompany(details.getTitle(), details.getCompany()).ifPresent(duplicate -> {
            if (!duplicate.getId().equals(existing.getId())) {
                throw new IllegalArgumentException("Another posting already uses that title and company. Select it or use a different title/company.");
            }
        });
        existing.updateDetails(details.getTitle(), details.getCompany(), details.getDescription(),
                details.getSalary(), details.getLocation(), details.getDeadline());
        repository.save(existing);
        return existing;
    }

    public ca.yorku.careeragent.domain.JobAnalysis analyzeJob(String jobId) {
        return agent.analyzeJob(jobId);
    }

    public Job approveJobAnalysis(ca.yorku.careeragent.domain.JobAnalysis preview, Set<Integer> selected) {
        if (preview == null || selected.isEmpty()) throw new IllegalArgumentException("Select requirements from an analysis preview first.");
        Job job = toolManager.readJob(preview.jobId());
        if (!job.getDescription().equals(preview.sourceDescription())) {
            throw new IllegalArgumentException("The job description changed. Analyze it again before approving.");
        }
        List<ca.yorku.careeragent.domain.Requirement> accepted = new java.util.ArrayList<>();
        for (int index : selected) {
            if (index < 1 || index > preview.requirements().size()) throw new IllegalArgumentException("Invalid requirement number.");
            accepted.add(preview.requirements().get(index - 1));
        }
        job.updateReviewedAnalysis(accepted);
        repository.save(job);
        return job;
    }

    /** Explicit manual fallback; these entries are candidate-supplied, not claimed as AI evidence. */
    public Job saveManualRequirements(String jobId, Set<String> required, Set<String> preferred) {
        if (required.isEmpty() && preferred.isEmpty()) throw new IllegalArgumentException("Enter at least one skill requirement.");
        Job job = toolManager.readJob(jobId);
        job.updateAnalysis(required, preferred);
        repository.save(job);
        return job;
    }

    public Job getJob(String jobId) { return toolManager.readJob(jobId); }

    public List<Job> listJobs() {
        return repository.listJobs();
    }

    public Application trackApplication(String jobId, LocalDate submissionDate, Application.Status requestedStatus) {
        if (submissionDate == null || submissionDate.isAfter(LocalDate.now(java.time.ZoneId.of("America/Toronto")))) {
            throw new IllegalArgumentException("Submission/saved date must not be in the future (Toronto time).");
        }
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
                .sorted(Map.Entry.<Job, Double>comparingByValue(Comparator.reverseOrder()).thenComparing(e -> e.getKey().getId()))
                .toList();

        if (scored.isEmpty()) {
            throw new IllegalArgumentException("No analyzed jobs are available for matching.");
        }
        Map<Job, Double> ranked = new LinkedHashMap<>();
        scored.forEach(entry -> ranked.put(entry.getKey(), entry.getValue()));
        return ranked;
    }

    public ca.yorku.careeragent.domain.Assessment explainGaps(String jobId) {
        var gaps = identifyGaps(jobId); // Input errors are not disguised as provider failures.
        String facts = "Matched: " + gaps.get("matched") + "\nMissing: " + gaps.get("missing") + "\nUncertain: " + gaps.get("uncertain");
        return new ca.yorku.careeragent.domain.Assessment("F06 Skill gaps", facts, optionalExplanation(() -> agent.explainGaps(jobId)));
    }

    public ca.yorku.careeragent.domain.Assessment explainMatches(String strategy) {
        Map<Job, Double> ranked = matchJobs(strategy);
        String facts = ranked.entrySet().stream().map(e -> String.format(java.util.Locale.ROOT,
                "%s | %.1f%% | %s", e.getKey(), e.getValue(), toolManager.compareSkills(e.getKey().getId())))
                .collect(java.util.stream.Collectors.joining("\n"));
        return new ca.yorku.careeragent.domain.Assessment("F07 Ranked jobs", facts, optionalExplanation(() -> agent.explainMatches(ranked)));
    }

    public ca.yorku.careeragent.domain.Assessment compareJobs(List<String> jobIds, String strategy) {
        if (jobIds == null || jobIds.size() < 2 || jobIds.size() > 4 || Set.copyOf(jobIds).size() != jobIds.size()) {
            throw new IllegalArgumentException("Select 2-4 distinct jobs for comparison.");
        }
        List<Job> selected = jobIds.stream().map(toolManager::readJob).toList();
        toolManager.readProfile();
        if ("required".equalsIgnoreCase(strategy)) toolManager.setMatchingStrategy(new RequiredSkillMatching());
        else if (strategy == null || strategy.isBlank() || "weighted".equalsIgnoreCase(strategy)) toolManager.setMatchingStrategy(new WeightedMatching());
        else throw new IllegalArgumentException("Matching strategy must be 'weighted' or 'required'.");
        StringBuilder table = new StringBuilder("Job | Title | Company | Salary | Location | Deadline | Compatibility | Gaps\n");
        for (Job job : selected) {
            table.append(job.getId()).append(" | ").append(job.getTitle()).append(" | ").append(job.getCompany())
                    .append(" | ").append(known(job.getSalary())).append(" | ").append(known(job.getLocation()))
                    .append(" | ").append(job.getDeadline() == null ? "Unknown" : job.getDeadline()).append(" | ")
                    .append(job.isAnalyzed() ? String.format(java.util.Locale.ROOT, "%.1f%%", toolManager.calculateMatch(job.getId())) : "Not analyzed")
                    .append(" | ").append(job.isAnalyzed() ? toolManager.compareSkills(job.getId()) : "Unknown").append('\n');
        }
        String facts = table.toString();
        return new ca.yorku.careeragent.domain.Assessment("F08 Job comparison", facts, optionalExplanation(() -> agent.summarizeComparison(facts)));
    }

    private static String known(String text) { return text == null || text.isBlank() ? "Unknown" : text; }

    private static String optionalExplanation(java.util.function.Supplier<String> request) {
        try { return request.get(); }
        catch (IllegalArgumentException | IllegalStateException e) {
            return "AI explanation unavailable: " + e.getMessage() + " Deterministic results remain available.";
        }
    }


    public ca.yorku.careeragent.domain.CareerDocument suggestResume(String resumeId, String jobId) {
        return agent.suggestResume(resumeId, jobId);
    }
    public ca.yorku.careeragent.domain.CareerDocument draftLetter(String resumeId, String jobId, String tone) {
        return agent.draftLetter(resumeId, jobId, tone);
    }
    public ca.yorku.careeragent.domain.CareerDocument saveDocument(ca.yorku.careeragent.domain.CareerDocument draft, String editedContent) {
        if (draft == null) throw new IllegalArgumentException("Generate or load a document before saving.");
        if (!draft.sourceId().isEmpty() && (draft.type() == ca.yorku.careeragent.domain.CareerDocument.Type.COVER_LETTER
                || draft.type() == ca.yorku.careeragent.domain.CareerDocument.Type.RESUME_SUGGESTIONS)) {
            if (!draft.sourceFingerprint().equals(toolManager.writingFingerprint(draft.sourceId(), draft.jobId()))) {
                throw new IllegalArgumentException("Candidate or job records changed. Regenerate the draft before approval.");
            }
        }
        if (draft.type() == ca.yorku.careeragent.domain.CareerDocument.Type.COMPANY_SUMMARY
                && !draft.sourceFingerprint().equals(toolManager.readNotesById(draft.sourceId()).fingerprint())) {
            throw new IllegalArgumentException("Research notes changed. Generate a fresh summary before approval.");
        }
        if (draft.type() == ca.yorku.careeragent.domain.CareerDocument.Type.CAREER_PLAN
                && !draft.sourceFingerprint().equals(ToolManager.fingerprint(toolManager.planEvidence(List.of(draft.sourceId().split(",")))))) {
            throw new IllegalArgumentException("Profile, target jobs or reviewed advice changed. Generate a fresh plan before approval.");
        }
        var approved = draft.approve(editedContent == null ? draft.content() : editedContent);
        repository.records().put("documents", approved.id(), approved.toRecord());
        return approved;
    }
    public ca.yorku.careeragent.domain.CareerDocument getDocument(String id) {
        return ca.yorku.careeragent.domain.CareerDocument.fromRecord(repository.records().get("documents", id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown document: " + id)));
    }
    public List<ca.yorku.careeragent.domain.CareerDocument> listDocuments() {
        return repository.records().list("documents").stream().map(ca.yorku.careeragent.domain.CareerDocument::fromRecord).toList();
    }


    public ca.yorku.careeragent.domain.QuestionSet generateQuestions(String jobId, int count) { return interviews.generateQuestions(jobId, count); }
    public List<ca.yorku.careeragent.domain.QuestionSet> listQuestionSets() { return interviews.listQuestions(); }
    public ca.yorku.careeragent.domain.QuestionSet getQuestionSet(String id) { return interviews.questions(id); }
    public ca.yorku.careeragent.domain.InterviewSession startInterview(String setId) { return interviews.start(setId); }
    public List<ca.yorku.careeragent.domain.InterviewSession> listInterviews() { return interviews.listSessions(); }
    public ca.yorku.careeragent.domain.InterviewSession getInterview(String id) { return interviews.session(id); }
    public ca.yorku.careeragent.domain.InterviewSession answerInterview(String id, String text, boolean skip) { return interviews.answer(id, text, skip); }
    public ca.yorku.careeragent.domain.InterviewSession transitionInterview(String id, String action) { return interviews.transition(id, action); }
    public ca.yorku.careeragent.domain.InterviewSession interviewFeedback(String id) { return interviews.feedback(id); }


    public ca.yorku.careeragent.domain.CompanyNotes saveCompanyNotes(String jobId, String text, List<String> sources) {
        String company = toolManager.readJob(jobId).getCompany();
        var notes = new ca.yorku.careeragent.domain.CompanyNotes(ca.yorku.careeragent.domain.CompanyNotes.idFor(company), company, text, sources);
        repository.records().put("company-notes", notes.id(), notes.toRecord());
        return notes;
    }
    public ca.yorku.careeragent.domain.CompanyNotes getCompanyNotes(String jobId) { return toolManager.readNotes(jobId); }
    public ca.yorku.careeragent.domain.CareerDocument summarizeCompanyNotes(String jobId) { return agent.summarizeNotes(jobId); }
    public ca.yorku.careeragent.domain.CareerDocument generateCareerPlan(List<String> jobIds, String goals, int hours, java.math.BigDecimal budget) {
        return agent.generateCareerPlan(jobIds, goals, hours, budget);
    }


    public String aiStatus() { return agent.aiStatus(); }
    public String verifyAI() { return agent.verifyAI(); }

}
