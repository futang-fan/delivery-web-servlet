package com.deliveryweb.dao;

import com.deliveryweb.pojo.Address;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 收货地址数据访问接口（MyBatis Mapper，注解实现）。
 * 归属保护统一在 WHERE 中以 user_id 条件实现（越权防护）。
 */
public interface AddressDao {

    String BASE_COLUMNS = "id, user_id, receiver, phone, detail, is_default, created_at";

    @Select("SELECT " + BASE_COLUMNS + " FROM address WHERE user_id = #{userId} "
            + "ORDER BY is_default DESC, id DESC")
    List<Address> listByUser(@Param("userId") Long userId);

    @Select("SELECT " + BASE_COLUMNS + " FROM address WHERE id = #{id} AND user_id = #{userId}")
    Address findOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Insert("INSERT INTO address (user_id, receiver, phone, detail, is_default, created_at) "
            + "VALUES (#{userId}, #{receiver}, #{phone}, #{detail}, #{isDefault}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Address address);

    @Update("UPDATE address SET receiver = #{receiver}, phone = #{phone}, detail = #{detail}, "
            + "is_default = #{isDefault} WHERE id = #{id} AND user_id = #{userId}")
    int update(Address address);

    @Delete("DELETE FROM address WHERE id = #{id} AND user_id = #{userId}")
    int delete(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE address SET is_default = 0 WHERE user_id = #{userId}")
    int clearDefault(@Param("userId") Long userId);

    @Update("UPDATE address SET is_default = 1 WHERE id = #{id} AND user_id = #{userId}")
    int setDefault(@Param("id") Long id, @Param("userId") Long userId);
}
