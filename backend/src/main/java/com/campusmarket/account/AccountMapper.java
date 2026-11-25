package com.campusmarket.account;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountMapper {
    @Insert("""
            INSERT INTO users (phone, password_hash, nickname)
            VALUES (#{phone}, #{passwordHash}, #{nickname})
            """)
    void createUser(@Param("phone") String phone, @Param("passwordHash") String passwordHash,
                    @Param("nickname") String nickname);

    @Insert("""
            INSERT INTO user_roles (user_id, role_id)
            SELECT u.id, r.id
            FROM users u CROSS JOIN roles r
            WHERE u.phone = #{phone} AND r.code = 'STUDENT'
            """)
    int grantStudentRole(@Param("phone") String phone);
}
