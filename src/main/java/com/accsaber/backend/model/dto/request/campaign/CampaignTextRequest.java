package com.accsaber.backend.model.dto.request.campaign;

import java.math.BigDecimal;

import com.accsaber.backend.validation.CleanText;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CampaignTextRequest {

    @Size(max = 4000)
    @CleanText
    private String content;

    @NotNull
    private Integer positionX;

    @NotNull
    private Integer positionY;

    @Size(max = 64)
    @Pattern(regexp = "^$|^[A-Za-z0-9 _-]{1,64}$", message = "invalid font")
    private String font;

    private BigDecimal scale;

    @Pattern(regexp = "^$|^#?[A-Za-z0-9]{1,32}$", message = "must be a hex or named color")
    private String color;

    @Size(max = 128)
    @Pattern(regexp = "^$|^[a-z0-9]+( [a-z0-9]+)*$", message = "invalid effects")
    private String effects;
}
