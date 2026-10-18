package com.aalsikke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** KAN-372: the sentence typed in the quick-add box. */
public record QuickParseRequest(
        @NotBlank(message = "Type something to add")
        @Size(max = 300, message = "Keep it under 300 characters")
        String text) {
}