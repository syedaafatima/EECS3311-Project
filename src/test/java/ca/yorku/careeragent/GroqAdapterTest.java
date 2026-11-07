package ca.yorku.careeragent;

import ca.yorku.careeragent.llm.ConfiguredLLMClient;
import ca.yorku.careeragent.llm.GroqAdapter;
import ca.yorku.careeragent.llm.Json;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class GroqAdapterTest {
    @TempDir Path data;

    @Test void groqHttpContractUsesItsOwnKeyModelJsonModeAndSupportedReasoningOptions() throws Exception {
        AtomicReference<Map<?, ?>> request = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            request.set(Json.object(Json.parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))));
            byte[] body = Json.encode(Map.of("choices", List.of(Map.of("finish_reason", "stop", "message",
                    Map.of("content", "{\"status\":\"ok\"}", "reasoning", "Internal field must not be shown"))))).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            Path env = Files.writeString(data.resolve(".env"), "GROQ_API_KEY=groq-test-key\nOPENAI_API_KEY=openai-test-key\n");
            var adapter = new GroqAdapter(env, Map.of(), URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat"));
            assertEquals("{\"status\":\"ok\"}", adapter.complete("Return JSON", "No personal data"));
            assertEquals("Bearer groq-test-key", authorization.get());
            assertEquals("openai/gpt-oss-20b", request.get().get("model"));
            assertEquals("json_object", Json.object(request.get().get("response_format")).get("type"));
            assertEquals("low", request.get().get("reasoning_effort"));
            assertEquals(false, request.get().get("include_reasoning"));
            assertFalse(request.get().containsKey("reasoning_format"));
        } finally { server.stop(0); }
    }

    @Test void providerSelectionUsesEnvironmentPrecedenceAndReloadsLocalConfiguration() throws Exception {
        Path file = Files.writeString(data.resolve(".env"), "LLM_PROVIDER='openai'\nOPENAI_API_KEY=openai-test-key\n");
        var selected = new ConfiguredLLMClient(file, Map.of());
        assertTrue(selected.configurationStatus().startsWith("OpenAI"));
        assertTrue(new ConfiguredLLMClient(file, Map.of("LLM_PROVIDER", "groq")).configurationStatus().startsWith("Groq"));
        Files.writeString(file, "LLM_PROVIDER=groq\nGROQ_API_KEY=private-groq-test-value\n");
        assertTrue(selected.configurationStatus().startsWith("Groq"));
        assertFalse(selected.configurationStatus().contains("private-groq-test-value"));
        assertTrue(new ConfiguredLLMClient(data.resolve("missing"), Map.of()).configurationStatus().startsWith("Groq"));
    }

    @Test void missingGroqKeyNeverUsesAnOpenAIKeyOrFallsBackToAnotherProvider() {
        var adapter = new GroqAdapter(data.resolve("missing"), Map.of("OPENAI_API_KEY", "private-openai-test-value"),
                URI.create("http://127.0.0.1:1/chat"));
        var error = assertThrows(IllegalStateException.class, () -> adapter.complete("Return JSON", "Source"));
        assertTrue(error.getMessage().contains("GROQ_API_KEY"));
        assertFalse(error.getMessage().contains("private-openai-test-value"));
        assertThrows(IllegalArgumentException.class, () -> new ConfiguredLLMClient(data.resolve("missing"),
                Map.of("LLM_PROVIDER", "unknown")).complete("Return JSON", "Source"));
    }

    @Test void quotaFailuresAreRedactedAndGiveFreeTierRecoveryAdvice() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            byte[] body = "private-groq-test-value".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var adapter = new GroqAdapter(data.resolve("missing"), Map.of("GROQ_API_KEY", "private-groq-test-value"),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat"));
            var error = assertThrows(IllegalStateException.class, () -> adapter.complete("Return JSON", "Source"));
            assertTrue(error.getMessage().contains("free-plan limits"));
            assertFalse(error.getMessage().contains("private-groq-test-value"));
        } finally { server.stop(0); }
    }
}
