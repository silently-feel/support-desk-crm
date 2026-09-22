package com.support.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    private String fullname;

    @NotBlank(message = "Username is required")
    @Pattern(regexp = "^[a-z0-9]+$", message = "Username must be strictly lowercase letters and digits without spaces or special symbols")
    @Size(min = 3, max = 25, message = "Username must be between 3 and 25 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be a valid 10-digit number")
    private String mobileNumber;

    @NotBlank(message = "Password is required")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]{8,}$",
            message = "Password must contain at least 1 lowercase, 1 uppercase, 1 number, 1 special character (@$!%*?&#), and min 8 characters"
    )
    private String password;
}