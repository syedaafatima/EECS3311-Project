package ca.yorku.careeragent.llm;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Select the explicit local provider for each request; never switch providers after a failure. */
public final class ConfiguredLLMClient implements LLMClient {
    private final Path configFile;
    private final Map<String, String> environment;
    public ConfiguredLLMClient() { this(Path.of(".env"), System.getenv()); }
    public ConfiguredLLMClient(Path configFile, Map<String, String> environment) {
        this.configFile = configFile; this.environment = Map.copyOf(environment);
    }
    private LLMClient selected() {
        String provider = "groq";
        if (Files.exists(configFile)) {
            try {
                for (String line : Files.readAllLines(configFile)) {
                    String value = line.strip();
                    if (value.startsWith("#")) continue;
                    int equals = value.indexOf('=');
                    if (equals > 0 && value.substring(0, equals).strip().equals("LLM_PROVIDER")) {
                        provider = value.substring(equals + 1).strip().replaceAll("^[\"']|[\"']$", "");
                    }
                }
            } catch (IOException e) { throw new IllegalStateException("Could not read local LLM configuration."); }
        }
        provider = environment.getOrDefault("LLM_PROVIDER", provider).strip().toLowerCase(java.util.Locale.ROOT);
        return switch (provider) {
            case "groq" -> new GroqAdapter(configFile, environment, URI.create("https://api.groq.com/openai/v1/chat/completions"));
            case "openai" -> new OpenAIAdapter(configFile, environment, URI.create("https://api.openai.com/v1/chat/completions"));
            default -> throw new IllegalArgumentException("LLM_PROVIDER must be groq or openai. No provider request was sent.");
        };
    }
    @Override public String complete(String instructions, String context) { return selected().complete(instructions, context); }
    @Override public String configurationStatus() { return selected().configurationStatus(); }
}
