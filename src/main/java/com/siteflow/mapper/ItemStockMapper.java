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
}
