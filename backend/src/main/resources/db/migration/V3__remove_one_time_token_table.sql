-- 一次性验证和重置令牌已迁移到 Redis，数据库不再保存临时令牌。
DROP TABLE IF EXISTS t_one_time_token;
