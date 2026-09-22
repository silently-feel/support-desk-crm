package com.support.crm.controller;

import com.support.crm.dto.CreateTicketRequest;
import com.support.crm.dto.CreateTicketResponse;
import com.support.crm.dto.TicketDetailResponse;
import com.support.crm.service.NotificationService;
import com.support.crm.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/portal")
@RequiredArgsConstructor
public class CustomerPortalController {

    private final TicketService ticketService;
    private final NotificationService notificationService;

    // 1. Complaint Registration Form
    @GetMapping("/inquiry")
    public String openInquiryForm(Model model) {
        if (!model.containsAttribute("ticketRequest")) {
            model.addAttribute("ticketRequest", new CreateTicketRequest());
        }
        return "portal/inquiry";
    }

    // 2. Submit Complaint + Free Notification Dispatch
    @PostMapping("/inquiry")
    public String submitInquiry(@Valid @ModelAttribute("ticketRequest") CreateTicketRequest request,
                                BindingResult result,
                                RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "portal/inquiry";
        }

        CreateTicketResponse response = ticketService.createTicket(request);

        // Free Email/SMS notification dispatch
        notificationService.sendTicketCreatedNotification(
                request.getCustomer_email(),
                response.getTicket_id(),
                request.getSubject()
        );

        redirectAttributes.addFlashAttribute("createdRef", response.getTicket_id());
        return "redirect:/portal/inquiry?success=true";
    }

    // 3. Track Complaint Status
    @GetMapping("/track")
    public String trackTicket(@RequestParam(value = "ref", required = false) String ref, Model model) {
        if (ref != null && !ref.trim().isEmpty()) {
            try {
                TicketDetailResponse ticket = ticketService.getTicketByTicketId(ref.trim().toUpperCase());
                model.addAttribute("ticket", ticket);
            } catch (Exception ex) {
                model.addAttribute("notFound", true);
            }
            model.addAttribute("searchedRef", ref.trim().toUpperCase());
        }
        return "portal/track";
    }
}