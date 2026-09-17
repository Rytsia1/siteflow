package com.siteflow.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.siteflow.domain.PurchaseOrder;
import com.siteflow.domain.enums.PurchaseOrderStatus;

@Mapper
public interface PurchaseOrderMapper {

    @Insert("INSERT INTO purchase_orders (mr_id, po_number, supplier_name, order_date, expected_delivery_date, po_status) "
            + "VALUES (#{mrId}, #{poNumber}, #{supplierName}, #{orderDate}, #{expectedDeliveryDate}, #{poStatus})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PurchaseOrder order);

    @Select("SELECT * FROM purchase_orders WHERE id = #{id}")
    PurchaseOrder findById(Long id);

    @Select("SELECT * FROM purchase_orders WHERE po_number = #{poNumber}")
    PurchaseOrder findByPoNumber(String poNumber);

    /** Returns all POs generated from a given material request. */
    @Select("SELECT * FROM purchase_orders WHERE mr_id = #{mrId}")
    List<PurchaseOrder> findByMrId(@Param("mrId") Long mrId);

    /** Transitions the PO between ISSUED → PARTIAL_RECEIVED → FULFILLED. */
    @Update("UPDATE purchase_orders SET po_status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") PurchaseOrderStatus status);

    /** Atomically transitions the PO status guarded by its current expected status. */
    @Update("UPDATE purchase_orders SET po_status = #{newStatus} WHERE id = #{id} AND po_status = #{expectedStatus}")
    int updateStatusGuarded(@Param("id") Long id,
                            @Param("expectedStatus") PurchaseOrderStatus expectedStatus,
                            @Param("newStatus") PurchaseOrderStatus newStatus);
}
