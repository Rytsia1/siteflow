package com.siteflow.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BorrowItem {
    private Long id;
    private Long borrowRequestId;
    private Long itemId;
    private Integer qtyBorrowed;
    private Integer qtyReturned;
    private LocalDateTime returnDate;
}
