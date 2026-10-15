import de.allgemein.testsweet.LlmReviewService;
import de.allgemein.lmg.web.Json;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Isolierter Reviewaufruf: synthetische aktuelle Berichte, keine Konfigurationsänderung. */
class ReviewLlmRun {
    public static void main(String[] args) throws Exception {
        Path root=Path.of("").toAbsolutePath().normalize(), out=Path.of(args[0]);
        int cases=Integer.parseInt(args[1]);
        try(LlmReviewService service=new LlmReviewService(root)) {
            Files.writeString(out.resolve("llm-public-configuration.json"),Json.write(service.configuration()));
            Map<String,Object> status=service.start(cases);
            long deadline=System.nanoTime()+Duration.ofMinutes(30).toNanos();
            while(Boolean.TRUE.equals(status.get("running"))) {
                Files.writeString(out.resolve("llm-review-status.json"),Json.write(status));
                if(System.nanoTime()>deadline)throw new IllegalStateException("Review-Zeitlimit");
                Thread.sleep(1000);status=service.status();
            }
            Files.writeString(out.resolve("llm-review-status.json"),Json.write(status));
            System.out.println("state="+status.get("state")+" completedCases="+status.get("completedCases")+" artifactDirectory="+status.get("artifactDirectory"));
        }
    }
}
