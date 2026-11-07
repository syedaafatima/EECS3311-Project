package ca.yorku.careeragent.controller;

import ca.yorku.careeragent.domain.Application;
import ca.yorku.careeragent.domain.CandidateProfile;
import ca.yorku.careeragent.domain.Job;
import ca.yorku.careeragent.domain.Resume;
import ca.yorku.careeragent.service.CareerFacade;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Routes GUI/CLI requests to the shared CareerFacade. */
public class AgentController {
    private final CareerFacade facade;
    private ca.yorku.careeragent.domain.CareerDocument pendingDocument;
    private ca.yorku.careeragent.domain.JobAnalysis pendingAnalysis;
    private Resume pendingResume;
    private java.util.List<ca.yorku.careeragent.domain.SkillProposal> pendingSkills;

    public AgentController(CareerFacade facade) {
        this.facade = facade;
    }

    public java.util.Optional<CandidateProfile> getProfile() { return facade.getProfile(); }
    public java.util.List<Job> getJobs() { return facade.listJobs(); }
    public java.util.List<Resume> listResumes() { return facade.listResumes(); }

    public java.util.List<ca.yorku.careeragent.domain.QuestionSet> listQuestionSets() { return facade.listQuestionSets(); }
    public java.util.List<ca.yorku.careeragent.domain.InterviewSession> listInterviews() { return facade.listInterviews(); }

    public ca.yorku.careeragent.domain.CompanyNotes getCompanyNotes(String jobId) { return facade.getCompanyNotes(jobId); }

    public void subscribeDeadlines(ca.yorku.careeragent.deadline.DeadlineObserver observer) {
        facade.subscribeDeadlines(observer);
    }

    public void unsubscribeDeadlines(ca.yorku.careeragent.deadline.DeadlineObserver observer) {
        facade.unsubscribeDeadlines(observer);
    }

    public String submitRequest(String action, Map<String, String> input) {
        try {
            return switch (action) {
                case "ai.status" -> facade.aiStatus();
                case "ai.verify" -> facade.verifyAI();
                case "deadlines.list" -> facade.listDeadlines().stream().map(Object::toString)
                        .collect(Collectors.collectingAndThen(Collectors.joining(System.lineSeparator()),
                                result -> result.isEmpty() ? "No outstanding application deadlines." : result));
                case "deadlines.check" -> "Upcoming deadlines within 7 days: " + facade.checkDeadlines();
                case "profile.update" -> updateProfile(input);
                case "profile.show" -> facade.getProfile().map(CandidateProfile::toString)
                        .orElse("No candidate profile has been saved yet.");
                case "resume.extract-skills" -> extractSkills(input);
                case "skills.approve" -> approveSkills(input);
                case "skills.cancel" -> { pendingSkills = null; yield "Skill extraction cancelled."; }
                case "resume.preview" -> previewResume(input);
                case "resume.paste" -> previewResumeText(input);
                case "resume.import" -> confirmResume(input);
                case "resume.cancel" -> { pendingResume = null; yield "Resume import cancelled."; }
                case "resume.list" -> facade.listResumes().stream()
                        .map(r -> r.getId() + " | " + r.getOriginalPath())
                        .collect(Collectors.collectingAndThen(Collectors.joining(System.lineSeparator()),
                                result -> result.isEmpty() ? "No resumes have been imported yet." : result));
                case "resume.show" -> facade.listResumes().stream()
                        .filter(r -> r.getId().equals(input.get("resumeId"))).findFirst()
                        .map(Resume::toString).orElse("No matching resume found.");
                case "job.analyze" -> analyzeJob(input);
                case "job.analysis.approve" -> approveJobAnalysis(input);
                case "job.analysis.cancel" -> { pendingAnalysis = null; yield "Job analysis cancelled."; }
                case "job.requirements" -> "Manual requirements saved: " + facade.saveManualRequirements(
                        input.get("jobId"), csv(input.get("required")), csv(input.get("preferred")));
                case "job.show" -> showJob(input.get("jobId"));
                case "job.import" -> importJob(input);
                case "job.list" -> listJobs();
                case "application.add" -> addApplication(input);
                case "application.update" -> updateApplication(input);
                case "application.list" -> listApplications();
                case "company.notes" -> facade.saveCompanyNotes(input.get("jobId"), input.get("text"), java.util.List.copyOf(csv(input.get("sources")))).toString();
                case "company.show" -> facade.getCompanyNotes(input.get("jobId")).toString();
                case "company.summarize" -> researchDocument(input, false);
                case "career.plan" -> researchDocument(input, true);
                case "interview.questions" -> facade.generateQuestions(input.get("jobId"), Integer.parseInt(input.getOrDefault("count", "5"))).toString();
                case "interview.question-sets" -> facade.listQuestionSets().stream().map(q -> q.id() + " | job=" + q.jobId() + " | count=" + q.questions().size())
                        .collect(Collectors.joining(System.lineSeparator()));
                case "interview.questions.show" -> facade.getQuestionSet(input.get("questionSetId")).toString();
                case "interview.start" -> facade.startInterview(input.get("questionSetId")).toString();
                case "interview.list" -> facade.listInterviews().stream().map(s -> s.id() + " | job=" + s.questionSet().jobId() + " | " + s.status())
                        .collect(Collectors.joining(System.lineSeparator()));
                case "interview.show" -> facade.getInterview(input.get("sessionId")).toString();
                case "interview.answer", "interview.skip" -> facade.answerInterview(input.get("sessionId"), input.get("answer"), action.equals("interview.skip")).toString();
                case "interview.pause", "interview.resume", "interview.finish" -> facade.transitionInterview(input.get("sessionId"), action.substring("interview.".length())).toString();
                case "interview.feedback" -> facade.interviewFeedback(input.get("sessionId")).toString();
                case "resume.suggest" -> previewDocument("resume", input);
                case "letter.draft" -> previewDocument("letter", input);
                case "document.save" -> saveDocument(input);
                case "document.cancel" -> { pendingDocument = null; yield "Document review cancelled."; }
                case "document.show" -> {
                    pendingDocument = null;
                    pendingDocument = facade.getDocument(input.get("documentId"));
                    yield pendingDocument.toString();
                }
                case "document.list" -> facade.listDocuments().stream().map(d -> d.id() + " | " + d.type() + " | job=" + d.jobId())
                        .collect(Collectors.collectingAndThen(Collectors.joining(System.lineSeparator()), s -> s.isBlank() ? "No reviewed documents saved." : s));
                case "job.gaps.explain" -> facade.explainGaps(input.get("jobId")).toString();
                case "job.match.explain" -> facade.explainMatches(input.get("strategy")).toString();
                case "job.compare" -> facade.compareJobs(java.util.Arrays.stream(input.getOrDefault("jobIds", "").split(","))
                        .map(String::trim).toList(), input.get("strategy")).toString();
                case "job.gaps" -> formatGaps(input.get("jobId"));
                case "job.match" -> formatMatches(input.get("strategy"));
                default -> "Unknown request: " + action;
            };
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String extractSkills(Map<String, String> input) {
        pendingSkills = null;
        pendingSkills = facade.extractSkills(input.get("resumeId"));
        if (pendingSkills.isEmpty()) return "No evidence-backed skills found. Use profile edit for manual entry.";
        StringBuilder out = new StringBuilder("Proposed skills (not saved; select numbers to approve):");
        for (int i = 0; i < pendingSkills.size(); i++) {
            var skill = pendingSkills.get(i);
            out.append(System.lineSeparator()).append(i + 1).append(". ").append(skill.skill())
                    .append(" | Evidence: ").append(skill.evidence());
        }
        return out.toString();
    }

    private String approveSkills(Map<String, String> input) {
        if (pendingSkills == null) throw new IllegalArgumentException("Extract skills before approving them.");
        if (!"yes".equalsIgnoreCase(input.get("confirmed"))) {
            pendingSkills = null;
            return "Skill extraction cancelled.";
        }
        CandidateProfile profile = facade.approveSkills(pendingSkills, proposalNumbers(input.get("selected")));
        pendingSkills = null;
        return "Approved skills saved: " + profile.getSkills();
    }

    private static Set<Integer> proposalNumbers(String value) {
        Set<Integer> selected = new LinkedHashSet<>();
        for (String part : csv(value)) {
            try { selected.add(Integer.parseInt(part)); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Select comma-separated proposal numbers."); }
        }
        return selected;
    }

    private String analyzeJob(Map<String, String> input) {
        pendingAnalysis = null;
        pendingAnalysis = facade.analyzeJob(input.get("jobId"));
        if (pendingAnalysis.requirements().isEmpty()) return "No evidence-backed requirements found. Use job requirements for manual entry.";
        StringBuilder out = new StringBuilder("Proposed requirements (not saved; select numbers to approve):");
        for (int i = 0; i < pendingAnalysis.requirements().size(); i++) {
            var item = pendingAnalysis.requirements().get(i);
            out.append(System.lineSeparator()).append(i + 1).append(". ").append(item.kind()).append(": ")
                    .append(item.text()).append(" | Evidence: ").append(item.evidence());
        }
        return out.toString();
    }

    private String approveJobAnalysis(Map<String, String> input) {
        if (!"yes".equalsIgnoreCase(input.get("confirmed"))) {
            pendingAnalysis = null;
            return "Job analysis cancelled.";
        }
        Job job = facade.approveJobAnalysis(pendingAnalysis, proposalNumbers(input.get("selected")));
        pendingAnalysis = null;
        return "Reviewed job analysis saved: " + job.getId();
    }

    private String showJob(String jobId) {
        Job job = facade.getJob(jobId);
        return job + System.lineSeparator() + job.getDescription() + System.lineSeparator()
                + "Salary: " + (job.getSalary().isBlank() ? "Unknown" : job.getSalary())
                + " | Deadline: " + (job.getDeadline() == null ? "Unknown" : job.getDeadline())
                + System.lineSeparator() + "Required skills: " + job.getRequiredSkills()
                + System.lineSeparator() + "Preferred skills: " + job.getPreferredSkills()
                + System.lineSeparator() + "Reviewed evidence: " + job.getAnalysisDetails();
    }

    private String previewResume(Map<String, String> input) {
        pendingResume = null;
        String path = input.get("path");
        if (path == null || path.isBlank()) throw new IllegalArgumentException("Resume path is required.");
        pendingResume = facade.previewResume(Path.of(path));
        return "Resume preview (not saved):" + System.lineSeparator() + pendingResume;
    }

    private String previewResumeText(Map<String, String> input) {
        pendingResume = null;
        pendingResume = facade.previewResumeText(input.get("text"));
        return "Resume preview (not saved):" + System.lineSeparator() + pendingResume;
    }

    private String confirmResume(Map<String, String> input) {
        if (!"yes".equalsIgnoreCase(input.get("confirmed"))) {
            pendingResume = null;
            return "Resume import cancelled.";
        }
        Resume saved = facade.importResume(pendingResume);
        pendingResume = null;
        return "Resume saved: " + saved.getId();
    }

    private String updateProfile(Map<String, String> input) {
        CandidateProfile profile = facade.updateProfile(input.get("name"), input.get("education"),
                input.get("experience"), csv(input.get("skills")), csv(input.get("interests")), input.get("location"));
        return "Profile saved for " + profile.getName() + ".";
    }

    private String importJob(Map<String, String> input) {
        Job job = facade.importJob(input.get("title"), input.get("company"), input.get("description"),
                input.get("salary"), input.get("location"), parseDate(input.get("deadline"), true));
        return "Job saved: " + job;
    }

    private String listJobs() {
        if (facade.listJobs().isEmpty()) return "No jobs have been imported yet.";
        return facade.listJobs().stream().map(Job::toString).collect(Collectors.joining(System.lineSeparator()));
    }

    private String addApplication(Map<String, String> input) {
        Application application = facade.trackApplication(input.get("jobId"),
                parseDate(input.get("submissionDate"), false), parseStatus(input.get("status")));
        return "Application saved: " + application;
    }

    private String updateApplication(Map<String, String> input) {
        Application application = facade.updateApplicationStatus(input.get("applicationId"), parseStatus(input.get("status")));
        return "Application updated: " + application;
    }

    private String listApplications() {
        if (facade.listApplications().isEmpty()) return "No applications have been tracked yet.";
        return facade.listApplications().stream().map(Application::toString)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String formatGaps(String jobId) {
        Map<String, java.util.List<String>> gaps = facade.identifyGaps(jobId);
        return "Matched: " + gaps.get("matched") + System.lineSeparator()
                + "Missing: " + gaps.get("missing") + System.lineSeparator()
                + "Uncertain: " + gaps.get("uncertain");
    }

    private String formatMatches(String strategy) {
        return facade.matchJobs(strategy).entrySet().stream()
                .map(e -> String.format("%s | %.1f%%", e.getKey(), e.getValue()))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static Application.Status parseStatus(String value) {
        if (value == null || value.isBlank()) return Application.Status.APPLIED;
        try { return Application.Status.valueOf(value.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown application status: " + value); }
    }

    private static LocalDate parseDate(String value, boolean optional) {
        if (value == null || value.isBlank()) {
            if (optional) return null;
            throw new IllegalArgumentException("Date is required and must use YYYY-MM-DD format.");
        }
        try { return LocalDate.parse(value.trim()); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("Date must use YYYY-MM-DD format."); }
    }

    private static Set<String> csv(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null || value.isBlank()) return result;
        for (String item : value.split(",")) if (!item.isBlank()) result.add(item.trim());
        return result;
    }

    public String pendingDocumentContent() { return pendingDocument == null ? "" : pendingDocument.content(); }

    private String previewDocument(String task, Map<String, String> input) {
        pendingDocument = null;
        pendingDocument = "resume".equals(task) ? facade.suggestResume(input.get("resumeId"), input.get("jobId"))
                : facade.draftLetter(input.get("resumeId"), input.get("jobId"), input.getOrDefault("tone", "professional"));
        return pendingDocument.toString();
    }

    private String saveDocument(Map<String, String> input) {
        if (!"yes".equalsIgnoreCase(input.get("confirmed"))) {
            pendingDocument = null;
            return "Document review cancelled.";
        }
        var saved = facade.saveDocument(pendingDocument, input.get("content"));
        pendingDocument = null;
        return "Reviewed document saved: " + saved.id();
    }


    private String researchDocument(Map<String, String> input, boolean careerPlan) {
        pendingDocument = null;
        pendingDocument = careerPlan ? facade.generateCareerPlan(java.util.Arrays.stream(input.getOrDefault("jobIds", "").split(","))
                .map(String::trim).toList(), input.get("goals"), Integer.parseInt(input.getOrDefault("hours", "0")),
                new java.math.BigDecimal(input.getOrDefault("budget", "0"))) : facade.summarizeCompanyNotes(input.get("jobId"));
        return pendingDocument.toString();
    }

}