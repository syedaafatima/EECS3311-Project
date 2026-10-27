package ca.yorku.careeragent.domain;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Stores candidate information and confirmed skills from the Stage 1 design. */
public class CandidateProfile {
    private String name;
    private String educationLevel;
    private String experience;
    private final Set<String> skills = new LinkedHashSet<>();
    private final Set<String> interests = new LinkedHashSet<>();
    private String locationPreference;

    public CandidateProfile() {
        this.name = "";
        this.educationLevel = "";
        this.experience = "";
        this.locationPreference = "";
    }

    public CandidateProfile(String name, String educationLevel, String experience,
            Set<String> skills, Set<String> interests, String locationPreference) {
        updateProfile(name, educationLevel, experience, skills, interests, locationPreference);
    }

    public void updateProfile(String name, String educationLevel, String experience,
            Set<String> skills, Set<String> interests, String locationPreference) {
        this.name = requireText(name, "Name");
        this.educationLevel = requireText(educationLevel, "Education level");
        this.experience = Objects.requireNonNullElse(experience, "").trim();
        this.locationPreference = Objects.requireNonNullElse(locationPreference, "").trim();

        this.skills.clear();
        this.skills.addAll(normalize(skills));
        this.interests.clear();
        this.interests.addAll(normalize(interests));
    }

    public void updateSkills(Set<String> approvedSkills) {
        skills.clear();
        skills.addAll(normalize(approvedSkills));
    }

    private static String requireText(String value, String fieldName) {
        String cleaned = Objects.requireNonNullElse(value, "").trim();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return cleaned;
    }

    private static Set<String> normalize(Set<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return result;
    }

    public String getName() { return name; }
    public String getEducationLevel() { return educationLevel; }
    public String getExperience() { return experience; }
    public Set<String> getSkills() { return Set.copyOf(skills); }
    public Set<String> getInterests() { return Set.copyOf(interests); }
    public String getLocationPreference() { return locationPreference; }

    @Override
    public String toString() {
        return "CandidateProfile{" +
                "name='" + name + '\'' +
                ", educationLevel='" + educationLevel + '\'' +
                ", experience='" + experience + '\'' +
                ", skills=" + skills +
                ", interests=" + interests +
                ", locationPreference='" + locationPreference + '\'' +
                '}';
    }
}