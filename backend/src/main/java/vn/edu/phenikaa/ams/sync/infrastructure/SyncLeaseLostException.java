package vn.edu.phenikaa.ams.sync.infrastructure;

public final class SyncLeaseLostException extends RuntimeException {
    public SyncLeaseLostException() { super("SYNC_LEASE_LOST", null, false, false); }
}
