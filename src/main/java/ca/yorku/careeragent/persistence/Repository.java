package ca.yorku.careeragent.persistence;

import ca.yorku.careeragent.domain.Application;
import ca.yorku.careeragent.domain.CandidateProfile;
import ca.yorku.careeragent.domain.Job;
import ca.yorku.careeragent.domain.Resume;

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
    private final Path applicationsDirectory;

    public Repository(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.jobsDirectory = dataDirectory.resolve("jobs");
        this.applicationsDirectory = dataDirectory.resolve("applications");
        try {
            Files.createDirectories(jobsDirectory);
            Files.createDirectories(applicationsDirectory);
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


    public void save(Application application) {
        String json = "{\n" +
                "  \"id\": " + JsonSupport.quote(application.getId()) + ",\n" +
                "  \"jobId\": " + JsonSupport.quote(application.getJobId()) + ",\n" +
                "  \"submissionDate\": " + JsonSupport.quote(application.getSubmissionDate().toString()) + ",\n" +
                "  \"status\": " + JsonSupport.quote(application.getStatus().name()) + ",\n" +
                "  \"statusHistory\": " + JsonSupport.arrayOfStrings(application.getStatusHistory()) + "\n" +
                "}\n";
        write(applicationsDirectory.resolve(application.getId() + ".json"), json);
    }

    public Optional<Application> findApplication(String id) {
        Path file = applicationsDirectory.resolve(id + ".json");
        if (!Files.exists(file)) return Optional.empty();
        return Optional.of(readApplication(file));
    }

    public Optional<Application> findApplicationByJobId(String jobId) {
        return listApplications().stream()
                .filter(a -> a.getJobId().equalsIgnoreCase(jobId))
                .findFirst();
    }

    public List<Application> listApplications() {
        try (var paths = Files.list(applicationsDirectory)) {
            return paths.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(this::readApplication)
                    .sorted(Comparator.comparing(Application::getId))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not list applications.", e);
        }
    }

    public String nextApplicationId() {
        int max = listApplications().stream()
                .map(Application::getId)
                .filter(id -> id.matches("A\\d+"))
                .mapToInt(id -> Integer.parseInt(id.substring(1)))
                .max().orElse(0);
        return String.format("A%02d", max + 1);
    }

    /** Each confirmed import is a separate version; failed writes cannot replace older resumes. */
    public void save(Resume resume) {
        if (resume == null || !resume.hasText()) {
            throw new IllegalArgumentException("Cannot save a resume without usable text.");
        }
        String json = "{\n"
                + "  \"id\": " + JsonSupport.quote(resume.getId()) + ",\n"
                + "  \"originalPath\": " + JsonSupport.quote(resume.getOriginalPath()) + ",\n"
                + "  \"text\": " + JsonSupport.quote(resume.getText()) + ",\n"
                + "  \"checksum\": " + JsonSupport.quote(resume.getChecksum()) + "\n}\n";
        Path directory = dataDirectory.resolve("resumes");
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            Path destination = directory.resolve(resume.getId() + ".json");
            if (Files.exists(destination)) {
                if (findResume(resume.getId()).orElseThrow().getChecksum().equals(resume.getChecksum())
                        && read(destination).equals(json)) return;
                throw new IllegalArgumentException("A different resume already uses this ID.");
            }
            temporary = Files.createTempFile(directory, ".import-", ".tmp");
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, destination);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not save imported resume.", e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException e) { temporary.toFile().deleteOnExit(); }
            }
        }
    }

    public Optional<Resume> findResume(String id) {
        if (id == null || !id.matches("R-[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("Invalid resume ID.");
        }
        Path file = dataDirectory.resolve("resumes").resolve(id + ".json");
        return Files.exists(file) ? Optional.of(readResume(file)) : Optional.empty();
    }

    public List<Resume> listResumes() {
        Path directory = dataDirectory.resolve("resumes");
        if (!Files.exists(directory)) return List.of();
        try (var paths = Files.list(directory)) {
            return paths.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted().map(this::readResume).toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not list resumes.", e);
        }
    }

    private Resume readResume(Path file) {
        String json = read(file);
        Resume resume = new Resume(JsonSupport.stringField(json, "id"),
                JsonSupport.stringField(json, "originalPath"), JsonSupport.stringField(json, "text"),
                JsonSupport.stringField(json, "checksum"));
        if (!resume.hasText()) throw new IllegalStateException("Stored resume has no usable text.");
        return resume;
    }

    private Application readApplication(Path file) {
        String json = read(file);
        Application application = new Application(
                JsonSupport.stringField(json, "id"),
                JsonSupport.stringField(json, "jobId"),
                LocalDate.parse(JsonSupport.stringField(json, "submissionDate")),
                Application.Status.valueOf(JsonSupport.stringField(json, "status")));
        application.restoreHistory(JsonSupport.stringArrayField(json, "statusHistory"));
        return application;
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