-- Schema für die eigene Transaktionssteuerung
-- Ausführen als Schema-Owner, z. B. TX_DEMO in einer PDB.

-- Journal für lange Geschäftsvorgänge (Saga-Muster)
-- STATE (Lebenszyklus des Vorgangs):
--   RUNNING       – in Ausführung
--   COMPLETED     – alle Schritte erfolgreich committet
--   FAILED        – abgebrochen; Kompensation unvollständig oder nichts zu kompensieren
--   COMPENSATING  – Rückwärtslauf läuft
--   COMPENSATED   – alle Schritte rückwärts kompensiert
CREATE TABLE TX_SAGA (
    SAGA_ID     VARCHAR2(36 CHAR)  NOT NULL,
    SAGA_TYPE   VARCHAR2(100 CHAR) NOT NULL,
    STATE       VARCHAR2(20 CHAR)  NOT NULL,
    PAYLOAD     VARCHAR2(2000 CHAR),
    CREATED_AT  TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATED_AT  TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_TX_SAGA PRIMARY KEY (SAGA_ID),
    CONSTRAINT CK_TX_SAGA_STATE CHECK
        (STATE IN ('RUNNING', 'COMPLETED', 'FAILED', 'COMPENSATING', 'COMPENSATED'))
);

-- Schritte eines Vorgangs; STATE dokumentiert den Write-Ahead des Saga-Kerns:
--   PENDING     – noch nicht begonnen
--   IN_FLIGHT   – Write-Ahead MARKIERT VOR dem Fachschritt; bedeutet nach einem Absturz
--                 „möglicherweise committet" und wird vom Wiederanlauf konservativ
--                 mit kompensiert (Kompensationen sind daher idempotent zu schreiben)
--   DONE        – Fachschritt UND Journal-Eintrag atomar committet
--   COMPENSATED – Kompensation UND Journal-Eintrag atomar committet
CREATE TABLE TX_SAGA_STEP (
    SAGA_ID    VARCHAR2(36 CHAR)  NOT NULL,
    IDX        NUMBER(5)          NOT NULL,
    NAME       VARCHAR2(100 CHAR),
    STATE      VARCHAR2(20 CHAR)  NOT NULL,
    UPDATED_AT TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_TX_SAGA_STEP PRIMARY KEY (SAGA_ID, IDX),
    CONSTRAINT FK_TX_SAGA_STEP FOREIGN KEY (SAGA_ID) REFERENCES TX_SAGA (SAGA_ID),
    CONSTRAINT CK_TX_SAGA_STEP_STATE CHECK
        (STATE IN ('PENDING', 'IN_FLIGHT', 'DONE', 'COMPENSATED'))
);

CREATE INDEX IX_TX_SAGA_STATE ON TX_SAGA (STATE);
-- FK-Index für die Schrittzeilen: recover() liest TX_SAGA_STEP per SAGA_ID — ohne
-- Index läuft jeder Wiederanlauf mit einem Full Scan je Saga (Oracle indexiert FKs nicht).
CREATE INDEX IX_TX_SAGA_STEP_SAGA ON TX_SAGA_STEP (SAGA_ID);

-- Demo-Tabellen für die Beispielverarbeitung (Antrag → Bescheid → Auszahlung).
-- SAGA_ID korreliert jede Zeile mit genau einer Saga: Die Kompensationen greifen
-- NUR auf die eigenen Zeilen der Saga zu (auch bei parallelen Demo-Sagas mit
-- gleichem Namen) und bleiben damit idempotent.
CREATE TABLE TX_DEMO_ANTRAG (
    ID         NUMBER(10) GENERATED ALWAYS AS IDENTITY,
    NAME       VARCHAR2(100 CHAR) NOT NULL,
    STATUS     VARCHAR2(30 CHAR)  NOT NULL,           -- NEU/BESCHIEDEN/AUSGEZAHLT
    BETRAG     NUMBER(12, 2)      NOT NULL,
    SAGA_ID    VARCHAR2(36 CHAR),
    CREATED_AT TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_TX_DEMO_ANTRAG PRIMARY KEY (ID)
);

CREATE TABLE TX_DEMO_ZAHLUNG (
    ID        NUMBER(10) GENERATED ALWAYS AS IDENTITY,
    ANTRAG_ID NUMBER(10)  NOT NULL,
    BETRAG    NUMBER(12, 2) NOT NULL,
    BOOKED_AT TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_TX_DEMO_ZAHLUNG PRIMARY KEY (ID),
    CONSTRAINT FK_TX_DEMO_ZAHLUNG FOREIGN KEY (ANTRAG_ID) REFERENCES TX_DEMO_ANTRAG (ID)
);

CREATE INDEX IX_TX_DEMO_ANTRAG_SAGA ON TX_DEMO_ANTRAG (SAGA_ID);
