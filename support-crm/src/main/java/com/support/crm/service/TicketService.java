package com.support.crm.service;

import com.support.crm.dto.*;
import com.support.crm.model.Note;
import com.support.crm.model.Ticket;
import com.support.crm.model.TicketStatus;
import com.support.crm.repository.NoteRepository;
import com.support.crm.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final NoteRepository noteRepository;

    // 1. Create Ticket with Intelligent Deduplication & Follow-up Detection
    @Transactional
    public CreateTicketResponse createTicket(CreateTicketRequest request) {
        String email = request.getCustomer_email().trim();
        String subject = request.getSubject().trim();
        String description = request.getDescription().trim();

        // Check if customer has any active (OPEN or IN_PROGRESS) tickets
        List<Ticket> activeTickets = ticketRepository.findActiveTicketsByCustomerEmail(email);

        for (Ticket activeTicket : activeTickets) {
            boolean isSameSubject = activeTicket.getSubject().trim().equalsIgnoreCase(subject);

            if (isSameSubject) {
                boolean isSameDescription = activeTicket.getDescription().trim().equalsIgnoreCase(description);

                if (isSameDescription) {
                    // Case 1: Exact Carbon Copy duplicate -> Reuse active ticket without creating redundant record
                    return new CreateTicketResponse(activeTicket.getTicketId(), activeTicket.getCreatedAt());
                } else {
                    // Case 2: Same subject, different details -> Customer forgot to write something earlier
                    // Append additional details into activity notes
                    Note followUpNote = Note.builder()
                            .ticket(activeTicket)
                            .noteText("[Customer Follow-up / Additional Details]:\n" + description)
                            .build();
                    noteRepository.save(followUpNote);
                    activeTicket.getNotes().add(followUpNote);
                    activeTicket.setUpdatedAt(LocalDateTime.now());
                    ticketRepository.save(activeTicket);

                    return new CreateTicketResponse(activeTicket.getTicketId(), activeTicket.getCreatedAt());
                }
            }
        }

        // Case 3: New customer or genuine new issue -> Generate new sequential ticket
        long nextIndex = ticketRepository.count() + 1;
        String formattedTicketId = String.format("TKT-%03d", nextIndex);

        while (ticketRepository.findByTicketId(formattedTicketId).isPresent()) {
            nextIndex++;
            formattedTicketId = String.format("TKT-%03d", nextIndex);
        }

        Ticket ticket = Ticket.builder()
                .ticketId(formattedTicketId)
                .customerName(request.getCustomer_name().trim())
                .customerEmail(email)
                .subject(subject)
                .description(description)
                .status(TicketStatus.OPEN)
                .build();

        Ticket savedTicket = ticketRepository.save(ticket);
        return new CreateTicketResponse(savedTicket.getTicketId(), savedTicket.getCreatedAt());
    }

    // 2. List all tickets with optional status & search filter
    @Transactional(readOnly = true)
    public List<TicketSummaryResponse> getAllTickets(TicketStatus status, String search) {
        String querySearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        List<Ticket> tickets = ticketRepository.searchTickets(status, querySearch);

        return tickets.stream()
                .map(t -> new TicketSummaryResponse(
                        t.getTicketId(),
                        t.getCustomerName(),
                        t.getSubject(),
                        t.getStatus(),
                        t.getCreatedAt()))
                .collect(Collectors.toList());
    }

    // 3. Get detailed ticket with chronological notes and version
    @Transactional(readOnly = true)
    public TicketDetailResponse getTicketByTicketId(String ticketId) {
        Ticket ticket = ticketRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found with ID: " + ticketId));

        List<TicketDetailResponse.NoteDto> noteDtos = ticket.getNotes().stream()
                .map(n -> new TicketDetailResponse.NoteDto(n.getNoteText(), n.getCreatedAt()))
                .collect(Collectors.toList());

        return new TicketDetailResponse(
                ticket.getTicketId(),
                ticket.getCustomerName(),
                ticket.getCustomerEmail(),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getVersion(),
                noteDtos
        );
    }

    // 4. Update ticket status with Optimistic Locking support
    @Transactional
    public Map<String, Object> updateTicket(String ticketId, UpdateTicketRequest request) {
        Ticket ticket = ticketRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found with ID: " + ticketId));

        // Detect race condition if incoming version is older than current database version
        if (request.getVersion() != null && !request.getVersion().equals(ticket.getVersion())) {
            throw new OptimisticLockingFailureException(
                    "Ticket has been modified by another user. Please refresh and review changes."
            );
        }

        if (request.getStatus() != null) {
            ticket.setStatus(request.getStatus());
        }

        if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
            Note note = Note.builder()
                    .ticket(ticket)
                    .noteText(request.getNotes().trim())
                    .build();
            noteRepository.save(note);
            ticket.getNotes().add(note);
        }

        Ticket updatedTicket = ticketRepository.save(ticket);

        return Map.of(
                "success", true,
                "version", updatedTicket.getVersion(),
                "updated_at", updatedTicket.getUpdatedAt() != null ? updatedTicket.getUpdatedAt() : LocalDateTime.now()
        );
    }
}