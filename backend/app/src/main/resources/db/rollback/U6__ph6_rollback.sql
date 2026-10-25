-- U6 PH-6 rollback (hand-written).
DROP TABLE IF EXISTS report_export_job;
DROP TABLE IF EXISTS report_definition;
DROP TABLE IF EXISTS metric_daily_snapshot;
DELETE FROM system_setting WHERE key = 'report.export_ttl_hours';
