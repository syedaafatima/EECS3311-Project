package ca.yorku.careeragent.controller;

import ca.yorku.careeragent.domain.CandidateProfile;
import ca.yorku.careeragent.service.CareerFacade;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
                case "profile.show" -> facade.getProfile()
                        .map(CandidateProfile::toString)
                        .orElse("No candidate profile has been saved yet.");
                default -> "Unknown request: " + action;
            };
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String updateProfile(Map<String, String> input) {
        CandidateProfile profile = facade.updateProfile(
                input.get("name"),
                input.get("education"),
                input.get("experience"),
                csv(input.get("skills")),
                csv(input.get("interests")),
                input.get("location"));
        return "Profile saved for " + profile.getName() + ".";
    }

    private static Set<String> csv(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null || value.isBlank()) return result;
        for (String item : value.split(",")) {
            if (!item.isBlank()) result.add(item.trim());
        }
        return result;
    }
}
