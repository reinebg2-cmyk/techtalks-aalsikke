package com.aalsikke.dto;

import java.time.OffsetDateTime;

public record UpdateReminderRequest(OffsetDateTime remindAt) {}