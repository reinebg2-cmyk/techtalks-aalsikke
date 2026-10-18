package com.aalsikke.dto;

import com.aalsikke.model.ItemType;
import com.aalsikke.model.Priority;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

/** KAN-372: what the New task form is pre-filled with. Times are Beirut local time; null = left for the user. */
public record QuickParseResponse(
        String title,
        LocalDateTime start,
        LocalDateTime due,
        ItemType category,
        Priority priority,
        List<DayOfWeek> repeatDays) {
}