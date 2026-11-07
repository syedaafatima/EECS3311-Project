package ca.yorku.careeragent.persistence;

import ca.yorku.careeragent.llm.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Atomic JSON records for reviewed documents, interviews and research notes. */
public final class LocalRecords {
    private static final Set<String> COLLECTIONS = Set.of("documents", "question-sets", "interviews", "company-notes");
    private final Path root;
    public LocalRecords(Path root) { this.root = root; }
    private Path file(String collection, String id) {
        if (!COLLECTIONS.contains(collection) || id == null || !id.matches("[A-Za-z0-9-]{1,80}")) {
            throw new IllegalArgumentException("Invalid record collection or ID.");
        }
        return root.resolve(collection).resolve(id + ".json");
    }
    public void put(String collection, String id, Map<String, ?> record) {
        Path destination = file(collection, id);
        if (!id.equals(record.get("id"))) throw new IllegalArgumentException("Record ID does not match.");
        Path temporary = null;
        try {
            String json = Json.encode(record);
            if (json.length() > 1_048_576) throw new IllegalArgumentException("Record is too large.");
            Files.createDirectories(destination.getParent());
            temporary = Files.createTempFile(destination.getParent(), ".save-", ".tmp");
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) {
            throw new IllegalStateException("Could not save " + collection + " record.", e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException e) { temporary.toFile().deleteOnExit(); }
            }
        }
    }
    public Optional<Map<?, ?>> get(String collection, String id) {
        Path path = file(collection, id);
        if (!Files.exists(path)) return Optional.empty();
        try { return Optional.of(Json.object(Json.parse(Files.readString(path, StandardCharsets.UTF_8)))); }
        catch (IOException e) { throw new IllegalStateException("Could not read " + collection + " record.", e); }
    }
    public List<Map<?, ?>> list(String collection) {
        Path directory = file(collection, "probe").getParent();
        if (!Files.exists(directory)) return List.of();
        try (var paths = Files.list(directory)) {
            return paths.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .<Map<?, ?>>map(p -> get(collection, p.getFileName().toString().replaceFirst("\\.json$", "")).orElseThrow()).toList();
        } catch (IOException e) { throw new IllegalStateException("Could not list " + collection + " records.", e); }
    }
}
