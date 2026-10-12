-- Fachschema für das Leistungsmanagement Grundsicherung (SGB XII)
-- Produkt: SDN.Leistung — Leistungsmanagement (LMG)
-- Ausführen als Schema-Owner, z. B. TX_DEMO in einer PDB (gleiche Umgebung wie ddl/oracle_schema.sql).
--
-- Konventionen:
--  * IDs über Oracle-Sequences (SEQ_BG, SEQ_FALL, SEQ_ZAHLUNG, SEQ_JOURNAL), wie im Blueprint vorgesehen.
--  * VERSION-Spalten für optimistisches Locking; BEFORE-UPDATE-Trigger erhöhen die Version,
--    falls der Aufrufer sie nicht selbst hochgesetzt hat.
--  * JOURNAL.BG_ID ist bewusst eine logische Referenz ohne Foreign Key — Einträge bleiben
--    auch nach Historisierung/Löschung der Bedarfsgemeinschaft revisionsicher erhalten.
--  * Statuswerte sind ASCII-stabil (UEBERGEBEN statt ÜBERGEBEN), damit CHECK-Constraints
--    unabhängig vom Datenbank-Zeichensatz passen; die Oberfläche zeigt "Übergeben" an.

-- ------------------------------------------------------------------
-- Bedarfsgemeinschaft: Führung eines Vorgangs (Aktenzeichen), Restbudget
-- ------------------------------------------------------------------
CREATE TABLE BEDARFSGEMEINSCHAFT (
    ID               NUMBER(19)         NOT NULL,
    AKTENZEICHEN     VARCHAR2(50 CHAR)  NOT NULL,   -- fachlicher Identifikator der Behörde, eindeutig
    HAUPTPERSON_NAME VARCHAR2(100 CHAR) NOT NULL,   -- Name des Antragstellers
    STATUS_AKTIV     NUMBER(1)          DEFAULT 1 NOT NULL,  -- 1 = aktiv, 0 = historisiert
    BUDGET_REST      NUMBER(10,2)       DEFAULT 0 NOT NULL,  -- verfügbarer Restanspruch pro Monat
    VERMOEGEN        NUMBER(12,2)       DEFAULT 0 NOT NULL,  -- Gesamtvermögen der BG (§ 90 SGB XII; Schonvermögen in der Berechnung)
    WIEDERVORLAGE_AM DATE,                          -- Frist/Wiedervorlage für die Fallübersicht
    VERSION          NUMBER(10)         DEFAULT 1 NOT NULL,  -- optimistisches Locking
    IBAN             VARCHAR2(34 CHAR),             -- SEPA-IBAN für Auszahlungen
    BIC              VARCHAR2(11 CHAR),             -- SEPA-BIC der Bank
    KONTOINHABER     VARCHAR2(100 CHAR),            -- Name des Kontoinhabers
    CONSTRAINT PK_BEDARFSGEMEINSCHAFT PRIMARY KEY (ID),
    CONSTRAINT UQ_BG_AKTENZEICHEN UNIQUE (AKTENZEICHEN),
    CONSTRAINT CK_BG_STATUS_AKTIV CHECK (STATUS_AKTIV IN (0, 1)),
    CONSTRAINT CK_BG_BUDGET_REST CHECK (BUDGET_REST >= 0),
    CONSTRAINT CK_BG_VERMOEGEN CHECK (VERMOEGEN >= 0)
);

-- ------------------------------------------------------------------
-- SozialFall: ein Leistungsberechtigter innerhalb einer Bedarfsgemeinschaft.
-- Trägt alle Parameter der Bedarfs- und Einkommensermittlung (§ 27a, § 82 SGB XII).
-- ------------------------------------------------------------------
CREATE TABLE SOZIAL_FALL (
    ID                 NUMBER(19)    NOT NULL,
    BG_ID              NUMBER(19)    NOT NULL,
    REGELBEDARFSSTUFE  NUMBER(1)     NOT NULL,           -- 1..6, steuert den Basisbetrag
    FLAG_MERKZEICHEN_G NUMBER(1)     DEFAULT 0 NOT NULL, -- 1 = Merkzeichen G, 17 % Mehrbedarf (§ 30 Abs. 1)
    FLAG_SCHWANGER     NUMBER(1)     DEFAULT 0 NOT NULL, -- 1 = schwanger ab der 13. SSW (nach Vollendung der 12. Woche), 17 % Mehrbedarf (§ 30 Abs. 2)
    ENTBINDUNGSMONAT   VARCHAR2(7 CHAR),                 -- Ende des Schwangeren-Mehrbedarfs (§ 30 Abs. 2): YYYY-MM, optional; danach keine Leistung mehr mit diesem Merkmal
    ANZ_KINDER_U7      NUMBER(2)     DEFAULT 0 NOT NULL, -- Kinder unter 7 im Haushalt (§ 30 Abs. 3)
    ANZ_KINDER_U16     NUMBER(2)     DEFAULT 0 NOT NULL, -- Kinder unter 16 im Haushalt (§ 30 Abs. 3 Nr. 1)
    ANZ_KINDER_MINDERJ NUMBER(2)     DEFAULT 0 NOT NULL, -- minderjährige Kinder im Haushalt insgesamt (§ 30 Abs. 3 Nr. 2)
    FLAG_WARMWASSER    NUMBER(1)     DEFAULT 0 NOT NULL, -- 1 = Warmwasser dezentral (§ 30 Abs. 7), stufenabhängig 2,3/2,3/–/1,4/1,2/0,8 % (Stufe 3 ohne Satz)
    KDU_MIETE          NUMBER(10,2)  DEFAULT 0 NOT NULL, -- anerkannte Kaltmiete
    KDU_BETRIEBSKOSTEN NUMBER(10,2)  DEFAULT 0 NOT NULL, -- anerkannte kalte Betriebskosten
    KDU_HEIZUNG        NUMBER(10,2)  DEFAULT 0 NOT NULL, -- anerkannte Heizkosten (ohne dezentralen Warmwasseranteil)
    EINKOMMEN_RENTE    NUMBER(10,2)  DEFAULT 0 NOT NULL, -- Renteneinkünfte (§ 82 Abs. 1)
    EINKOMMEN_ERWERB   NUMBER(10,2)  DEFAULT 0 NOT NULL, -- Brutto-Erwerbseinkommen (Freibetrag § 82 Abs. 3)
    ABZ_STEUERN        NUMBER(10,2)  DEFAULT 0 NOT NULL, -- Absetzung: auf das Einkommen entrichtete Steuern (§ 82 Abs. 2)
    ABZ_SV             NUMBER(10,2)  DEFAULT 0 NOT NULL, -- Absetzung: Pflichtbeiträge Sozialversicherung (§ 82 Abs. 2)
    ABZ_VERSICHERUNGEN NUMBER(10,2)  DEFAULT 0 NOT NULL, -- Absetzung: gesetzl./angemessene Versicherungsbeiträge (§ 82 Abs. 2)
    ABZ_ERWERBSAUSGABEN NUMBER(10,2) DEFAULT 0 NOT NULL, -- Absetzung: mit der Erzielung verbundene Ausgaben (§ 82 Abs. 2)
    VERSION            NUMBER(10)    DEFAULT 1 NOT NULL, -- optimistisches Locking
    CONSTRAINT PK_SOZIAL_FALL PRIMARY KEY (ID),
    CONSTRAINT FK_SOZIAL_FALL_BG FOREIGN KEY (BG_ID) REFERENCES BEDARFSGEMEINSCHAFT (ID),
    CONSTRAINT CK_FALL_RBS CHECK (REGELBEDARFSSTUFE BETWEEN 1 AND 6),
    CONSTRAINT CK_FALL_MERKZEICHEN_G CHECK (FLAG_MERKZEICHEN_G IN (0, 1)),
    CONSTRAINT CK_FALL_SCHWANGER CHECK (FLAG_SCHWANGER IN (0, 1)),
    CONSTRAINT CK_FALL_ENTBINDUNGSMONAT CHECK (ENTBINDUNGSMONAT IS NULL
        OR REGEXP_LIKE(ENTBINDUNGSMONAT, '^[0-9]{4}-(0[1-9]|1[0-2])$')),
    CONSTRAINT CK_FALL_WARMWASSER CHECK (FLAG_WARMWASSER IN (0, 1)),
    CONSTRAINT CK_FALL_KINDER CHECK (ANZ_KINDER_U7 >= 0 AND ANZ_KINDER_U16 >= 0 AND ANZ_KINDER_MINDERJ >= 0),
    CONSTRAINT CK_FALL_KDU_MIETE CHECK (KDU_MIETE >= 0),
    CONSTRAINT CK_FALL_KDU_BETRIEB CHECK (KDU_BETRIEBSKOSTEN >= 0),
    CONSTRAINT CK_FALL_KDU_HEIZUNG CHECK (KDU_HEIZUNG >= 0),
    CONSTRAINT CK_FALL_EINK_RENTE CHECK (EINKOMMEN_RENTE >= 0),
    CONSTRAINT CK_FALL_EINK_ERWERB CHECK (EINKOMMEN_ERWERB >= 0),
    CONSTRAINT CK_FALL_ABZUEGE CHECK (ABZ_STEUERN >= 0 AND ABZ_SV >= 0
        AND ABZ_VERSICHERUNGEN >= 0 AND ABZ_ERWERBSAUSGABEN >= 0)
);

-- ------------------------------------------------------------------
-- Zahlung: angeordnete Auszahlung; VORGANGS_NR garantiert Idempotenz
-- (Doppelbuchung bei wiederholten Requests ist ausgeschlossen).
-- ------------------------------------------------------------------
CREATE TABLE ZAHLUNG (
    ID                NUMBER(19)        NOT NULL,
    BG_ID             NUMBER(19)        NOT NULL,
    FALL_ID           NUMBER(19),                      -- SozialFall, für den angeordnet wurde
    VORGANGS_NR       VARCHAR2(100 CHAR) NOT NULL,     -- UUID aus dem Frontend, eindeutig
    ZAHLUNGSMONAT     VARCHAR2(7 CHAR)  NOT NULL,      -- Abrechnungszeitraum, Format YYYY-MM
    AUSZAHLUNGSBETRAG NUMBER(10,2)      NOT NULL,
    ZAHLUNGSSTATUS    VARCHAR2(20 CHAR) DEFAULT 'BEREIT' NOT NULL,  -- ANGELEGT/BEREIT/UEBERGEBEN/STORNIERT
    ERSTELLT_AM       TIMESTAMP(6)      DEFAULT SYSTIMESTAMP NOT NULL,
    SACHBEARBEITER_ID VARCHAR2(50 CHAR),               -- Erfasser / anordnender Sachbearbeiter
    FREIGEGEBEN_VON   VARCHAR2(50 CHAR),               -- Rechnerisch festgestellt (Vier-Augen-Prinzip)
    EMPFAENGER_IBAN   VARCHAR2(34 CHAR),               -- Überweisungs-IBAN
    EMPFAENGER_BIC    VARCHAR2(11 CHAR),               -- Überweisungs-BIC
    EMPFAENGER_NAME   VARCHAR2(100 CHAR),              -- Name des Begünstigten
    VERMOEGENSEINSATZ NUMBER(10,2)      DEFAULT 0 NOT NULL, -- bei der Anordnung verbrauchtes verwertbares Vermögen (§ 90; Storno bucht genau diesen Betrag zurück)
    CONSTRAINT PK_ZAHLUNG PRIMARY KEY (ID),
    CONSTRAINT UQ_ZAHLUNG_VORGANG UNIQUE (VORGANGS_NR),
    CONSTRAINT FK_ZAHLUNG_BG FOREIGN KEY (BG_ID) REFERENCES BEDARFSGEMEINSCHAFT (ID),
    CONSTRAINT FK_ZAHLUNG_FALL FOREIGN KEY (FALL_ID) REFERENCES SOZIAL_FALL (ID),
    CONSTRAINT CK_ZAHLUNG_STATUS CHECK (ZAHLUNGSSTATUS IN ('ANGELEGT', 'BEREIT', 'UEBERGEBEN', 'STORNIERT')),
    CONSTRAINT CK_ZAHLUNG_BETRAG CHECK (AUSZAHLUNGSBETRAG > 0),
    CONSTRAINT CK_ZAHLUNG_VERMOEGENSEINSATZ CHECK (VERMOEGENSEINSATZ >= 0),
    CONSTRAINT CK_ZAHLUNG_MONAT CHECK (REGEXP_LIKE(ZAHLUNGSMONAT, '^[0-9]{4}-(0[1-9]|1[0-2])$'))
);

-- ------------------------------------------------------------------
-- Bescheid: Bewilligungsbescheid (§ 44 SGB XII); BESCHEID_DATUM ist das
-- Datum des Bescheids (die Bekanntgabe nach § 37 SGB X ist ein eigener Zeitpunkt).
-- ------------------------------------------------------------------
CREATE TABLE BESCHEID (
    ID              NUMBER(19)        NOT NULL,
    BG_ID           NUMBER(19)        NOT NULL,
    BESCHEID_NR     VARCHAR2(100 CHAR) NOT NULL,
    BESCHEID_DATUM  DATE              DEFAULT SYSDATE NOT NULL,  -- Bescheiddatum
    BEWILLIGUNG_VON VARCHAR2(7 CHAR)  NOT NULL,
    BEWILLIGUNG_BIS VARCHAR2(7 CHAR)  NOT NULL,
    ZAHLBETRAG      NUMBER(10,2)      NOT NULL,
    STATUS          VARCHAR2(20 CHAR) DEFAULT 'ERTEILT' NOT NULL,
    ERSTELLT_VON    VARCHAR2(50 CHAR) NOT NULL,
    ERSTELLT_AM     TIMESTAMP(6)      DEFAULT SYSTIMESTAMP NOT NULL,
    -- Berechnungs-Snapshot (versioniertes JSON) aus dem Erteilungszeitpunkt: der
    -- Nachdruck zeigt ausschließlich diesen Stand, auch wenn die Fälle später geändert
    -- werden. Bestehende Instanzen nachrüsten: ALTER TABLE BESCHEID ADD (BERECHNUNG CLOB);
    BERECHNUNG      CLOB,
    CONSTRAINT PK_BESCHEID PRIMARY KEY (ID),
    CONSTRAINT UQ_BESCHEID_NR UNIQUE (BESCHEID_NR),
    CONSTRAINT FK_BESCHEID_BG FOREIGN KEY (BG_ID) REFERENCES BEDARFSGEMEINSCHAFT (ID),
    CONSTRAINT CK_BESCHEID_STATUS CHECK (STATUS IN ('ERTEILT', 'AUFGEHOBEN')),
    CONSTRAINT CK_BESCHEID_VON CHECK (REGEXP_LIKE(BEWILLIGUNG_VON, '^[0-9]{4}-(0[1-9]|1[0-2])$')),
    CONSTRAINT CK_BESCHEID_BIS CHECK (REGEXP_LIKE(BEWILLIGUNG_BIS, '^[0-9]{4}-(0[1-9]|1[0-2])$'))
);

-- ------------------------------------------------------------------
-- Satztabelle: je Jahr gepflegte Regelbedarfsstufen (Anlage zu § 28 SGB XII).
-- Für einen Bezugsmonat gilt der zuletzt wirksam gewordenen Stand
-- (größtes GUELTIG_AB ≤ Monat) — der Jahreswechsel ist ein INSERT,
-- keine Codeänderung. Startwerte: ddl/lmg_seed.sql.
-- ------------------------------------------------------------------
CREATE TABLE SATZSTAND (
    GUELTIG_AB VARCHAR2(7 CHAR) NOT NULL,                -- erster Gültigkeitsmonat YYYY-MM
    RBS_1      NUMBER(10,2)     NOT NULL,                -- Alleinstehende/Alleinerziehende
    RBS_2      NUMBER(10,2)     NOT NULL,                -- volljährige Partner
    RBS_3      NUMBER(10,2)     NOT NULL,                -- Volljährige ohne eigene Wirtschaftsführung
    RBS_4      NUMBER(10,2)     NOT NULL,                -- Jugendliche 14 bis unter 18
    RBS_5      NUMBER(10,2)     NOT NULL,                -- Kinder 6 bis unter 14
    RBS_6      NUMBER(10,2)     NOT NULL,                -- Kinder unter 6
    CONSTRAINT PK_SATZSTAND PRIMARY KEY (GUELTIG_AB),
    CONSTRAINT CK_SATZSTAND_AB CHECK (REGEXP_LIKE(GUELTIG_AB, '^[0-9]{4}-(0[1-9]|1[0-2])$')),
    CONSTRAINT CK_SATZSTAND_POSITIV CHECK (RBS_1 > 0 AND RBS_2 > 0 AND RBS_3 > 0
        AND RBS_4 > 0 AND RBS_5 > 0 AND RBS_6 > 0)
);

-- ------------------------------------------------------------------
-- Journal: revisionsicheres Buchungsjournal (Vier-Augen-Prinzip, DSGVO-Nachweis)
-- ------------------------------------------------------------------
CREATE TABLE JOURNAL (
    ID                NUMBER(19)       NOT NULL,
    BG_ID             NUMBER(19),                     -- logische Referenz, kein FK (Historisierung)
    AKTION_TEXT       VARCHAR2(500 CHAR) NOT NULL,
    SACHBEARBEITER_ID VARCHAR2(50 CHAR)  NOT NULL,
    ERSTELLT_AM       TIMESTAMP(6)      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_JOURNAL PRIMARY KEY (ID)
);

-- ------------------------------------------------------------------
-- Sequences (Startwert über Seed-Datenbestand hinaus)
-- ------------------------------------------------------------------
CREATE SEQUENCE SEQ_BG       START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE SEQ_FALL     START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE SEQ_ZAHLUNG  START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE SEQ_JOURNAL  START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE SEQ_BESCHEID START WITH 1000 INCREMENT BY 1;

-- ------------------------------------------------------------------
-- Trigger für optimistisches Locking: VERSION wird bei jedem UPDATE erhöht,
-- außer der Aufrufer hat sie bereits selbst hochgesetzt.
-- ------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_BG_VERSION
BEFORE UPDATE ON BEDARFSGEMEINSCHAFT
FOR EACH ROW
BEGIN
    IF :new.VERSION IS NULL OR :new.VERSION <= :old.VERSION THEN
        :new.VERSION := :old.VERSION + 1;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_FALL_VERSION
BEFORE UPDATE ON SOZIAL_FALL
FOR EACH ROW
BEGIN
    IF :new.VERSION IS NULL OR :new.VERSION <= :old.VERSION THEN
        :new.VERSION := :old.VERSION + 1;
    END IF;
END;
/

-- ------------------------------------------------------------------
-- Fremdschlüssel-Indizes (Oracle indexiert FKs nicht automatisch)
-- ------------------------------------------------------------------
CREATE INDEX IX_SOZIAL_FALL_BG ON SOZIAL_FALL (BG_ID);
CREATE INDEX IX_ZAHLUNG_BG ON ZAHLUNG (BG_ID);
CREATE INDEX IX_ZAHLUNG_FALL ON ZAHLUNG (FALL_ID);
CREATE INDEX IX_ZAHLUNG_STATUS ON ZAHLUNG (ZAHLUNGSSTATUS);
CREATE INDEX IX_JOURNAL_BG ON JOURNAL (BG_ID);
CREATE INDEX IX_BG_WIEDERVORLAGE ON BEDARFSGEMEINSCHAFT (WIEDERVORLAGE_AM);
CREATE INDEX IX_BESCHEID_BG ON BESCHEID (BG_ID);

-- ------------------------------------------------------------------
-- Fachregel "pro Bedarfsgemeinschaft und Monat höchstens eine wirksame
-- Zahlung" als letzte Verteidigungslinie AUCH auf Datenbankebene: Der
-- Service prüft die Regel unter der BG-Sperre (existiertAktiveFuerMonat);
-- dieser function-based Unique-Index schützt zusätzlich jeden künftigen
-- Schreibpfad. STORNIERT-Zeilen werden über die CASE-Ausdrücke zu NULL und
-- sind damit von der Eindeutigkeit ausgenommen (stornierte Monate sind frei).
-- ------------------------------------------------------------------
CREATE UNIQUE INDEX UQ_ZAHLUNG_BG_MONAT_AKTIV ON ZAHLUNG (
    CASE WHEN ZAHLUNGSSTATUS <> 'STORNIERT' THEN BG_ID END,
    CASE WHEN ZAHLUNGSSTATUS <> 'STORNIERT' THEN ZAHLUNGSMONAT END
);
