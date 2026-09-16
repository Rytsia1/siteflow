package com.siteflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.ItemInstance;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.ToolCondition;
import com.siteflow.mapper.BorrowRequestMapper;
import com.siteflow.mapper.ItemInstanceMapper;
import com.siteflow.web.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class AssetTrackingServiceTest {

    @Mock
    private ItemInstanceMapper itemInstanceMapper;
    @Mock
    private BorrowRequestMapper borrowRequestMapper;

    private AssetTrackingService assetTrackingService;

    @BeforeEach
    void setUp() {
        assetTrackingService = new AssetTrackingService(itemInstanceMapper, borrowRequestMapper);
    }

    @Test
    @DisplayName("checkoutItemInstance succeeds for a GOOD tool against an APPROVED borrow request")
    void checkout_goodToolApprovedRequest_succeeds() {
        ItemInstance instance = ItemInstance.builder().id(1L).serialNumber("SN-1").toolCondition(ToolCondition.GOOD)
                .build();
        BorrowRequest approved = BorrowRequest.builder().id(5L).approvalStatus(ApprovalStatus.APPROVED).build();
        when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(instance);
        when(borrowRequestMapper.findById(5L)).thenReturn(approved);

        ItemInstance result = assetTrackingService.checkoutItemInstance("SN-1", 5L);

        assertThat(result.getToolCondition()).isEqualTo(ToolCondition.GOOD);
    }

    @Test
    @DisplayName("checkoutItemInstance rejects a damaged tool — it cannot become immediately available")
    void checkout_brokenTool_throwsWithoutCheckingBorrowRequest() {
        ItemInstance instance = ItemInstance.builder().id(1L).serialNumber("SN-1").toolCondition(ToolCondition.BROKEN)
                .build();
        when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(instance);

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance("SN-1", 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BROKEN");

        // Condition is checked before the borrow request is even looked up.
        verify(borrowRequestMapper, never()).findById(any());
    }

    @Test
    @DisplayName("checkoutItemInstance rejects checkout against a borrow request that isn't approved yet")
    void checkout_borrowRequestNotApproved_throws() {
        ItemInstance instance = ItemInstance.builder().id(1L).serialNumber("SN-1").toolCondition(ToolCondition.GOOD)
                .build();
        BorrowRequest pending = BorrowRequest.builder().id(5L).approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .build();
        when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(instance);
        when(borrowRequestMapper.findById(5L)).thenReturn(pending);

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance("SN-1", 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not approved");
    }

    @Test
    @DisplayName("checkoutItemInstance falls back to QR code lookup when serial number doesn't match")
    void checkout_unknownSerialNumber_fallsBackToQrCode() {
        ItemInstance instance = ItemInstance.builder().id(1L).qrCodeValue("QR-1").toolCondition(ToolCondition.GOOD)
                .build();
        BorrowRequest approved = BorrowRequest.builder().id(5L).approvalStatus(ApprovalStatus.APPROVED).build();
        when(itemInstanceMapper.findBySerialNumber("QR-1")).thenReturn(null);
        when(itemInstanceMapper.findByQrCodeValue("QR-1")).thenReturn(instance);
        when(borrowRequestMapper.findById(5L)).thenReturn(approved);

        ItemInstance result = assetTrackingService.checkoutItemInstance("QR-1", 5L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("checkoutItemInstance fails with 404-mapped exception when no tool matches either identifier")
    void checkout_unknownIdentifier_throwsResourceNotFound() {
        when(itemInstanceMapper.findBySerialNumber("MISSING")).thenReturn(null);
        when(itemInstanceMapper.findByQrCodeValue("MISSING")).thenReturn(null);

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance("MISSING", 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("checkoutItemInstance fails with 404-mapped exception when the borrow request id doesn't exist")
    void checkout_unknownBorrowRequest_throwsResourceNotFound() {
        ItemInstance instance = ItemInstance.builder().id(1L).serialNumber("SN-1").toolCondition(ToolCondition.GOOD)
                .build();
        when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(instance);
        when(borrowRequestMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance("SN-1", 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("returnItemInstance records whatever condition inspection finds, including a downgrade to BROKEN")
    void returnItemInstance_recordsInspectedCondition() {
        ItemInstance beforeReturn = ItemInstance.builder().id(1L).serialNumber("SN-1").toolCondition(ToolCondition.GOOD)
                .build();
        ItemInstance afterReturn = ItemInstance.builder().id(1L).serialNumber("SN-1")
                .toolCondition(ToolCondition.BROKEN).build();
        when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(beforeReturn);
        when(itemInstanceMapper.findById(1L)).thenReturn(afterReturn);

        ItemInstance result = assetTrackingService.returnItemInstance("SN-1", ToolCondition.BROKEN);

        verify(itemInstanceMapper).updateToolCondition(1L, ToolCondition.BROKEN);
        assertThat(result.getToolCondition()).isEqualTo(ToolCondition.BROKEN);
    }
}
