package com.support.crm.controller;

import com.support.crm.dto.*;
import com.support.crm.model.TicketStatus;
import com.support.crm.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            @RequestBody UpdateTicketRequest request) {
        try {
            Map<String, Object> response = ticketService.updateTicket(ticketId, request);
            return ResponseEntity.ok(response);
        } catch (OptimisticLockingFailureException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Conflict",
                    "message", ex.getMessage()
            ));
        }
    }
}