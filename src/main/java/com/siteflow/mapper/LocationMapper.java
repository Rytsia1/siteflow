package com.siteflow.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.siteflow.domain.Location;

@Mapper
public interface LocationMapper {

    @Select("SELECT * FROM locations ORDER BY location_name")
    List<Location> findAll();
}
