package com.aalsikke.dto;

/** KAN-291: the signed-in user's selected color palette preset. */
public class ColorPaletteResponse {

    private final String palette;

    public ColorPaletteResponse(String palette) {
        this.palette = palette;
    }

    public String getPalette() { return palette; }
}
