package com.siteflow.domain.enums;

/**
 * Standardized audit event vocabulary for SiteFlow.
 * Enforces strong typing across business and security audit records,
 * avoiding arbitrary strings scattered throughout the codebase.
 */
public enum AuditEventType {
    // Authentication
    LOGIN_SUCCESS,
    LOGIN_FAILURE,

    // User Governance
    USER_DEACTIVATED,
    USER_PROVISIONED,

    // Borrowing & Asset Tracking
    BORROW_REQUEST_CREATED,
    BORROW_REQUEST_APPROVED,
    BORROW_REQUEST_REJECTED,
    BORROW_REQUEST_CANCELLED,
    ITEM_BORROWED,
    ITEM_RETURNED,

    // Inventory & Stock
    STOCK_ADJUSTED,

    // Procurement & Material Requests
    MATERIAL_REQUEST_CREATED,
    MATERIAL_REQUEST_APPROVED,
    MATERIAL_REQUEST_REJECTED,
    MATERIAL_REQUEST_CANCELLED,
    PURCHASE_ORDER_CREATED
}
