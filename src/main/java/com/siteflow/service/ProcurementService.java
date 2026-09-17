package com.siteflow.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siteflow.domain.MaterialRequest;
import com.siteflow.domain.MaterialRequestItem;
import com.siteflow.domain.PurchaseOrder;
import com.siteflow.domain.enums.MaterialRequestStatus;
import com.siteflow.domain.enums.PurchaseOrderStatus;
import com.siteflow.mapper.MaterialRequestItemMapper;
import com.siteflow.mapper.MaterialRequestMapper;
import com.siteflow.mapper.PurchaseOrderMapper;
import com.siteflow.web.ResourceNotFoundException;
import com.siteflow.web.dto.MaterialRequestView;

/**
 * Manages the full procurement pipeline: Material Request → Purchase Order.
 *
 * MR lifecycle (managed by this service):
 *   DRAFT → SUBMITTED      (submitMaterialRequest)
 *   SUBMITTED → APPROVED   (approveMaterialRequest — admin decision)
 *   SUBMITTED → REJECTED   (rejectMaterialRequest — admin decision, terminal)
 *   APPROVED → PO_CREATED  (generatePurchaseOrder)
 *   PO_CREATED → COMPLETED (markMaterialRequestCompleted — when goods are received)
 *
 * PO lifecycle: PurchaseOrderStatus defines ISSUED, PARTIAL_RECEIVED and FULFILLED, and
 * every PO is created ISSUED here, but no receiving workflow exists yet to advance a PO
 * past ISSUED — there is no per-line received-quantity data anywhere in the schema for
 * it to act on. Do not assume PARTIAL_RECEIVED/FULFILLED are reachable; they currently
 * are not.
 */
@Service
public class ProcurementService {

    private static final DateTimeFormatter PO_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final MaterialRequestMapper materialRequestMapper;
    private final MaterialRequestItemMapper materialRequestItemMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;

    public ProcurementService(MaterialRequestMapper materialRequestMapper,
                              MaterialRequestItemMapper materialRequestItemMapper,
                              PurchaseOrderMapper purchaseOrderMapper) {
        this.materialRequestMapper = materialRequestMapper;
        this.materialRequestItemMapper = materialRequestItemMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
    }

    /** Represents a single line in a material request: which item and how many. */
    public record MrLineItem(Long itemId, int requestedQty) {}

    // =========================================================================
    // Material Request operations
    // =========================================================================

    /**
     * Creates and immediately submits a new material request.
     *
     * <p>Transition: (new) → SUBMITTED
     *
     * <p>The request is created directly in SUBMITTED state rather than DRAFT
     * because the caller (a field worker or warehouse staff) is explicitly
     * requesting approval. A DRAFT state would require a separate submit call
     * and is reserved for future use by a UI "save for later" feature.
     *
     * <p>All line items are inserted inside the same transaction so a failure
     * on any line rolls back the entire request, leaving no orphan MR header.
     *
     * @param userId        the user raising the procurement need
     * @param justification free-text business reason for the request
     * @param lines         one or more items being requested (must not be empty)
     * @return the persisted MaterialRequest with a generated id
     */
    @Transactional
    public MaterialRequest submitMaterialRequest(Long userId, String justification,
                                                 List<MrLineItem> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("A material request must contain at least one item");
        }

        // Build and persist the MR header; status starts at SUBMITTED
        MaterialRequest mr = MaterialRequest.builder()
                .requestedBy(userId)
                .requestDate(LocalDateTime.now())
                .status(MaterialRequestStatus.SUBMITTED)   // bypasses DRAFT for direct submission
                .justification(justification)
                .build();
        materialRequestMapper.insert(mr);

        // Persist each requested line item, cascading under the new MR id
        for (MrLineItem line : lines) {
            if (line.requestedQty() <= 0) {
                throw new IllegalArgumentException(
                        "Requested quantity must be positive for item " + line.itemId());
            }
            MaterialRequestItem item = MaterialRequestItem.builder()
                    .mrId(mr.getId())
                    .itemId(line.itemId())
                    .requestedQty(line.requestedQty())
                    .build();
            materialRequestItemMapper.insert(item);
        }

        return mr;
    }

    /**
     * Approves an existing material request, making it eligible for PO generation.
     *
     * <p>Transition: SUBMITTED → APPROVED
     *
     * <p>Only SUBMITTED requests can be approved; a DRAFT was never submitted for
     * review and should not bypass the queue, and an already APPROVED/REJECTED/
     * PO_CREATED/COMPLETED request cannot be approved again.
     *
     * @param mrId    the id of the material request to approve
     * @param adminId the user id of the approving admin, recorded for audit
     * @param note    optional approval note
     * @return the updated MaterialRequest
     */
    @Transactional
    public MaterialRequest approveMaterialRequest(Long mrId, Long adminId, String note) {
        return transitionApproval(mrId, adminId, note, MaterialRequestStatus.APPROVED);
    }

    /**
     * Rejects an existing material request. Terminal: a rejected request cannot be
     * approved later or otherwise re-enter the pipeline — the requester submits a new
     * material request if the need still stands (submitMaterialRequest always inserts
     * a fresh row, so nothing more is needed to support that).
     *
     * <p>Transition: SUBMITTED → REJECTED
     *
     * @param mrId    the id of the material request to reject
     * @param adminId the user id of the rejecting admin, recorded for audit
     * @param note    reason for rejection — required so the requester can act on it
     * @return the updated MaterialRequest
     */
    @Transactional
    public MaterialRequest rejectMaterialRequest(Long mrId, Long adminId, String note) {
        return transitionApproval(mrId, adminId, note, MaterialRequestStatus.REJECTED);
    }

    /**
     * Lists material requests in the given status, with requester name
     * resolved, for the procurement dashboard.
     */
    @Transactional(readOnly = true)
    public List<MaterialRequestView> listMaterialRequestsByStatus(MaterialRequestStatus status) {
        return materialRequestMapper.findByStatusWithDetails(status);
    }

    // =========================================================================
    // Purchase Order generation
    // =========================================================================

    /**
     * Generates a Purchase Order document from an approved Material Request.
     *
     * <p>Transitions:
     *   MR:  APPROVED → PO_CREATED   (the MR is now linked to a concrete order)
     *   PO:  (new)    → ISSUED       (the PO starts as a newly-issued order)
     *
     * <p>The MR status is claimed (APPROVED → PO_CREATED) atomically <em>before</em> the PO
     * is built, so a request that loses a concurrency race to generate a PO for the same MR
     * fails before creating anything; if the later PO insert itself fails (e.g. duplicate
     * po_number collision), the whole transaction rolls back, including the status claim.
     *
     * <p>The PO number is auto-generated as "PO-{mrId}-{timestamp}" to guarantee
     * uniqueness. A more sophisticated numbering scheme (e.g. fiscal-year prefix)
     * can be injected via a separate {@code PoNumberGenerator} strategy later.
     *
     * @param mrId         the APPROVED material request to convert into a PO
     * @param supplierName the name of the vendor who will fulfil the order
     * @param expectedDeliveryDate optional target delivery date (may be null)
     * @return the newly created PurchaseOrder with status ISSUED
     */
    @Transactional
    public PurchaseOrder generatePurchaseOrder(Long mrId, String supplierName,
                                               LocalDateTime expectedDeliveryDate) {
        // Guard: only APPROVED MRs can be converted to a PO
        MaterialRequest mr = requireMrInStatus(mrId, MaterialRequestStatus.APPROVED);
        if (!mr.getStatus().canTransitionTo(MaterialRequestStatus.PO_CREATED)) {
            throw new IllegalStateException(
                    "Cannot transition material request from " + mr.getStatus() + " to PO_CREATED");
        }

        // Claim the transition first: APPROVED → PO_CREATED. Only one concurrent caller
        // can win this atomic compare-and-swap, so at most one PO is ever created per MR.
        updateMrStatusOrThrow(mrId, MaterialRequestStatus.APPROVED, MaterialRequestStatus.PO_CREATED);

        // Generate a unique, human-readable PO number: "PO-{mrId}-{yyyyMMdd-HHmmss}"
        String poNumber = "PO-" + mrId + "-" + LocalDateTime.now().format(PO_DATE_FORMAT);

        // Build the PO starting in ISSUED state — it has been sent to the supplier
        PurchaseOrder po = PurchaseOrder.builder()
                .mrId(mrId)
                .poNumber(poNumber)
                .supplierName(supplierName)
                .orderDate(LocalDateTime.now())
                .expectedDeliveryDate(expectedDeliveryDate)
                .poStatus(PurchaseOrderStatus.ISSUED)   // initial state: order sent to supplier
                .build();
        purchaseOrderMapper.insert(po);

        return po;
    }

    /**
     * Marks a material request as COMPLETED once the goods have been fully received.
     *
     * <p>Transition: PO_CREATED → COMPLETED
     *
     * <p>Typically called after the corresponding PO reaches FULFILLED status,
     * but kept as a separate step so receiving staff can complete the MR
     * independently of PO management (e.g. partial deliveries accepted as final).
     *
     * @param mrId the PO_CREATED material request to close out
     * @return the updated MaterialRequest
     */
    @Transactional
    public MaterialRequest markMaterialRequestCompleted(Long mrId) {
        MaterialRequest mr = requireMrInStatus(mrId, MaterialRequestStatus.PO_CREATED);
        if (!mr.getStatus().canTransitionTo(MaterialRequestStatus.COMPLETED)) {
            throw new IllegalStateException(
                    "Cannot transition material request from " + mr.getStatus() + " to COMPLETED");
        }

        // Transition: PO_CREATED → COMPLETED — procurement cycle is closed
        updateMrStatusOrThrow(mrId, MaterialRequestStatus.PO_CREATED, MaterialRequestStatus.COMPLETED);
        return materialRequestMapper.findById(mrId);
    }

    /**
     * Transitions a Purchase Order through its lifecycle:
     * ISSUED → PARTIAL_RECEIVED → FULFILLED.
     * Rejects invalid transitions, terminal modifications, and concurrent race conditions.
     */
    @Transactional
    public PurchaseOrder updatePurchaseOrderStatus(Long poId, PurchaseOrderStatus newStatus) {
        PurchaseOrder po = purchaseOrderMapper.findById(poId);
        if (po == null) {
            throw new ResourceNotFoundException("Purchase order not found: " + poId);
        }
        if (po.getPoStatus().isTerminal()) {
            throw new IllegalStateException(
                    "Cannot modify purchase order " + poId + " in terminal status: " + po.getPoStatus());
        }
        if (!po.getPoStatus().canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "Cannot transition purchase order from " + po.getPoStatus() + " to " + newStatus);
        }

        int updated = purchaseOrderMapper.updateStatusGuarded(poId, po.getPoStatus(), newStatus);
        if (updated == 0) {
            throw new IllegalStateException(
                    "Purchase order " + poId + " was concurrently modified and is no longer " + po.getPoStatus() + ".");
        }
        return purchaseOrderMapper.findById(poId);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Atomically writes the MR's new status, guarded by the expected current status,
     * and throws if the write matched zero rows (the MR was concurrently moved out of
     * that status by another request between this method's pre-check and this write).
     */
    private void updateMrStatusOrThrow(Long mrId, MaterialRequestStatus expected, MaterialRequestStatus newStatus) {
        assertRowsUpdated(materialRequestMapper.updateStatus(mrId, expected, newStatus), mrId, expected);
    }

    /**
     * Shared SUBMITTED → {approved|rejected} transition used by both approveMaterialRequest
     * and rejectMaterialRequest, since the only difference between the two is which target
     * status is written.
     *
     * <p>The pre-check gives a precise "must be SUBMITTED" message for the common case; the
     * atomic conditional UPDATE (matched by affected-row count) is what actually prevents two
     * concurrent decisions on the same request from both succeeding, in case the request
     * changed between the check and the write.
     */
    private MaterialRequest transitionApproval(Long mrId, Long adminId, String note, MaterialRequestStatus newStatus) {
        MaterialRequest mr = requireMrInStatus(mrId, MaterialRequestStatus.SUBMITTED);
        if (!mr.getStatus().canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "Cannot transition material request from " + mr.getStatus() + " to " + newStatus);
        }

        int updated = materialRequestMapper.updateApproval(
                mrId, MaterialRequestStatus.SUBMITTED, newStatus, adminId, note);
        assertRowsUpdated(updated, mrId, MaterialRequestStatus.SUBMITTED);

        return materialRequestMapper.findById(mrId);
    }

    /**
     * Shared by every atomic conditional UPDATE above: throws if the write matched zero rows,
     * meaning the MR was concurrently moved out of {@code expected} between the caller's
     * pre-check and this write.
     */
    private void assertRowsUpdated(int updatedRows, Long mrId, MaterialRequestStatus expected) {
        if (updatedRows == 0) {
            throw new IllegalStateException(
                    "Material request " + mrId + " was concurrently modified and is no longer " + expected + ".");
        }
    }

    /**
     * Loads the MR and asserts it is in the expected status.
     * Shared by all transition methods to enforce the state machine.
     */
    private MaterialRequest requireMrInStatus(Long mrId, MaterialRequestStatus expected) {
        MaterialRequest mr = materialRequestMapper.findById(mrId);
        if (mr == null) {
            throw new ResourceNotFoundException("Material request not found: " + mrId);
        }
        if (mr.getStatus() != expected) {
            throw new IllegalStateException(
                    "Material request " + mrId + " must be " + expected
                    + " for this operation (current status: " + mr.getStatus() + ")");
        }
        return mr;
    }
}
