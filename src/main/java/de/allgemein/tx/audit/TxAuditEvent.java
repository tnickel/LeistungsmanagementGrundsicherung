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
        sb.append(timestamp).append(" [").append(bereinige(threadName)).append("] ")
          .append(bereinige(category)).append(".").append(bereinige(eventType))
          .append(" id=").append(bereinige(correlationId));
        if (attempt > 0) {
            sb.append(" attempt=").append(attempt);
        }
        if (durationMillis >= 0) {
            sb.append(" duration=").append(durationMillis).append("ms");
        }
        if (details != null && !details.isBlank()) {
            sb.append(" details=\"").append(bereinige(details)).append("\"");
        }
        if (error != null && !error.isBlank()) {
            sb.append(" error=\"").append(bereinige(error)).append("\"");
        }
        return sb.toString();
    }

    /** Steuerzeichen und Anführungszeichen maskieren: eine Zeile pro Ereignis (Log-Injection). */
    private static String bereinige(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 0x20 || c == 0x7f) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
