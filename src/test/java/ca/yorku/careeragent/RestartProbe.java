package ca.yorku.careeragent;

import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;
import java.nio.file.Path;

/** Invoked in a separate JVM to expose randomized Map/Set iteration across restarts. */
public final class RestartProbe {
    public static void main(String[] args) {
        CareerFacade facade = new CareerFacade(new Repository(Path.of(args[0])));
        for (int i = 1; i < args.length; i++) {
            var document = facade.getDocument(args[i]);
            facade.saveDocument(document, document.content());
        }
        System.out.println("Separate JVM source verification passed.");
    }
}
