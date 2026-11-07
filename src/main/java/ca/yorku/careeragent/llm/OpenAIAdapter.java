package ca.yorku.careeragent.llm;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Adapter from LLMClient to OpenAI chat completions. No credentials enter the repository. */
public final class OpenAIAdapter implements LLMClient {
    private final Path configFile;
    private final Map<String, String> environment;
    private final URI endpoint;
    private final HttpClient http;

    public OpenAIAdapter() {
        this(Path.of(".env"), System.getenv(), URI.create("https://api.openai.com/v1/chat/completions"));
    }

    /** Injectable local config and endpoint permit HTTP contract tests without paid requests. */
    public OpenAIAdapter(Path configFile, Map<String, String> environment, URI endpoint) {
        this.configFile = configFile;
        this.environment = Map.copyOf(environment);
        this.endpoint = endpoint;
        if (!"https".equals(endpoint.getScheme()) && !("http".equals(endpoint.getScheme())
                && List.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost()))) {
            throw new IllegalArgumentException("LLM endpoint must use HTTPS.");
        }
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20));
        String proxy = environment.get("HTTPS_PROXY");
        if (proxy != null && !List.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost())) {
            URI uri = URI.create(proxy);
            builder.proxy(ProxySelector.of(new InetSocketAddress(uri.getHost(), uri.getPort() < 0 ? 80 : uri.getPort())));
        }
        http = builder.build();
    }

    @Override public String complete(String instructions, String context) {
        if (context == null || context.isBlank() || context.length() > 60_000) {
            throw new IllegalArgumentException("AI source text must contain 1–60000 characters.");
        }
        Map<String, String> config = readConfig();
        String key = config.getOrDefault("OPENAI_API_KEY", "").strip();
        if (key.isEmpty() || key.equals("YOUR_LOCAL_API_KEY_HERE")) {
            throw new IllegalStateException("Set OPENAI_API_KEY in local .env or your environment. Manual entry remains available.");
        }
        if (!key.matches("[\\x21-\\x7e]+")) {
            throw new IllegalStateException("OPENAI_API_KEY must contain only printable non-space ASCII characters.");
        }
        String body = Json.encode(Map.of("model", config.getOrDefault("OPENAI_MODEL", "gpt-4.1-mini"),
                "messages", List.of(Map.of("role", "system", "content", instructions),
                        Map.of("role", "user", "content", context)),
                "response_format", Map.of("type", "json_object"), "max_completion_tokens", 3000));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        try {
            HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body()) {
                if (response.statusCode() != 200) {
                    // Provider bodies can echo personal data or credentials: never surface them.
                    String help = switch (response.statusCode()) {
                        case 401 -> "API key rejected. Check the local key and project.";
                        case 403 -> "Access denied. Check project/model permissions.";
                        case 429 -> "Check API credits/billing or request rate limits; ChatGPT subscription billing is separate.";
                        default -> "Retry later or check provider availability.";
                    };
                    throw new IllegalStateException("LLM request failed (HTTP " + response.statusCode() + "). " + help + " Saved data is unchanged.");
                }
                byte[] bytes = input.readNBytes(1_048_577);
                if (bytes.length > 1_048_576) throw new IllegalStateException("LLM response is too large.");
                Map<?, ?> result = Json.object(Json.parse(new String(bytes, StandardCharsets.UTF_8)));
                List<?> choices = Json.array(result.get("choices"));
                if (choices.isEmpty()) throw new IllegalStateException("LLM returned no result.");
                Map<?, ?> choice = Json.object(choices.get(0));
                if (!"stop".equals(choice.get("finish_reason"))) {
                    throw new IllegalStateException("LLM response was incomplete. Retry with a shorter source.");
                }
                return Json.text(Json.object(choice.get("message")).get("content"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM request interrupted; saved data is unchanged.");
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach the LLM provider; retry or enter information manually.");
        }
    }

    private Map<String, String> readConfig() {
        Map<String, String> result = new HashMap<>();
        if (Files.exists(configFile)) {
            try {
                for (String line : Files.readAllLines(configFile)) {
                    String value = line.strip();
                    if (value.isEmpty() || value.startsWith("#")) continue;
                    int equals = value.indexOf('=');
                    if (equals < 1) continue;
                    String setting = value.substring(0, equals).strip();
                    String content = value.substring(equals + 1).strip();
                    if (content.length() >= 2 && ((content.startsWith("\"") && content.endsWith("\""))
                            || (content.startsWith("'") && content.endsWith("'")))) {
                        content = content.substring(1, content.length() - 1);
                    }
                    if (setting.equals("OPENAI_API_KEY") || setting.equals("OPENAI_MODEL")) result.put(setting, content);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Could not read local LLM configuration.");
            }
        }
        for (String setting : List.of("OPENAI_API_KEY", "OPENAI_MODEL")) {
            if (environment.containsKey(setting)) result.put(setting, environment.get(setting));
        }
        return result;
    }

    public String configurationStatus() {
        String key = readConfig().getOrDefault("OPENAI_API_KEY", "").strip();
        if (key.isEmpty() || key.equals("YOUR_LOCAL_API_KEY_HERE")) {
            return "OpenAI API key is not configured. Copy .env.example to .env, enter your API key locally, and use ai verify. "
                    + "OpenAI API billing is separate from a ChatGPT subscription. Never paste a key into chat or commit .env.";
        }
        return "An API key is configured locally (value hidden). A live verification request is still needed to check access and billing.";
    }

}
