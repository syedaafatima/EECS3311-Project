package ca.yorku.careeragent.persistence;

import ca.yorku.careeragent.domain.CandidateProfile;
import ca.yorku.careeragent.domain.Job;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Loads and saves local JSON records used by application workflows. */
public class Repository {
    private final Path dataDirectory;
    private final Path jobsDirectory;

    public Repository(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.jobsDirectory = dataDirectory.resolve("jobs");
        try {
            Files.createDirectories(jobsDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create data directory: " + dataDirectory, e);
        }
    }

    public void save(CandidateProfile profile) {
        String json = "{\n" +
                "  \"name\": " + JsonSupport.quote(profile.getName()) + ",\n" +
                "  \"educationLevel\": " + JsonSupport.quote(profile.getEducationLevel()) + ",\n" +
                "  \"experience\": " + JsonSupport.quote(profile.getExperience()) + ",\n" +
                "  \"skills\": " + JsonSupport.arrayOfStrings(profile.getSkills()) + ",\n" +
                "  \"interests\": " + JsonSupport.arrayOfStrings(profile.getInterests()) + ",\n" +
                "  \"locationPreference\": " + JsonSupport.quote(profile.getLocationPreference()) + "\n" +
                "}\n";
        write(dataDirectory.resolve("profile.json"), json);
    }

    public Optional<CandidateProfile> loadProfile() {
        Path file = dataDirectory.resolve("profile.json");
        if (!Files.exists(file)) return Optional.empty();
        String json = read(file);
        return Optional.of(new CandidateProfile(
                JsonSupport.stringField(json, "name"),
                JsonSupport.stringField(json, "educationLevel"),
                JsonSupport.stringField(json, "experience"),
                new LinkedHashSet<>(JsonSupport.stringArrayField(json, "skills")),
                new LinkedHashSet<>(JsonSupport.stringArrayField(json, "interests")),
                JsonSupport.stringField(json, "locationPreference")));
    }

    public void save(Job job) {
        String deadline = job.getDeadline() == null ? "null" : JsonSupport.quote(job.getDeadline().toString());
        String json = "{\n" +
                "  \"id\": " + JsonSupport.quote(job.getId()) + ",\n" +
                "  \"title\": " + JsonSupport.quote(job.getTitle()) + ",\n" +
                "  \"company\": " + JsonSupport.quote(job.getCompany()) + ",\n" +
                "  \"description\": " + JsonSupport.quote(job.getDescription()) + ",\n" +
                "  \"salary\": " + JsonSupport.quote(job.getSalary()) + ",\n" +
                "  \"location\": " + JsonSupport.quote(job.getLocation()) + ",\n" +
                "  \"deadline\": " + deadline + ",\n" +
                "  \"requiredSkills\": " + JsonSupport.arrayOfStrings(job.getRequiredSkills()) + ",\n" +
                "  \"preferredSkills\": " + JsonSupport.arrayOfStrings(job.getPreferredSkills()) + "\n" +
                "}\n";
        write(jobsDirectory.resolve(job.getId() + ".json"), json);
    }

    public Optional<Job> findJob(String id) {
        Path file = jobsDirectory.resolve(id + ".json");
        if (!Files.exists(file)) return Optional.empty();
        return Optional.of(readJob(file));
    }

    public List<Job> listJobs() {
        try (var paths = Files.list(jobsDirectory)) {
            return paths.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(this::readJob)
                    .sorted(Comparator.comparing(Job::getId))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not list jobs.", e);
        }
    }

    public Optional<Job> findJobByTitleAndCompany(String title, String company) {
        return listJobs().stream()
                .filter(j -> j.getTitle().equalsIgnoreCase(title.trim())
                        && j.getCompany().equalsIgnoreCase(company.trim()))
                .findFirst();
    }

    public String nextJobId() {
        int max = listJobs().stream()
                .map(Job::getId)
                .filter(id -> id.matches("J\\d+"))
                .mapToInt(id -> Integer.parseInt(id.substring(1)))
                .max().orElse(0);
        return String.format("J%02d", max + 1);
    }

    private Job readJob(Path file) {
        String json = read(file);
        String deadlineText = JsonSupport.stringField(json, "deadline");
        LocalDate deadline = deadlineText == null || deadlineText.isBlank()
                ? null : LocalDate.parse(deadlineText);
        Job job = new Job(
                JsonSupport.stringField(json, "id"),
                JsonSupport.stringField(json, "title"),
                JsonSupport.stringField(json, "company"),
                JsonSupport.stringField(json, "description"),
                JsonSupport.stringField(json, "salary"),
                JsonSupport.stringField(json, "location"),
                deadline);
        Set<String> required = new LinkedHashSet<>(JsonSupport.stringArrayField(json, "requiredSkills"));
        Set<String> preferred = new LinkedHashSet<>(JsonSupport.stringArrayField(json, "preferredSkills"));
        job.updateAnalysis(required, preferred);
        return job;
    }

    private void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save " + file, e);
        }
    }

    private String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
    }
}