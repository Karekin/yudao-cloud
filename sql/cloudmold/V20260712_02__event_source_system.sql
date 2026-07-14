-- Add the source required by domain-event-envelope-v1 without rewriting the applied V01 migration.
ALTER TABLE `cloudmold_event_outbox`
  ADD COLUMN `source_system` varchar(64) NOT NULL DEFAULT 'cloudmold' AFTER `schema_version`;
