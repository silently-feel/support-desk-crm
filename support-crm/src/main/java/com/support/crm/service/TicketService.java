package com.support.crm.service;

import com.support.crm.dto.*;
import com.support.crm.model.*;
import com.support.crm.repository.NoteRepository;
import com.support.crm.repository.TicketAuditLogRepository;
import com.support.crm.repository.TicketRepository;
import com.support.crm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final TicketAuditLogRepository auditLogRepository;
    private final SseNotificationService sseNotificationService;

    // 1. Create Ticket with Deduplication & Follow-up detection
    @Transactional
    public CreateTicketResponse createTicket(CreateTicketRequest request) {
        String email = request.getCustomer_email().trim();
        String subject = request.getSubject().trim();
        String description = request.getDescription().trim();

        List<Ticket> activeTickets = ticketRepository.findActiveTicketsByCustomerEmail(email);

        for (Ticket activeTicket : activeTickets) {
            boolean isSameSubject = activeTicket.getSubject().trim().equalsIgnoreCase(subject);

            if (isSameSubject) {
                boolean isSameDescription = activeTicket.getDescription().trim().equalsIgnoreCase(description);

                if (isSameDescription) {
                    return new CreateTicketResponse(activeTicket.getTicketId(), activeTicket.getCreatedAt());
                } else {
                    Note followUpNote = Note.builder()
                            .ticket(activeTicket)
                            .noteText("[Customer Follow-up / Additional Details]:\n" + description)
                            .authorName("Customer Follow-up")
                            .build();
                    noteRepository.save(followUpNote);
                    activeTicket.getNotes().add(followUpNote);
                    activeTicket.setUpdatedAt(LocalDateTime.now());
                    Ticket updated = ticketRepository.save(activeTicket);

                    sseNotificationService.broadcast("TICKET_UPDATED", updated);
                    return new CreateTicketResponse(activeTicket.getTicketId(), activeTicket.getCreatedAt());
                }
            }
        }

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
        sseNotificationService.broadcast("TICKET_UPDATED", savedTicket);
        return new CreateTicketResponse(savedTicket.getTicketId(), savedTicket.getCreatedAt());
    }

    // 2. List all tickets with SLA stopwatch & Agent mapping
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
                        t.getCreatedAt(),
                        t.getClaimedAt(),
                        t.getAssignedAgentName()))
                .collect(Collectors.toList());
    }

    // 3. Get detailed ticket with chronological notes + Author Info
    @Transactional(readOnly = true)
    public TicketDetailResponse getTicketByTicketId(String ticketId) {
        Ticket ticket = ticketRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found with ID: " + ticketId));

        List<TicketDetailResponse.NoteDto> noteDtos = ticket.getNotes().stream()
                .map(n -> new TicketDetailResponse.NoteDto(
                        n.getNoteText(),
                        n.getCreatedAt(),
                        n.getAuthorName() != null ? n.getAuthorName() : "Support Agent"
                ))
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

    // 4. Update ticket status & notes with Real Agent Identity + Note Author
    @Transactional
    public Map<String, Object> updateTicket(String ticketId, UpdateTicketRequest request, String loggedInUsername) {
        Ticket ticket = ticketRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found with ID: " + ticketId));

        if (request.getVersion() != null && !request.getVersion().equals(ticket.getVersion())) {
            throw new OptimisticLockingFailureException(
                    "Ticket has been modified by another user. Please refresh and review changes."
            );
        }

        // Fetch logged-in agent entity
        User agent = null;
        if (loggedInUsername != null && !loggedInUsername.isBlank()) {
            agent = userRepository.findByUsername(loggedInUsername.trim().toLowerCase()).orElse(null);
        }

        // Auto-claim if ticket is unassigned
        if (ticket.getAssignedAgentId() == null && agent != null) {
            ticket.setAssignedAgentId(agent.getId());
            ticket.setAssignedAgentName(agent.getFullname());
            if (ticket.getClaimedAt() == null) {
                ticket.setClaimedAt(LocalDateTime.now());
            }
        }

        if (request.getStatus() != null) {
            ticket.setStatus(request.getStatus());
            if (request.getStatus() == TicketStatus.CLOSED) {
                ticket.setResolvedAt(LocalDateTime.now());
            }
        }

        String performerName = (agent != null) ? agent.getFullname()
                : (ticket.getAssignedAgentName() != null ? ticket.getAssignedAgentName() : "Support Agent");
        Long performerId = (agent != null) ? agent.getId()
                : (ticket.getAssignedAgentId() != null ? ticket.getAssignedAgentId() : 0L);

        if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
            Note note = Note.builder()
                    .ticket(ticket)
                    .noteText(request.getNotes().trim())
                    .authorName(performerName)
                    .build();
            noteRepository.save(note);
            ticket.getNotes().add(note);
        }

        ticket.setUpdatedAt(LocalDateTime.now());
        Ticket updatedTicket = ticketRepository.save(ticket);

        TicketAuditLog log = TicketAuditLog.builder()
                .ticketId(ticketId)
                .userId(performerId)
                .userName(performerName)
                .action(request.getStatus() == TicketStatus.CLOSED ? "RESOLVED_TICKET" : "UPDATED_STATUS")
                .timestamp(LocalDateTime.now())
                .build();
        auditLogRepository.save(log);

        sseNotificationService.broadcast("TICKET_UPDATED", updatedTicket);

        return Map.of(
                "success", true,
                "version", updatedTicket.getVersion(),
                "updated_at", updatedTicket.getUpdatedAt() != null ? updatedTicket.getUpdatedAt() : LocalDateTime.now()
        );
    }

    // 5. Claim ticket by assigned agent
    @Transactional
    public void claimTicket(String ticketId, String loggedInUsername) {
        Ticket ticket = ticketRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));

        if (ticket.getAssignedAgentId() != null) {
            throw new IllegalStateException("Ticket already claimed by " + ticket.getAssignedAgentName());
        }

        User agent = userRepository.findByUsername(loggedInUsername.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + loggedInUsername));

        ticket.setAssignedAgentId(agent.getId());
        ticket.setAssignedAgentName(agent.getFullname());
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setClaimedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        TicketAuditLog log = TicketAuditLog.builder()
                .ticketId(ticketId)
                .userId(agent.getId())
                .userName(agent.getFullname())
                .action("CLAIMED_TICKET")
                .timestamp(LocalDateTime.now())
                .build();
        auditLogRepository.save(log);

        sseNotificationService.broadcast("TICKET_UPDATED", ticket);
    }

    // 6. Resolve ticket by assigned agent
    @Transactional
    public void resolveTicket(String ticketId, String loggedInUsername) {
        Ticket ticket = ticketRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));

        User agent = userRepository.findByUsername(loggedInUsername.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + loggedInUsername));

        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setResolvedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        TicketAuditLog log = TicketAuditLog.builder()
                .ticketId(ticketId)
                .userId(agent.getId())
                .userName(agent.getFullname())
                .action("RESOLVED_TICKET")
                .timestamp(LocalDateTime.now())
                .build();
        auditLogRepository.save(log);

        sseNotificationService.broadcast("TICKET_UPDATED", ticket);
    }

    // 7. Recent Audit Logs for Activity Feed
    @Transactional(readOnly = true)
    public List<TicketAuditLog> getRecentAuditLogs() {
        return auditLogRepository.findTop20ByOrderByTimestampDesc();
    }

    // 8. Admin Analytics Aggregation
    @Transactional(readOnly = true)
    public AdminAnalyticsResponse getAdminAnalytics() {
        List<Ticket> allTickets = ticketRepository.findAll();

        long total = allTickets.size();
        long open = allTickets.stream().filter(t -> t.getStatus() == TicketStatus.OPEN).count();
        long inProgress = allTickets.stream().filter(t -> t.getStatus() == TicketStatus.IN_PROGRESS).count();
        long resolved = allTickets.stream().filter(t -> t.getStatus() == TicketStatus.CLOSED).count();

        Map<String, Long> agentWorkload = allTickets.stream()
                .filter(t -> t.getAssignedAgentName() != null && !t.getAssignedAgentName().isBlank())
                .collect(Collectors.groupingBy(Ticket::getAssignedAgentName, Collectors.counting()));

        return AdminAnalyticsResponse.builder()
                .totalTickets(total)
                .openCount(open)
                .inProgressCount(inProgress)
                .resolvedCount(resolved)
                .agentWorkload(agentWorkload)
                .build();
    }
}