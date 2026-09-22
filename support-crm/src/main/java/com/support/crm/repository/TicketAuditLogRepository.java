package com.support.crm.repository;

import com.support.crm.model.TicketAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketAuditLogRepository extends JpaRepository<TicketAuditLog , Long> {

    // Dashboard par dikhane ke liye: Sabse latest 20 activities (newest first)
    List<TicketAuditLog> findTop20ByOrderByTimestampDesc();

    // Kisi particular ticket ki poori history dekhne ke liye
    List<TicketAuditLog> findByTicketIdOrderByTimestampDesc(String ticketId);
}
