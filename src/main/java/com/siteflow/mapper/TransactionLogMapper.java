package com.siteflow.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

import com.siteflow.domain.TransactionLog;

@Mapper
public interface TransactionLogMapper {

    @Insert("INSERT INTO transaction_logs (item_id, location_id, user_id, transaction_type, qty_change, "
            + "reference_id, timestamp) "
            + "VALUES (#{itemId}, #{locationId}, #{userId}, #{transactionType}, #{qtyChange}, "
            + "#{referenceId}, #{timestamp})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TransactionLog log);
}
