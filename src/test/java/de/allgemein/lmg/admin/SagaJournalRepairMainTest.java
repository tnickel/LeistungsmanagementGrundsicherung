package de.allgemein.lmg.admin;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SagaJournalRepairMainTest {
    @Test
    void leereOderSteuerzeichenhaltigeOperatorenUndGruendeWerdenVerworfen() {
        for(String operator:new String[]{""," ","admin\nanderer","admin\u2028anderer","a".repeat(101)})
            assertThrows(IllegalArgumentException.class,()->proof(operator,"Geprüfter Nullwirkungsnachweis",true,true));
        for(String reason:new String[]{""," ","geprüft\rüberschrieben","geprüft\u2029überschrieben","\u0000","a".repeat(1001)})
            assertThrows(IllegalArgumentException.class,()->proof("admin",reason,true,true));
    }
    @Test
    void fehlendeWartungsOderWirkungsbestaetigungWirdNichtDurchPendingErsetzt() {
        assertThrows(IllegalArgumentException.class,()->proof("admin","Geprüft",false,true));
        assertThrows(IllegalArgumentException.class,()->proof("admin","Geprüft",true,false));
    }
    @Test
    void unbekannteOderDoppelteParameterStartenKeineDatenbankaktion() {
        assertThrows(IllegalArgumentException.class,()->SagaJournalRepairMain.main(new String[]{"delete-all"}));
        assertThrows(IllegalArgumentException.class,()->SagaJournalRepairMain.main(new String[]{"inspect","--unknown","value"}));
        assertThrows(IllegalArgumentException.class,()->SagaJournalRepairMain.main(new String[]{"inspect","--type","a","--type","b"}));
    }
    private static SagaJournalRepairMain.Resolution proof(String operator,String reason,boolean maintenance,boolean noEffects) {
        return new SagaJournalRepairMain.Resolution(Path.of("snapshot.json"),"a".repeat(64),Path.of("proof.txt"),"b".repeat(64),
                operator,reason,maintenance,noEffects,Path.of("resolution"));
    }
}
