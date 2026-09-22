package com.support.crm.controller;

import com.support.crm.dto.*;
import com.support.crm.model.TicketStatus;
import com.support.crm.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TicketRestController {

    private final TicketService ticketService;

    // 1. POST /api/tickets
    @PostMapping
    public ResponseEntity<CreateTicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        CreateTicketResponse response = ticketService.createTicket(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. GET /api/tickets?status=Open&search=query
    @GetMapping
    public ResponseEntity<List<TicketSummaryResponse>> getTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) String search) {
        List<TicketSummaryResponse> tickets = ticketService.getAllTickets(status, search);
        return ResponseEntity.ok(tickets);
    }

    // 3. GET /api/tickets/{ticket_id}
    @GetMapping("/{ticket_id}")
    public ResponseEntity<TicketDetailResponse> getTicket(@PathVariable("ticket_id") String ticketId) {
        TicketDetailResponse ticket = ticketService.getTicketByTicketId(ticketId);
        return ResponseEntity.ok(ticket);
    }

    // 4. PUT /api/tickets/{ticket_id} with Optimistic Lock Conflict Handling
    @PutMapping("/{ticket_id}")
    public ResponseEntity<?> updateTicket(
            @PathVariable("ticket_id") String ticketId,
            @RequestBody UpdateTicketRequest request,
            Authentication auth) {
        try {
            String loggedInUsername = (auth != null) ? auth.getName() : null;
            Map<String, Object> response = ticketService.updateTicket(ticketId, request, loggedInUsername);
            return ResponseEntity.ok(response);
        } catch (OptimisticLockingFailureException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Conflict",
                    "message", ex.getMessage()
            ));
        }
    }

    // 5. POST /api/tickets/{ticket_id}/claim
    @PostMapping("/{ticket_id}/claim")
    public ResponseEntity<?> claimTicket(@PathVariable("ticket_id") String ticketId, Authentication auth) {
        try {
            String username = (auth != null) ? auth.getName() : "Agent";
            ticketService.claimTicket(ticketId, username);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Ticket " + ticketId + " claimed by " + username
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "AlreadyClaimed",
                    "message", ex.getMessage()
            ));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "BadRequest",
                    "message", ex.getMessage()
            ));
        }
    }

    // 6. POST /api/tickets/{ticket_id}/resolve
    @PostMapping("/{ticket_id}/resolve")
    public ResponseEntity<?> resolveTicket(@PathVariable("ticket_id") String ticketId, Authentication auth) {
        try {
            String username = (auth != null) ? auth.getName() : "Agent";
            ticketService.resolveTicket(ticketId, username);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Ticket " + ticketId + " marked as resolved by " + username
            ));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "BadRequest",
                    "message", ex.getMessage()
            ));
        }
    }
}