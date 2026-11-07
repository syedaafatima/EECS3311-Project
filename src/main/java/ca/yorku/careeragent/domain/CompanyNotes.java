package ca.yorku.careeragent.domain;

import ca.yorku.careeragent.llm.Json;
import ca.yorku.careeragent.service.ToolManager;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** User-supplied research and source URLs; the application does not claim to browse them. */
public record CompanyNotes(String id, String company, String text, List<String> sources) {
    public CompanyNotes {
        if (company == null || company.isBlank()) throw new IllegalArgumentException("Company name is required.");
        if (text == null || text.isBlank() || text.length() > 40_000) throw new IllegalArgumentException("Research text must contain 1–40000 characters.");
        if (!idFor(company).equals(id)) throw new IllegalArgumentException("Notes ID must identify its company.");
        sources = sources.stream().map(String::strip).distinct().toList();
        if (sources.size() > 20) throw new IllegalArgumentException("At most 20 source URLs are allowed.");
        for (String source : sources) {
            URI uri;
            try { uri = URI.create(source); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("Each research source must be an HTTP(S) URL."); }
            if (source.length() > 2000 || !List.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Each source must be an HTTP(S) URL without credentials.");
            }
        }
    }
    public static String idFor(String company) {
        if (company == null || company.isBlank()) throw new IllegalArgumentException("Company name is required.");
        return "N-" + ToolManager.fingerprint(company.strip().toLowerCase(Locale.ROOT)).substring(0, 32);
    }
    public Map<String, Object> toRecord() { return Map.of("id", id, "company", company, "text", text, "sources", sources); }
    public static CompanyNotes fromRecord(Map<?, ?> row) {
        return new CompanyNotes(Json.text(row.get("id")), Json.text(row.get("company")), Json.text(row.get("text")),
                Json.array(row.get("sources")).stream().map(Json::text).toList());
    }
    public String fingerprint() { return ToolManager.fingerprint(Json.encode(toRecord())); }
    @Override public String toString() { return id + " | " + company + "\n" + text + "\nProvided sources: " + sources; }
}
