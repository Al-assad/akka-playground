-- reference: https://github.com/akka/akka-persistence-jdbc/blob/v5.0.4/core/src/main/resources/schema/postgres/postgres-drop-schema.sql

DROP TABLE IF EXISTS public.event_tag;
DROP TABLE IF EXISTS public.event_journal;
DROP TABLE IF EXISTS public.snapshot;
DROP TABLE IF EXISTS public.durable_state;
