package de.allgemein.testsweet;

import de.allgemein.lmg.web.Json;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Zusätzliche Verifikation auf ausdrücklich eigenem temporärem Schema. */
public final class IsolatedSimulatorProbe {
    public static void main(String[] args) throws Exception {
        String user=System.getenv("TX_USER");
        if(user==null||!user.matches("TSFIX_[A-F0-9]{16}"))throw new IllegalStateException("Fremdes Testschema");
        BuildEvidence.initialize();
        var category=Simulator.Kategorie.valueOf(args[0]);
        if(category!=Simulator.Kategorie.ECHT&&category!=Simulator.Kategorie.RECHNER)
            throw new IllegalArgumentException("Nur die 17 neuen Fachfälle");
        int count=category==Simulator.Kategorie.ECHT?5:12;
        var simulator=new Simulator(null,()->{});
        if(!simulator.dbUser.equals(user))throw new IllegalStateException("Falscher DB-Benutzer");
        simulator.laufSynchron(new Simulator.LaufKonfiguration(3,count,42,0,false,category));
        var rows=new ArrayList<Map<String,Object>>();
        try(var connection=DriverManager.getConnection(simulator.dbUrl,user,System.getenv("TX_PASS"))) {
            try(var statement=connection.createStatement();var result=statement.executeQuery("SELECT USER FROM DUAL")) {
                if(!result.next()||!user.equals(result.getString(1)))throw new IllegalStateException("Fremdes Oracle-Schema");
            }
            for(var order:simulator.auftraege()) {
                var row=new LinkedHashMap<String,Object>();
                row.put("scenario",order.szenarioTyp);row.put("bgId",order.bgId);row.put("status",order.status.name());
                try(var statement=connection.prepareStatement("SELECT BUDGET_REST,VERMOEGEN,(SELECT COUNT(*) FROM SOZIAL_FALL F WHERE F.BG_ID=B.ID),(SELECT COUNT(*) FROM JOURNAL J WHERE J.BG_ID=B.ID) FROM BEDARFSGEMEINSCHAFT B WHERE ID=?")) {
                    statement.setLong(1,order.bgId);
                    try(var result=statement.executeQuery()) {
                        if(!result.next())throw new AssertionError("Vorgang fehlt: "+order.szenarioTyp);
                        row.put("budget",result.getBigDecimal(1).toPlainString());
                        row.put("assets",result.getBigDecimal(2).toPlainString());
                        row.put("persons",result.getInt(3));row.put("journalRows",result.getInt(4));
                    }
                }
                var payments=new ArrayList<Map<String,Object>>();
                try(var statement=connection.prepareStatement("SELECT ZAHLUNGSMONAT,AUSZAHLUNGSBETRAG,VERMOEGENSEINSATZ,ZAHLUNGSSTATUS FROM ZAHLUNG WHERE BG_ID=? ORDER BY ID")) {
                    statement.setLong(1,order.bgId);
                    try(var result=statement.executeQuery()) {while(result.next())payments.add(Map.of(
                        "month",result.getString(1),"amount",result.getBigDecimal(2).toPlainString(),
                        "assetUse",result.getBigDecimal(3).toPlainString(),"status",result.getString(4)));}
                }
                row.put("payments",payments);rows.add(row);
                String expected=category==Simulator.Kategorie.ECHT?switch(order.szenarioTyp) {
                    case "ECHT-ANLAGE-KDU"->"1542.00";case "ECHT-SCHONVERMOEGEN-800"->"1023.00";
                    case "ECHT-KDU-DECKEL"->"1153.00";default->null;
                }:List.of("978.00","478.00","583.00","1063.00","1073.71","268.71","558.00","768.00","1427.00","727.00","227.00","1077.00").get(order.id-1);
                String expectedAssets=switch(order.szenarioTyp) {
                    case "ECHT-BSG-VERMOEGEN"->"44500";case "ECHT-LSG-KFZ-PAAR"->"31000";
                    case "ECHT-SCHONVERMOEGEN-800"->"800";default->"0";
                };
                if(new BigDecimal(expectedAssets).compareTo(new BigDecimal((String)row.get("assets")))!=0)
                    throw new AssertionError("Falsches Endvermögen: "+row);
                if(new BigDecimal(expected==null?"1000":"100").compareTo(new BigDecimal((String)row.get("budget")))!=0)
                    throw new AssertionError("Falsches Endbudget: "+row);
                if(payments.size()!=(expected==null?0:1))throw new AssertionError("Falsche Zahlungszahl: "+row);
                if(expected!=null) {
                    var payment=payments.getFirst();
                    if(new BigDecimal(expected).compareTo(new BigDecimal((String)payment.get("amount")))!=0
                        ||new BigDecimal((String)payment.get("assetUse")).signum()!=0
                        ||!payment.get("status").equals("ANGELEGT"))throw new AssertionError("Falsche Zahlung: "+row);
                }
            }
        }
        var output=new LinkedHashMap<String,Object>();output.put("schema",user);output.put("category",category.name());
        output.put("success",simulator.laufErfolgreich());output.put("evidenceComplete",simulator.nachweiseVollstaendig());
        output.put("evidenceErrors",simulator.nachweisFehler());output.put("runDirectory",simulator.letztesLogVerzeichnis());output.put("rows",rows);
        Files.writeString(Path.of(args[1]),Json.write(output));
        System.out.println("PROBE "+category+": "+rows.size()+" Datensätze; erfolgreich="+simulator.laufErfolgreich());
        if(!simulator.laufErfolgreich()||!simulator.nachweiseVollstaendig()
            ||simulator.auftraege().stream().anyMatch(a->a.status!=Auftrag.Status.VERIFIZIERT))
            throw new AssertionError("Simulator-/Buildnachweis unvollständig: "+simulator.nachweisFehler());
    }
}
