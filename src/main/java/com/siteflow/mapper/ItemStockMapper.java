package com.siteflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.siteflow.domain.ItemStock;

@Mapper
public interface ItemStockMapper {

    @Select("SELECT * FROM item_stocks WHERE item_id = #{itemId} AND location_id = #{locationId}")
    ItemStock findByItemIdAndLocationId(@Param("itemId") Long itemId, @Param("locationId") Long locationId);

    @Update("UPDATE item_stocks SET current_qty = current_qty + #{qtyDelta} WHERE id = #{id}")
    int adjustQty(@Param("id") Long id, @Param("qtyDelta") int qtyDelta);
}
