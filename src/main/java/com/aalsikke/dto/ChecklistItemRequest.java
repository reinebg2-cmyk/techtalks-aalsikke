package com.aalsikke.dto;

/**
 * KAN-342: add a step ({"text": "..."}) or change one ({"text": "..."} and/or {"done": true}).
 * Fields left out (null) are not changed.
 */
public record ChecklistItemRequest(String text, Boolean done) {}