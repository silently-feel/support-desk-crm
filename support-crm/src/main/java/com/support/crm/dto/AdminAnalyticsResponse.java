package com.support.crm.dto;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAnalyticsResponse {
    private long totalTickets;
    private long openCount;
    private long inProgressCount;
    private long resolvedCount;
    private Map<String , Long> agentWorkload;
}
