package com.siteflow.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.siteflow.domain.BorrowItem;

@Mapper
public interface BorrowItemMapper {

    @Insert("INSERT INTO borrow_items (borrow_request_id, item_id, qty_borrowed, qty_returned, return_date) "
            + "VALUES (#{borrowRequestId}, #{itemId}, #{qtyBorrowed}, #{qtyReturned}, #{returnDate})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BorrowItem item);

    @Select("SELECT * FROM borrow_items WHERE id = #{id}")
    BorrowItem findById(Long id);

    @Select("SELECT * FROM borrow_items WHERE borrow_request_id = #{borrowRequestId}")
    List<BorrowItem> findByBorrowRequestId(Long borrowRequestId);

    @Update("UPDATE borrow_items SET qty_returned = qty_returned + #{qtyReturned}, return_date = #{returnDate} "
            + "WHERE id = #{id}")
    int recordReturn(@Param("id") Long id, @Param("qtyReturned") int qtyReturned,
            @Param("returnDate") LocalDateTime returnDate);
}
