package com.siteflow.domain;

import java.time.LocalDateTime;

import com.siteflow.domain.enums.TransactionType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionLog {
    private Long id;
    private Long itemId;
    private Long locationId;
    private Long userId;
    private TransactionType transactionType;
    private Integer qtyChange;
    private Long referenceId;
    private LocalDateTime timestamp;
}
