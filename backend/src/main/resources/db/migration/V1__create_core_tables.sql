CREATE TABLE t_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  phone VARCHAR(32) NOT NULL COMMENT '登录手机号',
  nickname VARCHAR(64) NULL COMMENT '用户昵称',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希值',
  email VARCHAR(255) NULL COMMENT '找回密码邮箱',
  email_verified_at DATETIME NULL COMMENT '邮箱验证时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_t_user_phone (phone),
  UNIQUE KEY uk_t_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账户表';

CREATE TABLE t_category (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  user_id BIGINT NULL COMMENT '所属用户ID，NULL表示系统预置分类',
  name VARCHAR(64) NOT NULL COMMENT '分类名称',
  type ENUM('INCOME', 'EXPENSE') NOT NULL COMMENT '分类类型：收入或支出',
  icon VARCHAR(32) NOT NULL DEFAULT 'square.grid.2x2' COMMENT '客户端图标标识',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序值',
  active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  CONSTRAINT fk_t_category_user FOREIGN KEY (user_id) REFERENCES t_user(id),
  UNIQUE KEY uk_t_category_user_name (user_id, name, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收支分类表';

CREATE TABLE t_transaction (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  user_id BIGINT NOT NULL COMMENT '所属用户ID',
  category_id BIGINT NOT NULL COMMENT '分类ID',
  type ENUM('INCOME', 'EXPENSE') NOT NULL COMMENT '流水类型：收入或支出',
  amount DECIMAL(19,2) NOT NULL COMMENT '金额，单位元',
  occurred_on DATE NOT NULL COMMENT '记账日期',
  note VARCHAR(280) NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  CONSTRAINT fk_t_transaction_user FOREIGN KEY (user_id) REFERENCES t_user(id),
  CONSTRAINT fk_t_transaction_category FOREIGN KEY (category_id) REFERENCES t_category(id),
  INDEX idx_t_transaction_user_date (user_id, occurred_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收支流水表';

CREATE TABLE t_budget (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '预算ID',
  user_id BIGINT NOT NULL COMMENT '所属用户ID',
  month_start DATE NOT NULL COMMENT '预算月份首日',
  category_id BIGINT NULL COMMENT '分类ID，NULL表示月度总预算',
  amount DECIMAL(19,2) NOT NULL COMMENT '预算金额，单位元',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  CONSTRAINT fk_t_budget_user FOREIGN KEY (user_id) REFERENCES t_user(id),
  CONSTRAINT fk_t_budget_category FOREIGN KEY (category_id) REFERENCES t_category(id),
  UNIQUE KEY uk_t_budget_month_category (user_id, month_start, (IFNULL(category_id, 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='月度及分类预算表';

CREATE TABLE t_refresh_token (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '刷新令牌ID',
  user_id BIGINT NOT NULL COMMENT '所属用户ID',
  token_hash CHAR(64) NOT NULL COMMENT '刷新令牌SHA-256哈希',
  expires_at DATETIME NOT NULL COMMENT '过期时间',
  revoked_at DATETIME NULL COMMENT '撤销时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  CONSTRAINT fk_t_refresh_token_user FOREIGN KEY (user_id) REFERENCES t_user(id),
  UNIQUE KEY uk_t_refresh_token_hash (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录刷新令牌表';

CREATE TABLE t_one_time_token (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '一次性令牌ID',
  user_id BIGINT NOT NULL COMMENT '所属用户ID',
  purpose ENUM('VERIFY_EMAIL', 'RESET_PASSWORD') NOT NULL COMMENT '用途：验证邮箱或重置密码',
  token_hash CHAR(64) NOT NULL COMMENT '令牌SHA-256哈希',
  expires_at DATETIME NOT NULL COMMENT '过期时间',
  used_at DATETIME NULL COMMENT '使用时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  CONSTRAINT fk_t_one_time_token_user FOREIGN KEY (user_id) REFERENCES t_user(id),
  UNIQUE KEY uk_t_one_time_token_hash (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱验证及密码重置令牌表';
