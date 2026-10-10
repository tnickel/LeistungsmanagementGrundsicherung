package de.allgemein.tx.audit;

import java.time.Instant;

/**
 * Revisionssicheres Transaktions- und Saga-Ereignis.
 */
public record TxAuditEvent(
        long id,
        Instant timestamp,
        String threadName,
        String category,    // "TX" oder "SAGA"
        String eventType,   // "COMMIT", "ROLLBACK", "RETRY", "TIMEOUT", "START", "STEP_DONE", "COMPLETED", "COMPENSATING", "FAILED"
        String correlationId, // Tx-ID oder Saga-ID
        int attempt,
        long durationMillis,
        String details,
        String error) {

    public String toLogLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(timestamp).append(" [").append(threadName).append("] ")
          .append(category).append(".").append(eventType)
          .append(" id=").append(correlationId);
        if (attempt > 0) {
            sb.append(" attempt=").append(attempt);
        }
        if (durationMillis >= 0) {
            sb.append(" duration=").append(durationMillis).append("ms");
        }
        if (details != null && !details.isBlank()) {
            sb.append(" details=\"").append(details).append("\"");
        }
        if (error != null && !error.isBlank()) {
            sb.append(" error=\"").append(error).append("\"");
        }
        return sb.toString();
    }
}
