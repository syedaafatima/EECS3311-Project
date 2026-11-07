package ca.yorku.careeragent.llm;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

/** Concrete Adapter for Groq, which offers quota-limited free-plan API access. */
public final class GroqAdapter implements LLMClient {
    public static final String DEFAULT_MODEL = "openai/gpt-oss-20b";
    private final ChatCompletionAdapter transport;
    public GroqAdapter() { this(Path.of(".env"), System.getenv(), URI.create("https://api.groq.com/openai/v1/chat/completions")); }
    public GroqAdapter(Path configFile, Map<String, String> environment, URI endpoint) {
        transport = new ChatCompletionAdapter(configFile, environment, endpoint, "Groq", "GROQ_API_KEY", "GROQ_MODEL", DEFAULT_MODEL);
    }
    @Override public String complete(String instructions, String context) { return transport.complete(instructions, context); }
    @Override public String configurationStatus() { return transport.configurationStatus(); }
}
