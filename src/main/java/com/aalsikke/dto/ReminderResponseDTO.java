package com.aalsikke.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReminderResponseDTO(
        UUID id,
        UUID scheduleItemId,
        String scheduleItemTitle,
        OffsetDateTime remindAt,
        boolean isTriggered
) {}