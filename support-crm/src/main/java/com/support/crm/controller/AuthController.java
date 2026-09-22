package com.support.crm.controller;

import com.support.crm.dto.RegisterRequest;
import com.support.crm.model.User;
import com.support.crm.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            Model model) {
        if (error != null) {
            model.addAttribute("errorMessage", "Invalid username or password. Please try again.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "You have been logged out securely.");
        }
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signupPage(Model model) {
        if (!model.containsAttribute("registerRequest")) {
            model.addAttribute("registerRequest", new RegisterRequest());
        }
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String processSignup(@Valid @ModelAttribute("registerRequest") RegisterRequest request,
                                BindingResult result,
                                RedirectAttributes redirectAttributes) {

        String cleanedUsername = request.getUsername().trim().toLowerCase();
        String cleanedEmail = request.getEmail().trim().toLowerCase();
        String cleanedMobile = request.getMobileNumber().trim();

        // Unique checks across Username, Email, and Mobile
        if (userRepository.existsByUsername(cleanedUsername)) {
            result.rejectValue("username", "duplicate", "This username is already taken.");
        }

        if (userRepository.existsByEmail(cleanedEmail)) {
            result.rejectValue("email", "duplicate", "This email address is already registered.");
        }

        if (userRepository.existsByMobileNumber(cleanedMobile)) {
            result.rejectValue("mobileNumber", "duplicate", "This mobile number is already linked to another account.");
        }

        if (result.hasErrors()) {
            return "auth/signup";
        }

        User newAgent = User.builder()
                .fullname(request.getFullname().trim())
                .username(cleanedUsername)
                .email(cleanedEmail)
                .mobileNumber(cleanedMobile)
                .password(passwordEncoder.encode(request.getPassword().trim()))
                .role("ROLE_AGENT")
                .build();

        userRepository.save(newAgent);

        redirectAttributes.addFlashAttribute("successMessage", "Agent account created successfully! Please login.");
        return "redirect:/login";
    }
}