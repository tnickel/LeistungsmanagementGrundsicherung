package de.allgemein.testsweet;

import de.allgemein.lmg.calc.*;
import de.allgemein.lmg.model.*;
import de.allgemein.lmg.service.*;
import de.allgemein.tx.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Echte Persistenzregression: ein Nachdruck bleibt trotz Bank-/Konfigänderung bytegleich. */
class BescheidDokumentDbTest {
    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void erteilterBescheidWirdNachBankUndBehoerdenwechselUnveraendertNachgedruckt(boolean sagaSchritt) throws Exception {
        String url=System.getenv("TX_URL"); assumeTrue(url!=null&&!url.isBlank(),"Oracle-Verifikation benötigt TX_URL");
        String user=System.getenv().getOrDefault("TX_USER","tx_demo"),pass=System.getenv().getOrDefault("TX_PASS","tx_demo");
        Class.forName("oracle.jdbc.OracleDriver"); long id=0;
        try(var pool=new SimpleConnectionPool(url,user,pass,4)) {
            var manager=new TxManager(pool);
            var original=new SozialLeistungsService(manager,Leistungsberechnung.KduParameter.AUS,
                    new BescheidGenerator.Rechtsbehelf("WIDERSPRUCH","Alte Stelle","Altes Gericht","Alter Ort","Altes Amt"));
            var bg=original.legeBedarfsgemeinschaftAn("SWEET-DOK-"+UUID.randomUUID().toString().substring(0,12),
                    "Synthetische Person",new BigDecimal("5000"),null,"DE89370400440532013000","COBADEFFXXX","Originalkonto","sb-dok");
            id=bg.id();var zero=new BigDecimal("0.00");
            original.legeSozialFallAn(id,new SozialFall(0,id,1,false,false,zero,zero,zero,zero,zero,1),"sb-dok");
            if(sagaSchritt) {
                String payload=de.allgemein.lmg.web.Json.write(Map.of("aktenzeichen",bg.aktenzeichen(),
                        "zahlungsmonat","2026-10","bewilligungBis","2026-11","sachbearbeiterId","sb-dok"));
                try(var c=DriverManager.getConnection(url,user,pass)){c.setAutoCommit(false);
                    de.allgemein.lmg.saga.AntragsSagaProzess.bescheidErteilen(c,payload,UUID.randomUUID().toString());c.commit();}
            } else original.erteileBescheid(id,"2026-10","2026-11","sb-dok");
            var decision=original.letzterBescheid(id).orElseThrow();
            String before=original.generiereBescheidHtml(id);
            try(var c=DriverManager.getConnection(url,user,pass);var s=c.prepareStatement("SELECT BERECHNUNG FROM BESCHEID WHERE ID=?")) {
                s.setLong(1,decision.id());try(var r=s.executeQuery()){assertTrue(r.next());assertEquals(before,BerechnungsSnapshot.lese(r.getString(1)).dokumentHtml());}
            }
            var current=original.ladeDetail(id).bedarfsgemeinschaft();
            original.aktualisiereBedarfsgemeinschaft(id,true,null,"DE12500105170648489890","INGDDEFFXXX","Neues Konto",current.version());
            var changed=new SozialLeistungsService(manager,Leistungsberechnung.KduParameter.AUS,
                    new BescheidGenerator.Rechtsbehelf("KLAGE","Neue Stelle","Neues Gericht","Neuer Ort","Neues Amt"));
            assertEquals(before,changed.generiereBescheidHtml(id));
            assertTrue(before.contains("Originalkonto"));
            if(!sagaSchritt)assertTrue(before.contains("Altes Amt"));
            assertTrue(before.contains("§ 36a Abs. 2 SGB I"));
            assertFalse(before.contains("für die elektronische Form § 65a"));
            assertEquals(new BigDecimal("563.00"),BescheidPaymentBinding.approvedAmount(decision,"2026-11"));
        } finally {
            if(id!=0)try(var c=DriverManager.getConnection(url,user,pass)){c.setAutoCommit(false);
                for(String table:List.of("JOURNAL","BESCHEID","ZAHLUNG","SOZIAL_FALL"))try(var s=c.prepareStatement("DELETE FROM "+table+" WHERE BG_ID=?")){s.setLong(1,id);s.executeUpdate();}
                try(var s=c.prepareStatement("DELETE FROM BEDARFSGEMEINSCHAFT WHERE ID=?")){s.setLong(1,id);s.executeUpdate();}c.commit();
            }
        }
    }
}
