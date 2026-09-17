package com.siteflow.domain;

import java.time.LocalDateTime;

import com.siteflow.domain.enums.ToolCondition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemInstance {
    private Long id;
    private Long itemId;
    private String serialNumber;
    private String qrCodeValue;
    private ToolCondition toolCondition;
    private Boolean isAvailable;
    private Long currentBorrowRequestId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
