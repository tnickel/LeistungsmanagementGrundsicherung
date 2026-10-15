package de.allgemein.testsweet;

import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.LeistungsService;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.tx.ConnectionPool;
import de.allgemein.tx.TxManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Vergleicht den gerenderten Prüftext mit den tatsächlich aufgerufenen Serviceargumenten. */
class SepaPruefpaketEingabenTest {
    @Test void kuratierteSepaAbwehrDokumentiertBeideTatsaechlichenEingabenJeAufruf() throws Exception {
        var order = new SzenarioBibliothek(42).erzeugeNegativ(9,"SWEET-N-").stream()
                .filter(a -> a.szenarioTyp.equals("NEG-BANKVERBINDUNG")).findFirst().orElseThrow();
        pruefe(order);
    }

    @Test void generierteSepaAbwehrDokumentiertBeideTatsaechlichenEingabenJeAufruf() throws Exception {
        var generated = new SzenarioBibliothek(42).erzeugeNegativ(500,"SWEET-N-").stream()
                .filter(a -> a.beschreibung.startsWith("SEPA-Anlage abgewehrt")).toList();
        assertFalse(generated.isEmpty());
        for (Auftrag order : generated) pruefe(order);
    }

    private static void pruefe(Auftrag order) throws Exception {
        ConnectionPool neverDatabase = (ConnectionPool) Proxy.newProxyInstance(ConnectionPool.class.getClassLoader(),
                new Class<?>[]{ConnectionPool.class}, (o,m,a) -> { throw new AssertionError("SEPA-Abwehr darf keine DB-Verbindung ausleihen"); });
        SozialLeistungsService actualService = new SozialLeistungsService(new TxManager(neverDatabase));
        List<List<String>> actualInputs = new ArrayList<>();
        LeistungsService probe = (LeistungsService) Proxy.newProxyInstance(LeistungsService.class.getClassLoader(),
                new Class<?>[]{LeistungsService.class}, (o,m,args) -> {
                    assertEquals("legeBedarfsgemeinschaftAn",m.getName());
                    assertEquals(8,args.length);
                    actualInputs.add(List.of((String)args[4],(String)args[5]));
                    try { return m.invoke(actualService,args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
        for (SzenarioAktion action : order.aktionen) {
            var attempt = assertInstanceOf(SzenarioAktion.SepaAnlage.class,action.funktion());
            FachlicheException error = assertThrows(FachlicheException.class,
                    () -> attempt.ausfuehrenMit(probe,"sb-synthetisch"));
            assertTrue(action.akzeptiert(error));
            order.protokolliere(new Auftrag.Schritt(LocalDateTime.of(2026,10,15,12,0),
                    "sb-synthetisch",action.name(),"ERWARTETER FEHLER (" + error.code() + ") — " + error.getMessage()));
        }
        assertEquals(2,actualInputs.size());
        StringBuilder rendered = new StringBuilder();
        MdBericht.testfall(rendered,order);
        var reportInputs = Pattern.compile("synthetische Eingaben: IBAN=([^,\\s]+), BIC=([^\\s]+)")
                .matcher(rendered.toString()).results().map(m -> List.of(m.group(1),m.group(2))).toList();
        assertEquals(actualInputs,reportInputs,
                "Die Fallgruppe muss genau die Bankdaten aller tatsächlichen Serviceaufrufe übertragen");
        // Zusätzliche unabhängige Plausibilitätskontrolle der dokumentierten Fehlergründe.
        String shifted = actualInputs.getFirst().getFirst().substring(4)
                + actualInputs.getFirst().getFirst().substring(0,4);
        StringBuilder numeric = new StringBuilder();
        for (char c : shifted.toCharArray()) numeric.append(c >= 'A' && c <= 'Z' ? Integer.toString(c-'A'+10) : Character.toString(c));
        if (actualInputs.getFirst().getFirst().length() == 22)
            assertNotEquals(java.math.BigInteger.ONE,new java.math.BigInteger(numeric.toString()).mod(java.math.BigInteger.valueOf(97)));
        assertFalse(List.of(8,11).contains(actualInputs.get(1).get(1).length()));
    }
}
