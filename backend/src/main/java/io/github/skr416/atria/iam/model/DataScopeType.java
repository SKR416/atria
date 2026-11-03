package io.github.skr416.atria.iam.model;

/**
 * Role data-visibility level (ADR-0004). Lower rank value = broader scope.
 */
public enum DataScopeType {
    ALL(0),
    DEPT_AND_SUB(1),
    DEPT_ONLY(2),
    SELF_ONLY(3);

    private final int rank;

    DataScopeType(int rank) {
        this.rank = rank;
    }

    /** Returns the broader of two scopes. */
    public static DataScopeType broader(DataScopeType a, DataScopeType b) {
        return a.rank <= b.rank ? a : b;
    }
}
