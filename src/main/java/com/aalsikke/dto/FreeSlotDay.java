package com.aalsikke.dto;

import com.aalsikke.service.FreeSlots;

import java.time.LocalDate;
import java.util.List;

/** KAN-343: the free slots found on one day (empty list = no free slot that day). */
public record FreeSlotDay(LocalDate date, List<FreeSlots.Slot> slots) {}