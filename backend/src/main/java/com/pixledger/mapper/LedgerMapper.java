package com.pixledger.mapper;

import com.pixledger.domain.*;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface LedgerMapper {
    @Select("SELECT id, phone, nickname, password_hash AS passwordHash, email, email_verified_at AS emailVerifiedAt FROM t_user WHERE phone=#{phone}")
    UserDO userByPhone(String phone);

    @Select("SELECT id, phone, nickname, password_hash AS passwordHash, email, email_verified_at AS emailVerifiedAt FROM t_user WHERE email=#{email}")
    UserDO userByEmail(String email);

    @Select("SELECT id, phone, nickname, password_hash AS passwordHash, email, email_verified_at AS emailVerifiedAt FROM t_user WHERE id=#{id}")
    UserDO userById(long id);

    @Update("UPDATE t_user SET email=#{email}, email_verified_at=NULL WHERE id=#{userId}")
    void setEmail(long userId, String email);

    @Update("UPDATE t_user SET email_verified_at=NOW() WHERE id=#{userId}")
    void verifyEmail(long userId);

    @Update("UPDATE t_user SET password_hash=#{hash} WHERE id=#{userId}")
    void setPassword(long userId, String hash);

    @Insert("INSERT INTO t_refresh_token(user_id,token_hash,expires_at) VALUES(#{userId},#{hash},DATE_ADD(NOW(), INTERVAL 30 DAY))")
    void createRefreshToken(long userId, String hash);

    @Select("SELECT id, user_id AS userId FROM t_refresh_token WHERE token_hash=#{hash} AND revoked_at IS NULL AND expires_at>NOW()")
    TokenDO validRefreshToken(String hash);

    @Update("UPDATE t_refresh_token SET revoked_at=NOW() WHERE id=#{id} AND revoked_at IS NULL")
    int revokeRefreshToken(long id);

    @Insert("INSERT INTO t_one_time_token(user_id,purpose,token_hash,expires_at) VALUES(#{userId},#{purpose},#{hash},DATE_ADD(NOW(), INTERVAL 30 MINUTE))")
    void createOneTimeToken(long userId, String purpose, String hash);

    @Select("SELECT id, user_id AS userId FROM t_one_time_token WHERE token_hash=#{hash} AND purpose=#{purpose} AND used_at IS NULL AND expires_at>NOW()")
    TokenDO validOneTimeToken(String hash, String purpose);

    @Update("UPDATE t_one_time_token SET used_at=NOW() WHERE id=#{id} AND used_at IS NULL")
    int consumeOneTimeToken(long id);

    @Select("SELECT id, user_id AS userId, name, type, icon, sort_order AS sortOrder, active FROM t_category WHERE (user_id IS NULL OR user_id=#{userId}) AND active=TRUE ORDER BY type, sort_order, id")
    List<CategoryDO> categories(long userId);

    @Insert("INSERT INTO t_category(user_id,name,type,icon,sort_order) VALUES(#{userId},#{name},#{type},#{icon},#{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void createCategory(CategoryDO category);

    @Update("UPDATE t_category SET name=#{name}, icon=#{icon}, sort_order=#{sortOrder}, active=#{active} WHERE id=#{id} AND user_id=#{userId}")
    int updateCategory(CategoryDO category);

    @Select("SELECT id, type, name, icon FROM t_category WHERE id=#{categoryId} AND (user_id IS NULL OR user_id=#{userId}) AND active=TRUE")
    CategoryDO ownedCategory(long userId, long categoryId);

    @Select("SELECT t.id, t.user_id AS userId, t.type, t.amount, t.occurred_on AS occurredOn, t.note, t.category_id AS categoryId, c.name AS categoryName, c.icon AS categoryIcon FROM t_transaction t JOIN t_category c ON c.id=t.category_id WHERE t.user_id=#{userId} AND t.occurred_on BETWEEN #{from} AND #{to} ORDER BY t.occurred_on DESC,t.id DESC")
    List<TransactionDO> transactions(long userId, LocalDate from, LocalDate to);

    @Insert("INSERT INTO t_transaction(user_id,category_id,type,amount,occurred_on,note) VALUES(#{userId},#{categoryId},#{type},#{amount},#{occurredOn},#{note,jdbcType=VARCHAR})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void createTransaction(TransactionDO transaction);

    @Update("UPDATE t_transaction SET category_id=#{categoryId},type=#{type},amount=#{amount},occurred_on=#{occurredOn},note=#{note,jdbcType=VARCHAR} WHERE id=#{id} AND user_id=#{userId}")
    int updateTransaction(TransactionDO transaction);

    @Delete("DELETE FROM t_transaction WHERE id=#{id} AND user_id=#{userId}")
    int deleteTransaction(long userId, long id);

    @Select("SELECT COALESCE(SUM(CASE WHEN type='INCOME' THEN amount ELSE 0 END),0) AS income, COALESCE(SUM(CASE WHEN type='EXPENSE' THEN amount ELSE 0 END),0) AS expense FROM t_transaction WHERE user_id=#{userId} AND occurred_on BETWEEN #{from} AND #{to}")
    SummaryDO summary(long userId, LocalDate from, LocalDate to);

    @Select("SELECT c.name, c.icon, SUM(t.amount) AS amount FROM t_transaction t JOIN t_category c ON c.id=t.category_id WHERE t.user_id=#{userId} AND t.type='EXPENSE' AND t.occurred_on BETWEEN #{from} AND #{to} GROUP BY c.id,c.name,c.icon ORDER BY amount DESC")
    List<ExpenseBreakdownDO> expenseBreakdown(long userId, LocalDate from, LocalDate to);

    @Select("SELECT id, user_id AS userId, category_id AS categoryId, month_start AS monthStart, amount FROM t_budget WHERE user_id=#{userId} AND month_start=#{monthStart} ORDER BY category_id")
    List<BudgetDO> budgets(long userId, LocalDate monthStart);

    @Insert("INSERT INTO t_budget(user_id,month_start,category_id,amount) VALUES(#{userId},#{monthStart},#{categoryId,jdbcType=BIGINT},#{amount}) ON DUPLICATE KEY UPDATE amount=VALUES(amount),updated_at=NOW()")
    void upsertBudget(BudgetDO budget);
}
