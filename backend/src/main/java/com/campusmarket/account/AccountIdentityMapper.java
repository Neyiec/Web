package com.campusmarket.account;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AccountIdentityMapper {
    @Select("SELECT id, password_hash FROM users WHERE phone = #{phone} AND status = 'ACTIVE'")
    Credentials credentials(String phone);

    @Select("SELECT nickname FROM users WHERE id = #{id} AND status = 'ACTIVE'")
    String activeNickname(long id);

    @Select("""
            SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id = r.id
            WHERE ur.user_id = #{id}
            """)
    List<String> roles(long id);

    record Credentials(long id, String passwordHash) {}
}
