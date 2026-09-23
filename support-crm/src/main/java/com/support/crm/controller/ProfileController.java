package com.support.crm.controller;

import com.support.crm.dto.ChangePasswordRequest;
import com.support.crm.model.User;
import com.support.crm.repository.UserRepository;
import com.support.crm.security.CloudinaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryService cloudinaryService;

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
            User user = userRepository.findByUsername(auth.getName().trim().toLowerCase())
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + auth.getName()));

            // Upload directly to Cloudinary and get the persistent HTTPS CDN URL
            String cdnUrl = cloudinaryService.uploadAvatar(file, user.getId());

            // Save the CDN URL to the user record
            user.setAvatarUrl(cdnUrl);
            userRepository.save(user);

            redirectAttributes.addFlashAttribute("avatarSuccess", "Profile avatar updated successfully!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("avatarError", e.getMessage());
        } catch (Exception e) {
            log.error("Cloudinary upload failed: {}", e.getMessage(), e);
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