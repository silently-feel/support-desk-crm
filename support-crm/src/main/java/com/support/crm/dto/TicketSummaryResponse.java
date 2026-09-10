package com.support.crm.dto;

import com.support.crm.model.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TicketSummaryResponse {
    private String ticket_id;
    private String customer_name;
    private String subject;
    private TicketStatus status;
    private LocalDateTime created_at;
}
