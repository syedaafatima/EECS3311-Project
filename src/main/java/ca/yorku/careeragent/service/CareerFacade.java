package ca.yorku.careeragent.service;

import ca.yorku.careeragent.domain.CandidateProfile;
import ca.yorku.careeragent.persistence.Repository;

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
}