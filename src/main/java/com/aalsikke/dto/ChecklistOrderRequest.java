package com.aalsikke.dto;

import java.util.List;
import java.util.UUID;

/** KAN-342: the task's checklist ids in their new order (all of them, each once). */
public record ChecklistOrderRequest(List<UUID> itemIds) {}