package com.griddynamics.esgraduationproject.model;

public class FacetBucket {
    private String value;
    private long count;

    public FacetBucket() {}

    public FacetBucket(String value, long count) {
        this.value = value;
        this.count = count;
    }

    public String getKey() {
        return value;
    }

    public void setKey(String key) {
        this.value = key;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
