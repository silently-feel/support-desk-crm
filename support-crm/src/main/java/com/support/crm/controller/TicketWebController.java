package com.support.crm.controller;

import com.support.crm.dto.CreateTicketRequest;
import com.support.crm.dto.CreateTicketResponse;
import com.support.crm.dto.TicketDetailResponse;
import com.support.crm.dto.TicketSummaryResponse;
import com.support.crm.dto.UpdateTicketRequest;
import com.support.crm.model.TicketStatus;
import com.support.crm.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class TicketWebController {

    private final TicketService ticketService;

    // Direct root access automatically redirects to tickets console
    @GetMapping("/")
    public String rootRedirect() {
        return "redirect:/tickets";
    }

    @GetMapping("/tickets")
    public String dashboard(@RequestParam(required = false) TicketStatus status,
                            @RequestParam(required = false) String search,
                            Model model) {
        List<TicketSummaryResponse> tickets = ticketService.getAllTickets(status, search);
        model.addAttribute("tickets", tickets);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("searchQuery", search);
        return "tickets/dashboard";
    }

    @GetMapping("/tickets/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("ticketRequest")) {
            model.addAttribute("ticketRequest", new CreateTicketRequest());
        }
        return "tickets/create";
    }

    @PostMapping("/tickets/new")
    public String submitCreate(@Valid @ModelAttribute("ticketRequest") CreateTicketRequest request,
                               BindingResult bindingResult,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "tickets/create";
        }

        CreateTicketResponse response = ticketService.createTicket(request);
        redirectAttributes.addFlashAttribute("toastMessage", "Inquiry processed successfully under Ticket " + response.getTicket_id());
        redirectAttributes.addFlashAttribute("toastType", "success");
        return "redirect:/tickets";
    }

    @GetMapping("/tickets/{ticket_id}")
    public String viewDetail(@PathVariable String ticket_id, Model model) {
        TicketDetailResponse ticket = ticketService.getTicketByTicketId(ticket_id);
        model.addAttribute("ticket", ticket);
        if (!model.containsAttribute("updateRequest")) {
            UpdateTicketRequest updateReq = new UpdateTicketRequest();
            updateReq.setStatus(ticket.getStatus());
            updateReq.setVersion(ticket.getVersion());
            model.addAttribute("updateRequest", updateReq);
        }
        return "tickets/detail";
    }

    @PostMapping("/tickets/{ticket_id}/update")
    public String updateTicket(@PathVariable String ticket_id,
                               @ModelAttribute("updateRequest") UpdateTicketRequest request,
                               RedirectAttributes redirectAttributes) {
        try {
            ticketService.updateTicket(ticket_id, request);
            redirectAttributes.addFlashAttribute("toastMessage", "Ticket status and notes updated successfully!");
            redirectAttributes.addFlashAttribute("toastType", "success");
        } catch (OptimisticLockingFailureException ex) {
            redirectAttributes.addFlashAttribute("toastMessage", "Conflict detected: Ticket was updated by another user. Please refresh and review.");
            redirectAttributes.addFlashAttribute("toastType", "danger");
        }

        return "redirect:/tickets/" + ticket_id;
    }
}