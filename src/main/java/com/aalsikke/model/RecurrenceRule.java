package com.aalsikke.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recurrence_rules")
public class RecurrenceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_item_id", nullable = false)
    private ScheduleItem scheduleItem;

    @Column(nullable = false, length = 20)
    private String frequency;

    @Column(name = "interval_value")
    private Integer intervalValue = 1;

    @Column(name = "days_of_week", length = 50)
    private String daysOfWeek;

    @Column(name = "until_date")
    private LocalDate untilDate;

    public RecurrenceRule() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ScheduleItem getScheduleItem() { return scheduleItem; }
    public void setScheduleItem(ScheduleItem scheduleItem) { this.scheduleItem = scheduleItem; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public Integer getIntervalValue() { return intervalValue; }
    public void setIntervalValue(Integer intervalValue) { this.intervalValue = intervalValue; }

    public String getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(String daysOfWeek) { this.daysOfWeek = daysOfWeek; }

    public LocalDate getUntilDate() { return untilDate; }
    public void setUntilDate(LocalDate untilDate) { this.untilDate = untilDate; }
}