package de.allgemein.lmg.web;

/**
 * Angemeldeter Benutzer mit seiner Rolle ("admin" oder "sachbearbeiter").
 * Die Kennung stammt aus der Anmeldung und ist die einzige Quelle für den
 * Sachbearbeiter-Eintrag im Journal — Client-Angaben werden ignoriert.
 */
public record Benutzer(String kennung, String rolle) {

    public static final String ROLLE_ADMIN = "admin";
    public static final String ROLLE_SACHBEARBEITER = "sachbearbeiter";

    public boolean istAdmin() {
        return ROLLE_ADMIN.equals(rolle);
    }
}
