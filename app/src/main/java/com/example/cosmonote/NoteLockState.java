package com.example.cosmonote;

public class NoteLockState {
    private final String lockedByUid;
    private final String lockedByName;
    private final long expiresAtMillis;

    public NoteLockState(String lockedByUid, String lockedByName, long expiresAtMillis) {
        this.lockedByUid = lockedByUid;
        this.lockedByName = lockedByName;
        this.expiresAtMillis = expiresAtMillis;
    }

    public String getLockedByUid() {
        return lockedByUid;
    }

    public String getLockedByName() {
        return lockedByName;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean isActive(long nowMillis) {
        return expiresAtMillis > nowMillis;
    }

    public boolean isLockedByOtherUser(String currentUid, long nowMillis) {
        return isActive(nowMillis)
                && lockedByUid != null
                && currentUid != null
                && !lockedByUid.equals(currentUid);
    }
}

