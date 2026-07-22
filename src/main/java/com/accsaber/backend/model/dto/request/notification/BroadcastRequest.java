package com.accsaber.backend.model.dto.request.notification;

import com.accsaber.backend.validation.CleanText;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BroadcastRequest {

    @NotBlank
    @Size(max = 200)
    @CleanText
    private String title;

    @Size(max = 500)
    private String linkTo;
}
