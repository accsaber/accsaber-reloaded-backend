package com.accsaber.backend.model.dto.request.clan;

import com.accsaber.backend.validation.CleanText;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateClanRequest {

    @NotBlank
    @Size(min = 3, max = 32)
    @CleanText
    private String name;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9]{2,5}$")
    @CleanText
    private String tag;

    @Size(max = 500)
    @CleanText
    private String description;
}
