package com.pixledger.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.*;

@Mapper
public interface LedgerMapper {
    @Select("SELECT id, phone, password_hash AS passwordHash, email, email_verified_at AS emailVerifiedAt FROM users WHERE phone=#{phone}")
    Map<String, Object> userByPhone(String phone);

    @Select("SELECT id, phone, password_hash AS passwordHash, email, email_verified_at AS emailVerifiedAt FROM users WHERE email=#{email}")
    Map<String, Object> userByEmail(String email);

    @Select("SELECT id, phone, password_hash AS passwordHash, email, email_verified_at AS emailVerifiedAt FROM users WHERE id=#{id}")
    Map<String, Object> userById(long id);

    @Insert("INSERT INTO users(phone,password_hash) VALUES(#{phone},#{hash})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void createUser(Map<String, Object> user);

    @Update("UPDATE users SET email=#{email}, email_verified_at=NULL WHERE id=#{userId}")
    void setEmail(long userId, String email);

    @Update("UPDATE users SET email_verified_at=NOW() WHERE id=#{userId}")
    void verifyEmail(long userId);

    @Update("UPDATE users SET password_hash=#{hash} WHERE id=#{userId}")
    void setPassword(long userId, String hash);

    @Insert("INSERT INTO refresh_tokens(user_id,token_hash,expires_at) VALUES(#{userId},#{hash},DATE_ADD(NOW(), INTERVAL 30 DAY))")
    void createRefreshToken(long userId, String hash);

    @Select("SELECT id,user_id AS userId FROM refresh_tokens WHERE token_hash=#{hash} AND revoked_at IS NULL AND expires_at>NOW()")
    Map<String, Object> validRefreshToken(String hash);

    @Update("UPDATE refresh_tokens SET revoked_at=NOW() WHERE id=#{id} AND revoked_at IS NULL")
    int revokeRefreshToken(long id);

    @Insert("INSERT INTO one_time_tokens(user_id,purpose,token_hash,expires_at) VALUES(#{userId},#{purpose},#{hash},DATE_ADD(NOW(), INTERVAL 30 MINUTE))")
    void createOneTimeToken(long userId, String purpose, String hash);

    @Select("SELECT id,user_id AS userId FROM one_time_tokens WHERE token_hash=#{hash} AND purpose=#{purpose} AND used_at IS NULL AND expires_at>NOW()")
    Map<String, Object> validOneTimeToken(String hash, String purpose);

    @Update("UPDATE one_time_tokens SET used_at=NOW() WHERE id=#{id} AND used_at IS NULL")
    int consumeOneTimeToken(long id);

    @Select("SELECT id,name,type,icon,sort_order AS sortOrder,active FROM categories WHERE (user_id IS NULL OR user_id=#{userId}) AND active=TRUE ORDER BY type, sort_order, id")
    List<Map<String, Object>> categories(long userId);

    @Insert("INSERT INTO categories(user_id,name,type,icon,sort_order) VALUES(#{userId},#{name},#{type},#{icon},#{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void createCategory(Map<String, Object> category);

    @Update("UPDATE categories SET name=#{name}, icon=#{icon}, sort_order=#{sortOrder}, active=#{active} WHERE id=#{id} AND user_id=#{userId}")
    int updateCategory(Map<String, Object> category);

    @Select("SELECT id,type FROM categories WHERE id=#{categoryId} AND (user_id IS NULL OR user_id=#{userId}) AND active=TRUE")
    Map<String, Object> ownedCategory(long userId, long categoryId);

    @Select("SELECT t.id,t.type,t.amount,t.occurred_on AS occurredOn,t.note,t.category_id AS categoryId,c.name AS categoryName,c.icon AS categoryIcon FROM transactions t JOIN categories c ON c.id=t.category_id WHERE t.user_id=#{userId} AND t.occurred_on BETWEEN #{from} AND #{to} ORDER BY t.occurred_on DESC,t.id DESC")
    List<Map<String, Object>> transactions(long userId, LocalDate from, LocalDate to);

    @Insert("INSERT INTO transactions(user_id,category_id,type,amount,occurred_on,note) VALUES(#{userId},#{categoryId},#{type},#{amount},#{occurredOn},#{note,jdbcType=VARCHAR})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void createTransaction(Map<String, Object> tx);

    @Update("UPDATE transactions SET category_id=#{categoryId},type=#{type},amount=#{amount},occurred_on=#{occurredOn},note=#{note,jdbcType=VARCHAR} WHERE id=#{id} AND user_id=#{userId}")
    int updateTransaction(Map<String, Object> tx);

    @Delete("DELETE FROM transactions WHERE id=#{id} AND user_id=#{userId}")
    int deleteTransaction(long userId, long id);

    @Select("SELECT COALESCE(SUM(CASE WHEN type='INCOME' THEN amount ELSE 0 END),0) AS income, COALESCE(SUM(CASE WHEN type='EXPENSE' THEN amount ELSE 0 END),0) AS expense FROM transactions WHERE user_id=#{userId} AND occurred_on BETWEEN #{from} AND #{to}")
    Map<String, BigDecimal> summary(long userId, LocalDate from, LocalDate to);

    @Select("SELECT c.name, c.icon, SUM(t.amount) AS amount FROM transactions t JOIN categories c ON c.id=t.category_id WHERE t.user_id=#{userId} AND t.type='EXPENSE' AND t.occurred_on BETWEEN #{from} AND #{to} GROUP BY c.id,c.name,c.icon ORDER BY amount DESC")
    List<Map<String, Object>> expenseBreakdown(long userId, LocalDate from, LocalDate to);

    @Select("SELECT id,category_id AS categoryId,amount FROM budgets WHERE user_id=#{userId} AND month_start=#{monthStart} ORDER BY category_id")
    List<Map<String, Object>> budgets(long userId, LocalDate monthStart);

    @Insert("INSERT INTO budgets(user_id,month_start,category_id,amount) VALUES(#{userId},#{monthStart},#{categoryId,jdbcType=BIGINT},#{amount}) ON DUPLICATE KEY UPDATE amount=VALUES(amount),updated_at=NOW()")
    void upsertBudget(Map<String, Object> budget);
}
