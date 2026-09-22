package com.support.crm.controller;

import com.support.crm.model.User;
import com.support.crm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalUserControllerAdvice {

    private final UserRepository userRepository;

    @ModelAttribute("currentUser")
    public User populateCurrentUser(Authentication auth) {
        if (auth != null && auth.isAuthenticated()) {
            return userRepository.findByUsername(auth.getName().trim().toLowerCase()).orElse(null);
        }
        return null;
    }
}