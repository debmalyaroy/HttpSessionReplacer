package com.test.session.models;

/**
 * The attribute descriptor contains value of the attribute as well as flags
 * that indicate if attribute was deleted or changed. Changed attribute would be
 * sent to repository.
 */
public final class SessionAttribute {
    private Object value;
    private boolean deleted;
    private boolean changed;

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean changed) {
        this.changed = changed;
    }

    @Override
    public String toString() {
        return String.format("SessionAttribute [value=%s, deleted=%s, changed=%s]", value, deleted, changed);
    }
}