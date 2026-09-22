package com.support.crm.controller;

import com.support.crm.dto.ChangePasswordRequest;
import com.support.crm.model.User;
import com.support.crm.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public String viewProfile(Authentication auth, Model model) {
        if (auth == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByUsername(auth.getName().trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + auth.getName()));

        model.addAttribute("user", user);
        if (!model.containsAttribute("passwordRequest")) {
            model.addAttribute("passwordRequest", new ChangePasswordRequest());
        }

        return "profile/view";
    }

    @PostMapping("/avatar")
    public String uploadAvatar(@RequestParam("avatarFile") MultipartFile file,
                               Authentication auth,
                               RedirectAttributes redirectAttributes) {
        if (auth == null) return "redirect:/login";
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("avatarError", "Please select a photo to upload.");
            return "redirect:/profile";
        }

        try {
            User user = userRepository.findByUsername(auth.getName().trim().toLowerCase()).orElseThrow();
            String uploadDir = "uploads/avatars";
            Path uploadPath = Paths.get(uploadDir);

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "avatar.png";
            String fileExt = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".")) : ".png";
            String fileName = "user_" + user.getId() + "_" + System.currentTimeMillis() + fileExt;

            Path filePath = uploadPath.resolve(fileName);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            user.setAvatarUrl("/uploads/avatars/" + fileName);
            userRepository.save(user);

            redirectAttributes.addFlashAttribute("avatarSuccess", "Profile avatar updated successfully!");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("avatarError", "Failed to upload photo: " + e.getMessage());
        }

        return "redirect:/profile";
    }

    @PostMapping("/change-password")
    public String changePassword(@Valid @ModelAttribute("passwordRequest") ChangePasswordRequest request,
                                 BindingResult bindingResult,
                                 Authentication auth,
                                 RedirectAttributes redirectAttributes) {
        if (auth == null) return "redirect:/login";

        User user = userRepository.findByUsername(auth.getName().trim().toLowerCase()).orElseThrow();

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            bindingResult.rejectValue("currentPassword", "mismatch", "Incorrect current password.");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "nomatch", "New password and confirmation do not match.");
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.passwordRequest", bindingResult);
            redirectAttributes.addFlashAttribute("passwordRequest", request);
            return "redirect:/profile";
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword().trim()));
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("passwordSuccess", "Password updated successfully!");
        return "redirect:/profile";
    }
}