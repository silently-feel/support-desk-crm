package com.support.crm.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class TicketAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ticketId; // Jaise "TKT-001"

    @Column(nullable = false)
    private Long userId; // Employee ID (Jaise 2)

    @Column(nullable = false)
    private String userName; // Employee Full Name (Jaise "Satyam Developer")

    @Column(nullable = false)
    private String action; // "CLAIMED", "RESOLVED", "UPDATED_NOTES"

    @Column(nullable = false)
    private LocalDateTime timestamp;
}
