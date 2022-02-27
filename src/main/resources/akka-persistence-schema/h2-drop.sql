-- reference: https://github.com/akka/akka-persistence-jdbc/blob/v5.0.4/core/src/main/resources/schema/h2/h2-drop-schema.sql

DROP TABLE IF EXISTS "event_tag";
DROP TABLE IF EXISTS "event_journal";
DROP TABLE IF EXISTS "snapshot";
DROP TABLE IF EXISTS "durable_state";
