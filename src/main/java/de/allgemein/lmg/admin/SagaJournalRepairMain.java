package de.allgemein.lmg.admin;

import de.allgemein.lmg.web.Json;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Kurz gesagt: Das ist das Reparatur-Werkzeug für das Saga-Journal.
 * Es wird von Hand auf dem Server ausgeführt, wenn ein alter Journaleintrag
 * unklar ist. Es schaut nach (inspect) oder löst einen hängenden Eintrag
 * (resolve-pending) — aber nur mit doppelter Bestätigung und vollständiger
 * Dokumentation, damit nichts unkontrolliert geändert wird.
 *
 * Offline-Betriebswerkzeug. PENDING ist bei Altjournalen kein Beweis fehlender Fachwirkung.
 */
public final class SagaJournalRepairMain {
    private SagaJournalRepairMain() { }

    public record Resolution(Path snapshot,String snapshotSha256,Path evidence,String evidenceSha256,
            String operator,String reason,boolean maintenanceConfirmed,boolean noEffectsConfirmed,Path output) {
        public Resolution {
            Objects.requireNonNull(snapshot);Objects.requireNonNull(evidence);Objects.requireNonNull(output);
            snapshotSha256=validHash(snapshotSha256);evidenceSha256=validHash(evidenceSha256);
            operator=singleLine(operator,"Bearbeiter",100);reason=singleLine(reason,"Auflösungsgrund",1000);
            if(!maintenanceConfirmed||!noEffectsConfirmed)throw new IllegalArgumentException(
                    "Wartungszustand und unabhängiger Nachweis fehlender Fachwirkung müssen ausdrücklich bestätigt werden");
        }
    }
    public static final class CommitOutcomeUnknownException extends IOException {
        CommitOutcomeUnknownException(Throwable cause) {
            super("Commit-Ausgang unbekannt. Nicht wiederholen; Journal und gesicherte Auflösungsbelege unabhängig prüfen.",cause);
        }
    }

    public static void main(String[] args) throws Exception {
        if(args.length<1)throw new IllegalArgumentException("inspect oder resolve-pending mit benannten Parametern erforderlich");
        if(!Set.of("inspect","resolve-pending").contains(args[0]))throw new IllegalArgumentException("Unbekannte Werkzeugaktion");
        Map<String,String> options=new HashMap<>();
        for(int i=1;i<args.length;i+=2) {
            if(i+1>=args.length||!args[i].startsWith("--")||options.putIfAbsent(args[i],args[i+1])!=null)
                throw new IllegalArgumentException("Ungültiger oder doppelter Werkzeugparameter");
        }
        Set<String> allowed="inspect".equals(args[0])?Set.of("--saga-id","--type","--output"):
                Set.of("--saga-id","--type","--snapshot","--snapshot-sha256","--evidence","--evidence-sha256",
                        "--operator","--reason","--maintenance","--no-effects","--output");
        if(!allowed.containsAll(options.keySet()))throw new IllegalArgumentException("Unbekannter Werkzeugparameter");
        String id=required(options,"--saga-id"),type=required(options,"--type");
        Class.forName("oracle.jdbc.OracleDriver");
        var properties=new Properties();
        properties.setProperty("user",environment("TX_USER"));properties.setProperty("password",environment("TX_PASS"));
        properties.setProperty("oracle.net.CONNECT_TIMEOUT","5000");properties.setProperty("oracle.jdbc.ReadTimeout","10000");
        try(var c=DriverManager.getConnection(environment("TX_URL"),properties)) {
            if("inspect".equals(args[0])) {
                String hash=inspect(c,id,type,Path.of(required(options,"--output")));
                System.out.println("Saga-Snapshot gesichert. SHA-256: "+hash);
            } else if("resolve-pending".equals(args[0])) {
                var proof=new Resolution(Path.of(required(options,"--snapshot")),required(options,"--snapshot-sha256"),
                        Path.of(required(options,"--evidence")),required(options,"--evidence-sha256"),
                        required(options,"--operator"),required(options,"--reason"),
                        "MAINTENANCE_CONFIRMED".equals(options.get("--maintenance")),
                        "NO_EFFECTS_CONFIRMED".equals(options.get("--no-effects")),Path.of(required(options,"--output")));
                resolvePending(c,id,type,proof);
                System.out.println("Kontrollierter Abschluss bestätigt; Auflösungsbelege im angegebenen Verzeichnis.");
            } else throw new IllegalArgumentException("Unbekannte Werkzeugaktion");
        }
    }

    /** Sichert Kopf und alle Schritte unter NOWAIT-Sperren; verändert keine Datenbankzeile. */
    public static String inspect(Connection c,String id,String type,Path output) throws Exception {
        try {
            byte[] bytes=bytes(readLocked(c,id,type));writeNew(output,bytes);return sha256(bytes);
        } finally {c.rollback();}
    }

    /** Ausschließlich FAILED/all-PENDING und nur nach unabhängiger, betreiberbestätigter Wirkungsprüfung. */
    public static void resolvePending(Connection c,String id,String type,Resolution proof) throws Exception {
        byte[] saved=verifiedRead(proof.snapshot(),proof.snapshotSha256());
        byte[] evidence=verifiedRead(proof.evidence(),proof.evidenceSha256());
        if(new String(evidence,StandardCharsets.UTF_8).isBlank())throw new IllegalArgumentException("Wirkungsnachweis ist leer");
        Path directory=proof.output().toAbsolutePath().normalize();boolean commitStarted=false;
        try {
            Map<String,Object> current=readLocked(c,id,type);
            if(!proof.snapshotSha256().equals(sha256(bytes(current))))throw new IllegalStateException(
                    "Journal weicht vom gesicherten Snapshot ab; erneut untersuchen, keine Auflösung");
            if(!"FAILED".equals(current.get("state")))throw new IllegalStateException("Auflösung erfordert einen FAILED-Kopf");
            var steps=(List<?>)current.get("steps");
            if(steps.isEmpty())throw new IllegalStateException("Leere Schrittliste ist kein auflösbarer PENDING-Nachweis");
            for(int i=0;i<steps.size();i++) {
                var step=(Map<?,?>)steps.get(i);
                if(((Number)step.get("index")).intValue()!=i||!"PENDING".equals(step.get("state")))throw new IllegalStateException(
                        "Nur zusammenhängende PENDING-Zeilen dürfen nach Wirkungsprüfung aufgelöst werden");
            }
            Files.createDirectory(directory);
            writeNew(directory.resolve("snapshot.json"),saved);writeNew(directory.resolve("effect-evidence.txt"),evidence);
            var intent=new LinkedHashMap<String,Object>();
            intent.put("schemaVersion",1);intent.put("kind","SAGA_PENDING_RESOLUTION");intent.put("sagaId",id);intent.put("type",type);
            intent.put("operator",proof.operator());intent.put("reason",proof.reason());intent.put("createdAt",Instant.now().toString());
            intent.put("snapshotSha256",proof.snapshotSha256());intent.put("effectEvidenceSha256",proof.evidenceSha256());
            intent.put("maintenanceConfirmed",true);intent.put("noEffectsConfirmedByOperator",true);intent.put("state","PREPARED");
            writeNew(directory.resolve("resolution-intent.json"),bytes(intent));
            if(Thread.currentThread().isInterrupted())throw new IOException("Auflösung vor Commit unterbrochen");
            try(var st=c.prepareStatement("UPDATE TX_SAGA SET STATE = 'COMPENSATED',PAYLOAD = NULL,UPDATED_AT = SYSTIMESTAMP WHERE SAGA_ID = ? AND SAGA_TYPE = ? AND STATE = 'FAILED'")) {
                st.setString(1,id);st.setString(2,type);
                if(st.executeUpdate()!=1)throw new SQLException("Gesicherter Saga-Kopf konnte nicht gezielt abgeschlossen werden");
            }
            if(Thread.currentThread().isInterrupted())throw new IOException("Auflösung vor Commit unterbrochen");
            commitStarted=true;
            try {c.commit();}
            catch(SQLException|RuntimeException unknown) {
                try {writeNew(directory.resolve("resolution-outcome.json"),bytes(Map.of("state","COMMIT_OUTCOME_UNKNOWN",
                        "sagaId",id,"checkedAt",Instant.now().toString())));}
                catch(Exception auditFailure) {unknown.addSuppressed(auditFailure);}
                throw new CommitOutcomeUnknownException(unknown);
            }
            try {writeNew(directory.resolve("resolution-outcome.json"),bytes(Map.of("state","COMMITTED",
                    "sagaId",id,"committedAt",Instant.now().toString())));}
            catch(Exception auditFailure) {throw new IOException("Commit bestätigt, Abschlussbeleg nicht geschrieben. Journal unabhängig prüfen.",auditFailure);}
        } catch(Exception|Error error) {
            if(!commitStarted)try {c.rollback();}catch(SQLException rollback){error.addSuppressed(rollback);}
            throw error;
        }
    }

    private static Map<String,Object> readLocked(Connection c,String id,String type) throws SQLException {
        id=singleLine(id,"Saga-ID",36);type=singleLine(type,"Saga-Typ",100);c.setAutoCommit(false);
        var snapshot=new LinkedHashMap<String,Object>();snapshot.put("schemaVersion",1);snapshot.put("kind","SAGA_JOURNAL_SNAPSHOT");
        try(var st=c.prepareStatement("SELECT SAGA_ID,SAGA_TYPE,STATE,PAYLOAD,TO_CHAR(CREATED_AT,'YYYY-MM-DD HH24:MI:SS.FF6'),TO_CHAR(UPDATED_AT,'YYYY-MM-DD HH24:MI:SS.FF6') "
                +"FROM TX_SAGA WHERE SAGA_ID = ? AND SAGA_TYPE = ? FOR UPDATE NOWAIT")) {
            st.setString(1,id);st.setString(2,type);
            try(var rows=st.executeQuery()) {
                if(!rows.next())throw new SQLException("Gezielter Saga-Kopf nicht vorhanden");
                snapshot.put("sagaId",rows.getString(1));snapshot.put("type",rows.getString(2));snapshot.put("state",rows.getString(3));
                snapshot.put("payload",rows.getString(4));snapshot.put("createdAt",rows.getString(5));snapshot.put("updatedAt",rows.getString(6));
            }
        }
        var steps=new ArrayList<Map<String,Object>>();
        try(var st=c.prepareStatement("SELECT IDX,NAME,STATE,TO_CHAR(UPDATED_AT,'YYYY-MM-DD HH24:MI:SS.FF6') FROM TX_SAGA_STEP WHERE SAGA_ID = ? ORDER BY IDX FOR UPDATE NOWAIT")) {
            st.setString(1,id);
            try(var rows=st.executeQuery()) {while(rows.next()) {
                var step=new LinkedHashMap<String,Object>();step.put("index",rows.getInt(1));step.put("name",rows.getString(2));
                step.put("state",rows.getString(3));step.put("updatedAt",rows.getString(4));steps.add(step);
            }}
        }
        snapshot.put("steps",steps);return snapshot;
    }
    private static String environment(String name) {
        String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalArgumentException("Datenbankzugang über "+name+" fehlt");return value;
    }
    private static String required(Map<String,String> options,String name) {
        String value=options.get(name);if(value==null||value.isBlank())throw new IllegalArgumentException("Werkzeugparameter "+name+" fehlt");return value;
    }
    private static String singleLine(String value,String name,int max) {
        if(value==null||value.isBlank()||value.length()>max||value.chars().anyMatch(c->Character.isISOControl(c)
                ||Character.getType(c)==Character.LINE_SEPARATOR||Character.getType(c)==Character.PARAGRAPH_SEPARATOR))
            throw new IllegalArgumentException(name+" muss nichtleer, begrenzt und ohne Steuerzeichen sein");return value;
    }
    private static String validHash(String value) {
        if(value==null||!value.matches("[0-9a-fA-F]{64}"))throw new IllegalArgumentException("SHA-256 muss 64 Hexzeichen enthalten");
        return value.toLowerCase(Locale.ROOT);
    }
    private static byte[] verifiedRead(Path file,String expected) throws Exception {
        byte[] bytes=Files.readAllBytes(file);if(!expected.equals(sha256(bytes)))throw new IOException("Gesicherte Belegdatei stimmt nicht mit erwartetem SHA-256 überein");return bytes;
    }
    private static void writeNew(Path file,byte[] bytes) throws Exception {
        try(var channel=FileChannel.open(file,StandardOpenOption.WRITE,StandardOpenOption.CREATE_NEW)) {
            var buffer=ByteBuffer.wrap(bytes);
            while(buffer.hasRemaining())channel.write(buffer);
            channel.force(true);
        }
        if(!sha256(bytes).equals(sha256(Files.readAllBytes(file))))throw new IOException("Geschriebener Auflösungsbeleg ist unvollständig");
    }
    private static byte[] bytes(Object value) {return Json.write(value).getBytes(StandardCharsets.UTF_8);}
    public static String sha256(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}