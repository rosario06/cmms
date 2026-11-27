package com.grash.dto.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class WhatsappTestRequestDTO {
    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Phone must be in E.164 format")
    private String phone;
}
