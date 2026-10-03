package com.aalsikke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** KAN-291: body of PUT /api/users/me/palette. */
@Getter
@Setter
public class ColorPaletteRequest {

    @NotBlank(message = "Palette is required.")
    @Pattern(regexp = "brand|ocean|forest|sunset|violet|rose|graphite",
            message = "Palette must be one of: brand, ocean, forest, sunset, violet, rose, graphite.")
    private String palette;
}
