package de.allgemein.testsweet;

import de.allgemein.lmg.calc.Leistungsberechnung.Berechnungsergebnis;
import de.allgemein.lmg.calc.Leistungsberechnung.FallBerechnung;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Prüft den gesamten Verifikationspfad gegen einen gemeinsamen Fehler in Soll und SQL-Ist. */
class CalculationMutationRegressionTest {
    @TempDir Path temp;

    @Test void konsistentFalscherErwerbsfreibetragBleibtTrotzPassendemSqlRot() throws Exception {
        Auftrag generated = new SzenarioBibliothek(42).erzeuge(500).stream()
                .filter(order -> order.szenarioTyp.equals("KOMBINATION") && order.rechenbloecke.size() == 1)
                .filter(order -> order.rechenbloecke.getFirst().ergebnis().faelle().stream()
                        .anyMatch(person -> person.freibetragErwerb().signum() > 0
                                && person.anrechenbaresEinkommen().compareTo(BigDecimal.ONE) > 0))
                .findFirst().orElseThrow();
        Rechenblock original = generated.rechenbloecke.getFirst();
        Auftrag baseline = verifiedOrder(generated, original);
        assertEquals(1, verify(baseline), "Die unveränderte Kontrolle muss tatsächlich grün sein");
        assertEquals(Auftrag.Status.VERIFIZIERT, baseline.status);

        // Simuliert einen Produktdefekt und denselben Defekt im daraus abgeleiteten Soll:
        // 1 EUR zusätzlicher Freibetrag, entsprechend weniger Einkommen und mehr Auszahlung.
        // Alle abhängigen Summen ändern sich mit; bloße Summen-/SQL-Gleichheit reicht daher nicht.
        Rechenblock mutated = wrongEmploymentAllowance(original);
        Auftrag wrong = verifiedOrder(generated, mutated);
        assertEquals(0, verify(wrong));
        assertEquals(Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN, wrong.status);
        assertTrue(wrong.pruefungen().getFirst().ok(), "SQL-Ist und das absichtlich falsche Soll stimmen überein");
        Auftrag.Pruefung independent = wrong.pruefungen().getLast();
        assertFalse(independent.ok());
        assertTrue(independent.hinweis().contains("freibetrag_erwerb"), independent::hinweis);
        assertTrue(independent.hinweis().contains("bg_zahlbetrag"), independent::hinweis);
    }

    @Test void generierteZahlbetragspruefungenHabenImmerUnabhaengigPruefbareRechenbloecke() {
        List<Auftrag> orders = new ArrayList<>(new SzenarioBibliothek(42).erzeuge(500));
        orders.addAll(new SzenarioBibliothek(42).erzeugeNegativ(500, "SWEET-N-"));
        orders.addAll(new SzenarioBibliothek(42).erzeugeLast(3, "SWEET-L-"));
        orders.addAll(new SzenarioBibliothek(42).erzeugeStress(3, "SWEET-S-"));
        orders.addAll(new WebSzenarioBibliothek(42).erzeuge(100, "SWEET-W-"));
        orders.addAll(new WebSzenarioBibliothek(42).erzeugeNegativ(100, "SWEET-WN-"));
        orders.addAll(new WebSzenarioBibliothek(42).erzeugeLast(3, "SWEET-WL-"));
        long paymentChecks = 0;
        for (Auftrag order : orders) {
            if (order.erwartungen.stream().anyMatch(expected -> expected.art() == Erwartung.Art.BETRAG
                    && expected.sql().contains("AUSZAHLUNGSBETRAG"))) {
                paymentChecks++;
                assertFalse(order.rechenbloecke.isEmpty(), order.aktenzeichen + " " + order.szenarioTyp);
                assertTrue(Verifikator.pruefeRechnung(order).ok(), order.aktenzeichen + " " + order.szenarioTyp);
            }
        }
        assertTrue(paymentChecks > 500, "Die Gegenprobe muss die erzeugten Fach- und Web-Zahlfälle erreichen");
    }

    private static Rechenblock wrongEmploymentAllowance(Rechenblock original) {
        var before = original.ergebnis();
        List<FallBerechnung> persons = new ArrayList<>(before.faelle());
        int selected = 0;
        while (persons.get(selected).freibetragErwerb().signum() <= 0
                || persons.get(selected).anrechenbaresEinkommen().compareTo(BigDecimal.ONE) <= 0) selected++;
        var person = persons.get(selected);
        persons.set(selected, new FallBerechnung(person.fallId(), person.regelbedarf(), person.mehrbedarfG(),
                person.mehrbedarfSchwangere(), person.mehrbedarfAlleinerziehend(), person.mehrbedarfWarmwasser(),
                person.mehrbedarf(), person.kduWarmwasserAbzug(), person.kduMiete(), person.kduBetriebskosten(),
                person.kduHeizung(), person.kdu(), person.kduUnangemessen(), person.gesamtbedarf(),
                person.freibetragErwerb().add(BigDecimal.ONE), person.absetzbetaetrag(),
                person.anrechenbaresEinkommen().subtract(BigDecimal.ONE), person.zahlbetrag().add(BigDecimal.ONE)));
        var result = new Berechnungsergebnis(before.bgId(), List.copyOf(persons), before.gesamtbedarf(),
                before.anrechenbaresEinkommen().subtract(BigDecimal.ONE), before.verwertbaresVermoegen(),
                before.zahlbetrag().add(BigDecimal.ONE));
        return new Rechenblock(original.titel(), original.zahlungsmonat(), original.faelle(), original.vermoegen(),
                original.budget(), result, original.zusatzHinweise(), original.budgetRestExpected() == null ? null
                        : original.budgetRestExpected().subtract(BigDecimal.ONE), original.kduParameter());
    }

    private static Auftrag verifiedOrder(Auftrag generated, Rechenblock block) {
        Auftrag order = new Auftrag(generated.id, generated.szenarioTyp, generated.aktenzeichen,
                generated.hauptperson, generated.beschreibung, List.of(), List.of(Erwartung.betrag(
                "SQL stimmt mit dem Produkt-Rechenvorschlag überein", "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?",
                context -> List.of(42L), block.ergebnis().zahlbetrag().toPlainString())))
                .dokumentiere(generated.beschreibungEinfach, List.of(block));
        order.status = Auftrag.Status.FERTIG_GESPIELT;
        return order;
    }

    private int verify(Auftrag order) throws Exception {
        try (var log = new SweetLog(temp)) {
            return new Verifikator("", "", "").verifiziereAlle(
                    databaseWithPayout(order.rechenbloecke.getFirst().ergebnis().zahlbetrag()), List.of(order), log);
        }
    }

    /** Kontrollierte SQL-Gegenstelle: Sie bestätigt gerade den gemeinsam falschen Betrag. */
    private static Connection databaseWithPayout(BigDecimal payout) {
        return proxy(Connection.class, (connection, method, args) -> switch (method.getName()) {
            case "getAutoCommit" -> true;
            case "setAutoCommit" -> null;
            case "prepareStatement" -> proxy(PreparedStatement.class, (statement, call, values) -> switch (call.getName()) {
                case "setObject", "close" -> null;
                case "executeQuery" -> {
                    AtomicBoolean first = new AtomicBoolean(true);
                    yield proxy(ResultSet.class, (result, read, indexes) -> switch (read.getName()) {
                        case "next" -> first.getAndSet(false);
                        case "getObject" -> payout;
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException(read.getName());
                    });
                }
                default -> throw new UnsupportedOperationException(call.getName());
            });
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
