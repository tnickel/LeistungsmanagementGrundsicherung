-- Fachschema für das Leistungsmanagement Grundsicherung (SGB XII)
-- Produkt: SDN.sozial — Leistungsmanagement (LMG)
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
    WIEDERVORLAGE_AM DATE,                          -- Frist/Wiedervorlage für die Fallübersicht
    VERSION          NUMBER(10)         DEFAULT 1 NOT NULL,  -- optimistisches Locking
    CONSTRAINT PK_BEDARFSGEMEINSCHAFT PRIMARY KEY (ID),
    CONSTRAINT UQ_BG_AKTENZEICHEN UNIQUE (AKTENZEICHEN),
    CONSTRAINT CK_BG_STATUS_AKTIV CHECK (STATUS_AKTIV IN (0, 1)),
    CONSTRAINT CK_BG_BUDGET_REST CHECK (BUDGET_REST >= 0)
);

-- ------------------------------------------------------------------
-- SozialFall: ein Leistungsberechtigter innerhalb einer Bedarfsgemeinschaft.
-- Trägt alle Parameter der Bedarfs- und Einkommensermittlung (§ 27a, § 82 SGB XII).
-- ------------------------------------------------------------------
CREATE TABLE SOZIAL_FALL (
    ID                 NUMBER(19)    NOT NULL,
    BG_ID              NUMBER(19)    NOT NULL,
    REGELBEDARFSSTUFE  NUMBER(1)     NOT NULL,           -- 1..6, steuert den Basisbetrag
    FLAG_MERKZEICHEN_G NUMBER(1)     DEFAULT 0 NOT NULL, -- 1 = Merkzeichen G, 17 % Mehrbedarf
    KDU_MIETE          NUMBER(10,2)  DEFAULT 0 NOT NULL, -- anerkannte Kaltmiete
    KDU_HEIZUNG        NUMBER(10,2)  DEFAULT 0 NOT NULL, -- anerkannte Heizkosten
    EINKOMMEN_RENTE    NUMBER(10,2)  DEFAULT 0 NOT NULL, -- voll anrechenbare Renteneinkünfte
    EINKOMMEN_ERWERB   NUMBER(10,2)  DEFAULT 0 NOT NULL, -- Brutto-Erwerbseinkommen (Freibetrag § 82 Abs. 3)
    VERSION            NUMBER(10)    DEFAULT 1 NOT NULL, -- optimistisches Locking
    CONSTRAINT PK_SOZIAL_FALL PRIMARY KEY (ID),
    CONSTRAINT FK_SOZIAL_FALL_BG FOREIGN KEY (BG_ID) REFERENCES BEDARFSGEMEINSCHAFT (ID),
    CONSTRAINT CK_FALL_RBS CHECK (REGELBEDARFSSTUFE BETWEEN 1 AND 6),
    CONSTRAINT CK_FALL_MERKZEICHEN_G CHECK (FLAG_MERKZEICHEN_G IN (0, 1)),
    CONSTRAINT CK_FALL_KDU_MIETE CHECK (KDU_MIETE >= 0),
    CONSTRAINT CK_FALL_KDU_HEIZUNG CHECK (KDU_HEIZUNG >= 0),
    CONSTRAINT CK_FALL_EINK_RENTE CHECK (EINKOMMEN_RENTE >= 0),
    CONSTRAINT CK_FALL_EINK_ERWERB CHECK (EINKOMMEN_ERWERB >= 0)
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
    ZAHLUNGSSTATUS    VARCHAR2(20 CHAR) DEFAULT 'BEREIT' NOT NULL,  -- BEREIT/UEBERGEBEN/STORNIERT
    ERSTELLT_AM       TIMESTAMP(6)      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_ZAHLUNG PRIMARY KEY (ID),
    CONSTRAINT UQ_ZAHLUNG_VORGANG UNIQUE (VORGANGS_NR),
    CONSTRAINT FK_ZAHLUNG_BG FOREIGN KEY (BG_ID) REFERENCES BEDARFSGEMEINSCHAFT (ID),
    CONSTRAINT FK_ZAHLUNG_FALL FOREIGN KEY (FALL_ID) REFERENCES SOZIAL_FALL (ID),
    CONSTRAINT CK_ZAHLUNG_STATUS CHECK (ZAHLUNGSSTATUS IN ('BEREIT', 'UEBERGEBEN', 'STORNIERT')),
    CONSTRAINT CK_ZAHLUNG_BETRAG CHECK (AUSZAHLUNGSBETRAG > 0)
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
CREATE SEQUENCE SEQ_BG      START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE SEQ_FALL    START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE SEQ_ZAHLUNG START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE SEQ_JOURNAL START WITH 1000 INCREMENT BY 1;

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
CREATE INDEX IX_ZAHLUNG_STATUS ON ZAHLUNG (ZAHLUNGSSTATUS);
CREATE INDEX IX_JOURNAL_BG ON JOURNAL (BG_ID);
CREATE INDEX IX_BG_WIEDERVORLAGE ON BEDARFSGEMEINSCHAFT (WIEDERVORLAGE_AM);
