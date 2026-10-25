package ca.yorku.careeragent.domain;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Stores a job posting and its reviewed analysis. */
public class Job {
    private final String id;
    private String title;
    private String company;
    private String description;
    private String salary;
    private String location;
    private LocalDate deadline;
    private final Set<String> requiredSkills = new LinkedHashSet<>();
    private final Set<String> preferredSkills = new LinkedHashSet<>();

    public Job(String id, String title, String company, String description,
            String salary, String location, LocalDate deadline) {
        this.id = requireText(id, "Job id");
        updateDetails(title, company, description, salary, location, deadline);
    }

    public void updateDetails(String title, String company, String description,
            String salary, String location, LocalDate deadline) {
        this.title = requireText(title, "Job title");
        this.company = requireText(company, "Company");
        this.description = requireText(description, "Job description");
        this.salary = Objects.requireNonNullElse(salary, "").trim();
        this.location = Objects.requireNonNullElse(location, "").trim();
        this.deadline = deadline;
    }

    public void updateAnalysis(Set<String> requiredSkills, Set<String> preferredSkills) {
        this.requiredSkills.clear();
        this.preferredSkills.clear();
        if (requiredSkills != null) {
            requiredSkills.stream().filter(Objects::nonNull).map(String::trim)
                    .filter(s -> !s.isEmpty()).forEach(this.requiredSkills::add);
        }
        if (preferredSkills != null) {
            preferredSkills.stream().filter(Objects::nonNull).map(String::trim)
                    .filter(s -> !s.isEmpty()).forEach(this.preferredSkills::add);
        }
    }

    public boolean isAnalyzed() {
        return !requiredSkills.isEmpty() || !preferredSkills.isEmpty();
    }

    private static String requireText(String value, String fieldName) {
        String cleaned = Objects.requireNonNullElse(value, "").trim();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return cleaned;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getCompany() { return company; }
    public String getDescription() { return description; }
    public String getSalary() { return salary; }
    public String getLocation() { return location; }
    public LocalDate getDeadline() { return deadline; }
    public Set<String> getRequiredSkills() { return Set.copyOf(requiredSkills); }
    public Set<String> getPreferredSkills() { return Set.copyOf(preferredSkills); }

    @Override
    public String toString() {
        return id + " | " + title + " | " + company + " | " + location;
    }
}