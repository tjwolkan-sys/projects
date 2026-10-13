package com.powerdialer.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Generic outcome that carries an operation status together with the affected row count.
 * Reusable across services: OperationOutcome<YourStatusEnum> or OperationOutcome<String>, etc.
 */
public final class OperationOutcome<S> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final S status;
    private final int affectedCount;

    public OperationOutcome(S status, int affectedCount) {
        this.status = status;
        this.affectedCount = affectedCount;
    }

    public S getStatus() {
        return status;
    }

    public int getAffectedCount() {
        return affectedCount;
    }

    public static <S> OperationOutcome<S> of(S status, int affectedCount) {
        return new OperationOutcome<>(status, affectedCount);
    }

    @Override
    public String toString() {
        return "OperationOutcome{status=" + status + ", affectedCount=" + affectedCount + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OperationOutcome)) return false;
        OperationOutcome<?> that = (OperationOutcome<?>) o;
        return affectedCount == that.affectedCount && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, affectedCount);
    }
}
