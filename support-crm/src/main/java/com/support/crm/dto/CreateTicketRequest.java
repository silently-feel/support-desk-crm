package com.support.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTicketRequest {
    @NotBlank(message = "Customer Name Required")
    private String customer_name;

    @NotBlank(message = "Customer Email is Required")
    @Email(message = "Invalid Email")
    private String customer_email;

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Description is required")
    private String description;
}
