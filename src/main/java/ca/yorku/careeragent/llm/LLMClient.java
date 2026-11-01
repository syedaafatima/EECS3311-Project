package ca.yorku.careeragent.llm;

/** Provider-independent boundary; callers supply a bounded task and retrieved context. */
@FunctionalInterface
public interface LLMClient {
    String complete(String instructions, String context);
}
