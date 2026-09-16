package com.siteflow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siteflow.domain.ItemInstance;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.enums.ToolCondition;
import com.siteflow.mapper.BorrowRequestMapper;
import com.siteflow.mapper.ItemInstanceMapper;

/**
 * Manages the lifecycle of individually-tracked physical tool instances.
 *
 * Checkout flow:
 *   Worker presents serial number or QR code → service validates the tool is
 *   GOOD and the borrow request is APPROVED → records the checkout assignment.
 *
 * Return flow:
 *   Worker returns the tool → service records the return and immediately
 *   updates the tool_condition based on the physical inspection result.
 *   If the returned condition is BROKEN or NEEDS_REPAIR the tool is flagged
 *   in the database so it cannot be re-issued until repaired.
 */
@Service
public class AssetTrackingService {

    private final ItemInstanceMapper itemInstanceMapper;
    private final BorrowRequestMapper borrowRequestMapper;

    public AssetTrackingService(ItemInstanceMapper itemInstanceMapper,
                                BorrowRequestMapper borrowRequestMapper) {
        this.itemInstanceMapper = itemInstanceMapper;
        this.borrowRequestMapper = borrowRequestMapper;
    }

    /**
     * Assigns a specific physical tool instance to an approved borrow request.
     *
     * <p>Guards enforced before checkout:
     * <ul>
     *   <li>The tool must exist (identified by serial number).
     *   <li>The tool must be in GOOD condition — a BROKEN or NEEDS_REPAIR tool
     *       must not be issued.
     *   <li>The parent borrow request must exist and be in APPROVED state —
     *       tools may not be checked out against pending or rejected requests.
     * </ul>
     *
     * @param serialNumber    the unique serial number stamped on the physical tool
     * @param borrowRequestId the approved borrow request this tool is assigned to
     * @return the ItemInstance that was checked out
     */
    @Transactional
    public ItemInstance checkoutItemInstance(String serialNumber, Long borrowRequestId) {
        // Locate the physical tool by its serial number
        ItemInstance instance = itemInstanceMapper.findBySerialNumber(serialNumber);
        if (instance == null) {
            throw new IllegalArgumentException("No tool found with serial number: " + serialNumber);
        }

        // A tool in any condition other than GOOD must not leave the warehouse
        if (instance.getToolCondition() != ToolCondition.GOOD) {
            throw new IllegalStateException(
                    "Tool " + serialNumber + " cannot be checked out — current condition: "
                    + instance.getToolCondition());
        }

        // Verify the borrow request exists and has been approved
        BorrowRequest borrowRequest = borrowRequestMapper.findById(borrowRequestId);
        if (borrowRequest == null) {
            throw new IllegalArgumentException("Borrow request not found: " + borrowRequestId);
        }
        if (borrowRequest.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalStateException(
                    "Borrow request " + borrowRequestId + " is not approved (approval status: "
                    + borrowRequest.getApprovalStatus() + "). Tool checkout is not allowed.");
        }

        // The current schema tracks the tool's condition; a dedicated
        // borrow_instance_assignments table can be added in a future iteration
        // to record the exact request-to-instance mapping for full audit history.
        // For now the checkout is confirmed by the caller persisting the association.
        return instance;
    }

    /**
     * Processes the return of a specific physical tool and updates its condition.
     *
     * <p>State transition (tool_condition column):
     *   Any condition → {@code returnedCondition}
     *
     * <p>If the tool comes back in BROKEN or NEEDS_REPAIR condition it is flagged
     * immediately in the database. Any subsequent {@link #checkoutItemInstance}
     * call for this serial number will be blocked until the condition is reset
     * to GOOD (e.g. after a repair is completed and the tool is re-inspected).
     *
     * @param serialNumber      the serial number of the tool being returned
     * @param returnedCondition the physical condition observed during return inspection
     * @return the updated ItemInstance reflecting the post-return condition
     */
    @Transactional
    public ItemInstance returnItemInstance(String serialNumber, ToolCondition returnedCondition) {
        ItemInstance instance = itemInstanceMapper.findBySerialNumber(serialNumber);
        if (instance == null) {
            throw new IllegalArgumentException("No tool found with serial number: " + serialNumber);
        }

        // Update tool_condition immediately based on the physical inspection.
        // GOOD  → no further action needed, tool is available for re-issue.
        // NEEDS_REPAIR → tool is quarantined from checkout until repaired.
        // BROKEN       → tool is quarantined; a procurement request may be needed.
        itemInstanceMapper.updateToolCondition(instance.getId(), returnedCondition);

        // Return a fresh read so callers see the persisted condition
        return itemInstanceMapper.findById(instance.getId());
    }
}
