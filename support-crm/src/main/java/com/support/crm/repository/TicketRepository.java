package com.support.crm.repository;

import com.support.crm.model.Ticket;
import com.support.crm.model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByTicketId(String ticketId);

    long count();

    // Query to find all active tickets (OPEN or IN_PROGRESS) for a specific customer email
    @Query("SELECT t FROM Ticket t WHERE LOWER(t.customerEmail) = LOWER(:email) AND t.status IN ('OPEN', 'IN_PROGRESS') ORDER BY t.createdAt DESC")
    List<Ticket> findActiveTicketsByCustomerEmail(@Param("email") String email);

    @Query("SELECT t FROM Ticket t WHERE " +
            "(:status IS NULL OR t.status = :status) AND " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(t.customerName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(t.customerEmail) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(t.ticketId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(t.subject) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<Ticket> searchTickets(@Param("status") TicketStatus status, @Param("search") String search);
}