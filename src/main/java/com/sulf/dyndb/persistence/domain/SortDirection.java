package com.sulf.dyndb.persistence.domain;

public enum SortDirection {
    ASC,
    DESC;

    public boolean scanIndexForward() {
        return this == ASC;
    }
}
