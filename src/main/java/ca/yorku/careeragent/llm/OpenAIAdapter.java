package ca.yorku.careeragent.llm;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

/** Concrete Adapter for the optional OpenAI API provider. */
public final class OpenAIAdapter implements LLMClient {
    private final ChatCompletionAdapter transport;
    public OpenAIAdapter() { this(Path.of(".env"), System.getenv(), URI.create("https://api.openai.com/v1/chat/completions")); }
    public OpenAIAdapter(Path configFile, Map<String, String> environment, URI endpoint) {
        transport = new ChatCompletionAdapter(configFile, environment, endpoint, "OpenAI", "OPENAI_API_KEY", "OPENAI_MODEL", "gpt-4.1-mini");
    }
    @Override public String complete(String instructions, String context) { return transport.complete(instructions, context); }
    @Override public String configurationStatus() { return transport.configurationStatus(); }
}
