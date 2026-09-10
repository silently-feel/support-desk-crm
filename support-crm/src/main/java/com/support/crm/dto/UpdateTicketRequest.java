package com.support.crm.dto;

import com.support.crm.model.TicketStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTicketRequest {
    private TicketStatus status;
    private String notes;
    private Long version; // Track entity version for optimistic locking
}