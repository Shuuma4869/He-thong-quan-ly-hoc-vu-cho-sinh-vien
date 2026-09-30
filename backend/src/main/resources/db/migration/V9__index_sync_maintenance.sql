DROP INDEX sync_run_history;
CREATE INDEX sync_run_history ON sync_run(user_id, requested_at DESC, id DESC);

CREATE INDEX sync_run_cleanup ON sync_run(finished_at, id)
    WHERE status IN ('SUCCEEDED', 'PARTIAL', 'FAILED');

CREATE INDEX phenikaa_connection_auto_connected ON phenikaa_connection(user_id, created_at)
    WHERE status = 'CONNECTED';
