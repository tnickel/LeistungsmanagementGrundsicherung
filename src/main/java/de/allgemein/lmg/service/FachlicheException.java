package de.allgemein.lmg.service;

import de.allgemein.tx.TxException;

/**
 * Kurz gesagt: Das ist der „klar benannte Fehler" der Fachlichkeit.
 * Beispiel: kein Anspruch, Budget erschöpft, zwei Bearbeiter im Konflikt.
 * Diese Fehler sind endgültig — erneutes Versuchen hilft nicht. Er trägt einen
 * Code, mit dem die Oberfläche eine verständliche Meldung zeigen kann.
 *
 * <p>Erbt von {@link TxException}, damit der TxManager die Ausnahme uningeschraubt
 * durchreicht (rollback, kein Retry) und Webschicht/Tests den Typ und {@code code}
 * weiterhin erkennen — sonst würde der Fachfehler als technischer Fehler (HTTP 500)
 * ankommen. {@code code} steuert die Anzeige in der Oberfläche.</p>
 */
public class FachlicheException extends TxException {

    public static final String KONKURRENZ = "KONKURRENZ";
    public static final String KEIN_ANSPRUCH = "KEIN_ANSPRUCH";
    public static final String BUDGET_ERSCHOEPFT = "BUDGET_ERSCHOEPFT";
    public static final String VALIDATION = "VALIDATION";
    public static final String NICHT_GEFUNDEN = "NICHT_GEFUNDEN";
    public static final String RBS_NICHT_KONFIGURIERT = "RBS_NICHT_KONFIGURIERT";
    public static final String BERECHTIGUNG = "BERECHTIGUNG";
    /** HTTP 405: Pfad bekannt, Methode nicht unterstützt (Unterscheidung von 404). */
    public static final String METHODE_NICHT_UNTERSTUETZT = "METHODE_NICHT_UNTERSTUETZT";

    private final String code;

    public FachlicheException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}