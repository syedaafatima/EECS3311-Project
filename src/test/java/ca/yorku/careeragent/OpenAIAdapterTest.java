package ca.yorku.careeragent;

import ca.yorku.careeragent.llm.Json;
import ca.yorku.careeragent.llm.OpenAIAdapter;
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

class OpenAIAdapterTest {
    @TempDir Path temp;

    @Test void sendsConfiguredModelAndParsesEscapedContentThroughRealHttpBoundary() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<Map<?, ?>> request = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            request.set(Json.object(Json.parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))));
            byte[] body = Json.encode(Map.of("choices", List.of(Map.of("finish_reason", "stop",
                    "message", Map.of("content", "{\"skills\":[]}"))))).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            Path env = Files.writeString(temp.resolve(".env"), "# local config\nOPENAI_API_KEY='file-test-key'\nOPENAI_MODEL=example-model\n");
            OpenAIAdapter adapter = new OpenAIAdapter(env, Map.of("OPENAI_API_KEY", "environment-test-key"),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat"));
            assertEquals("{\"skills\":[]}", adapter.complete("Return JSON", "Zoë \"Java\"\nSQL"));
            assertEquals("Bearer environment-test-key", authorization.get());
            assertEquals("example-model", request.get().get("model"));
            var messages = Json.array(request.get().get("messages"));
            assertEquals("Zoë \"Java\"\nSQL", Json.object(messages.get(1)).get("content"));
        } finally { server.stop(0); }
    }

    @Test void providerFailuresNeverEchoCredentialOrProviderBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            byte[] body = "secret-test-value".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            OpenAIAdapter adapter = new OpenAIAdapter(temp.resolve("missing"), Map.of("OPENAI_API_KEY", "secret-test-value"),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat"));
            var error = assertThrows(IllegalStateException.class, () -> adapter.complete("Extract", "Java"));
            assertTrue(error.getMessage().contains("429"));
            assertFalse(error.getMessage().contains("secret-test-value"));
        } finally { server.stop(0); }
    }

    @Test void missingKeyAndOversizedInputFailBeforeAnyProviderCall() {
        OpenAIAdapter adapter = new OpenAIAdapter(temp.resolve("missing"), Map.of(), URI.create("http://127.0.0.1:1/chat"));
        assertTrue(assertThrows(IllegalStateException.class, () -> adapter.complete("Extract", "Java"))
                .getMessage().contains("OPENAI_API_KEY"));
        assertThrows(IllegalArgumentException.class, () -> adapter.complete("Extract", "x".repeat(60_001)));
    }

    @Test void malformedKeyFailsWithoutEchoingItsValue() {
        OpenAIAdapter adapter = new OpenAIAdapter(temp.resolve("missing"), Map.of("OPENAI_API_KEY", "sensitive-value\ninvalid"),
                URI.create("http://127.0.0.1:1/chat"));
        var error = assertThrows(IllegalStateException.class, () -> adapter.complete("Extract", "Java"));
        assertFalse(error.getMessage().contains("sensitive-value"));
    }

    @Test void strictJsonRejectsAmbiguousMalformedAndDeeplyNestedData() {
        for (String invalid : new String[]{"{\"a\":1,\"a\":2}", "[1,]", "{} trailing", "\"bad\\z\"",
                "01", "[".repeat(40) + "0" + "]".repeat(40)}) {
            assertThrows(IllegalArgumentException.class, () -> Json.parse(invalid), invalid);
        }
        String text = "Zoë \"Java\"\nSQL\\notes\u0001";
        assertEquals(text, Json.parse(Json.encode(text)));
    }
}
