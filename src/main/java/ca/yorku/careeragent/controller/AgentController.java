package ca.yorku.careeragent.controller;

import ca.yorku.careeragent.domain.Application;
import ca.yorku.careeragent.domain.CandidateProfile;
import ca.yorku.careeragent.domain.Job;
import ca.yorku.careeragent.service.CareerFacade;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Routes GUI/CLI requests to the shared CareerFacade. */
public class AgentController {
    private final CareerFacade facade;

    public AgentController(CareerFacade facade) {
        this.facade = facade;
    }

    public String submitRequest(String action, Map<String, String> input) {
        try {
            return switch (action) {
                case "profile.update" -> updateProfile(input);
                case "profile.show" -> facade.getProfile().map(CandidateProfile::toString)
                        .orElse("No candidate profile has been saved yet.");
                case "job.import" -> importJob(input);
                case "job.list" -> listJobs();
                case "application.add" -> addApplication(input);
                case "application.update" -> updateApplication(input);
                case "application.list" -> listApplications();
                case "job.gaps" -> formatGaps(input.get("jobId"));
                case "job.match" -> formatMatches(input.get("strategy"));
                default -> "Unknown request: " + action;
            };
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "ERROR: " + e.getMessage();
        }
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
}