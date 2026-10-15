package com.aalsikke.dto;

import com.aalsikke.model.ChecklistItem;

import java.util.UUID;

/** KAN-342: one checklist step as sent to the browser. */
public record ChecklistItemResponse(UUID id, String text, boolean done, int position) {
    public static ChecklistItemResponse from(ChecklistItem item) {
        return new ChecklistItemResponse(item.getId(), item.getText(), item.isDone(), item.getPosition());
    }
}