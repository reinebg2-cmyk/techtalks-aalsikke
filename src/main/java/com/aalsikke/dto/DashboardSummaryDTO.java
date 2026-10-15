package com.aalsikke.dto;

public class DashboardSummaryDTO {
    private long total;
    private long pending;
    private long completed;
    private long overdue;

    public DashboardSummaryDTO(long total, long pending, long completed, long overdue) {
        this.total = total;
        this.pending = pending;
        this.completed = completed;
        this.overdue = overdue;
    }

    // getters (no setters needed — this is read-only output)
    public long getTotal() { return total; }
    public long getPending() { return pending; }
    public long getCompleted() { return completed; }
    public long getOverdue() { return overdue; }
}