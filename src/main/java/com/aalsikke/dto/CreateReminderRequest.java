package com.aalsikke.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateReminderRequest(
        UUID scheduleItemId,
        OffsetDateTime remindAt
) {}