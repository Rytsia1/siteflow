package com.siteflow.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.siteflow.domain.Item;

@Mapper
public interface ItemMapper {

    @Select("SELECT * FROM items ORDER BY id")
    List<Item> findAll();

    @Select("SELECT * FROM items WHERE id = #{id}")
    Item findById(Long id);
}
