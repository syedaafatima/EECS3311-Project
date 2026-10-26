package ca.yorku.careeragent.service;

import ca.yorku.careeragent.domain.Application;
import ca.yorku.careeragent.domain.CandidateProfile;
import ca.yorku.careeragent.domain.Job;
import ca.yorku.careeragent.persistence.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Facade that coordinates application workflows from the Stage 1 design. */
public class CareerFacade {
    private final Repository repository;

    public CareerFacade(Repository repository) {
        this.repository = repository;
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
}
