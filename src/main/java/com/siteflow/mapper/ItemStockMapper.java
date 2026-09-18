package com.siteflow.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.siteflow.domain.ItemStock;
import com.siteflow.web.dto.ItemStockView;

@Mapper
public interface ItemStockMapper {

    @Select("SELECT * FROM item_stocks WHERE item_id = #{itemId} AND location_id = #{locationId}")
    ItemStock findByItemIdAndLocationId(@Param("itemId") Long itemId, @Param("locationId") Long locationId);

    @Select("SELECT * FROM item_stocks WHERE item_id = #{itemId} AND location_id = #{locationId} FOR UPDATE")
    ItemStock findByItemIdAndLocationIdForUpdate(@Param("itemId") Long itemId, @Param("locationId") Long locationId);

    @Select("SELECT s.location_id AS location_id, l.location_name AS location_name, s.current_qty AS current_qty "
            + "FROM item_stocks s JOIN locations l ON l.id = s.location_id "
            + "WHERE s.item_id = #{itemId} ORDER BY l.location_name")
    @ConstructorArgs({
            @Arg(column = "location_id", javaType = Long.class),
            @Arg(column = "location_name", javaType = String.class),
            @Arg(column = "current_qty", javaType = Integer.class)
    })
    List<ItemStockView> findByItemId(Long itemId);

    @Update("UPDATE item_stocks SET current_qty = current_qty + #{qtyDelta} "
            + "WHERE id = #{id} AND current_qty + #{qtyDelta} >= 0")
    int adjustQty(@Param("id") Long id, @Param("qtyDelta") int qtyDelta);

    /**
     * Atomically reserves stock for a pending borrow request:
     * Decreases available stock (current_qty) and increases reserved_qty in one CAS update.
     * Guaranteed to match zero rows if available stock is insufficient.
     */
    @Update("UPDATE item_stocks "
            + "SET current_qty = current_qty - #{qty}, reserved_qty = reserved_qty + #{qty} "
            + "WHERE id = #{id} AND current_qty >= #{qty}")
    int reserveStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Atomically releases reserved stock on rejection or cancellation:
     * Decreases reserved_qty and restores available stock (current_qty).
     * Guards against releasing more than what is currently reserved.
     */
    @Update("UPDATE item_stocks "
            + "SET current_qty = current_qty + #{qty}, reserved_qty = reserved_qty - #{qty} "
            + "WHERE id = #{id} AND reserved_qty >= #{qty}")
    int releaseReservation(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Atomically fulfills reservation on physical checkout / tool dispensing:
     * Decreases reserved_qty as the physical tool leaves the warehouse.
     */
    @Update("UPDATE item_stocks "
            + "SET reserved_qty = reserved_qty - #{qty} "
            + "WHERE id = #{id} AND reserved_qty >= #{qty}")
    int fulfillReservation(@Param("id") Long id, @Param("qty") int qty);
}
