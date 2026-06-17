CREATE TABLE IF NOT EXISTS admin_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'SUPER_ADMIN',
  avatar_url VARCHAR(255) DEFAULT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  last_login_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_user_username (username),
  KEY idx_admin_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='admin accounts';

CREATE TABLE IF NOT EXISTS store_account (
  id BIGINT NOT NULL AUTO_INCREMENT,
  account VARCHAR(64) NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  shop_name VARCHAR(50) NOT NULL,
  contact_mobile VARCHAR(20) NOT NULL,
  avatar_url VARCHAR(255) DEFAULT NULL,
  address VARCHAR(255) DEFAULT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  last_login_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_store_account_account (account),
  KEY idx_store_account_status (status),
  KEY idx_store_account_mobile (contact_mobile)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='merchant store accounts';

CREATE TABLE IF NOT EXISTS store_member (
  id BIGINT NOT NULL AUTO_INCREMENT,
  store_id BIGINT NOT NULL,
  member_no VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  mobile VARCHAR(20) NOT NULL,
  gender VARCHAR(16) NOT NULL,
  age INT NOT NULL,
  avatar_url VARCHAR(255) DEFAULT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_store_member_no (member_no),
  UNIQUE KEY uk_store_member_mobile_active (store_id, mobile, status),
  KEY idx_store_member_store_name (store_id, name),
  KEY idx_store_member_store_mobile (store_id, mobile),
  CONSTRAINT fk_store_member_store FOREIGN KEY (store_id) REFERENCES store_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='store members';

CREATE TABLE IF NOT EXISTS member_wallet (
  member_id BIGINT NOT NULL,
  recharge_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  gift_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  accumulated_recharge DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  accumulated_gift DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  accumulated_consumption DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  recharge_count INT NOT NULL DEFAULT 0,
  consumption_count INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (member_id),
  CONSTRAINT fk_member_wallet_member FOREIGN KEY (member_id) REFERENCES store_member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='member wallet balances';

CREATE TABLE IF NOT EXISTS recharge_tier (
  id BIGINT NOT NULL AUTO_INCREMENT,
  store_id BIGINT NOT NULL,
  tier_no INT NOT NULL,
  recharge_amount DECIMAL(12,2) NOT NULL,
  gift_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recharge_tier_store_no (store_id, tier_no),
  KEY idx_recharge_tier_store_amount (store_id, recharge_amount),
  CONSTRAINT fk_recharge_tier_store FOREIGN KEY (store_id) REFERENCES store_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='merchant recharge tiers';

CREATE TABLE IF NOT EXISTS wallet_transaction (
  id BIGINT NOT NULL AUTO_INCREMENT,
  serial_no VARCHAR(64) NOT NULL,
  store_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  original_transaction_id BIGINT DEFAULT NULL,
  idempotency_key VARCHAR(128) DEFAULT NULL,
  type VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
  amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  gift_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  before_recharge_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  before_gift_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  before_total_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  after_recharge_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  after_gift_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  after_total_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  payment_method VARCHAR(32) NOT NULL DEFAULT 'CASH',
  item_name VARCHAR(64) DEFAULT NULL,
  remark VARCHAR(255) DEFAULT NULL,
  operator_type VARCHAR(16) NOT NULL,
  operator_id BIGINT NOT NULL,
  reversed_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_wallet_transaction_serial_no (serial_no),
  UNIQUE KEY uk_wallet_transaction_idempotency (idempotency_key),
  KEY idx_wallet_transaction_store_time (store_id, created_at),
  KEY idx_wallet_transaction_member_time (member_id, created_at),
  KEY idx_wallet_transaction_original (original_transaction_id),
  CONSTRAINT fk_wallet_transaction_store FOREIGN KEY (store_id) REFERENCES store_account (id),
  CONSTRAINT fk_wallet_transaction_member FOREIGN KEY (member_id) REFERENCES store_member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='wallet transaction ledger';

CREATE TABLE IF NOT EXISTS uploaded_file (
  id BIGINT NOT NULL AUTO_INCREMENT,
  original_name VARCHAR(255) NOT NULL,
  stored_name VARCHAR(255) NOT NULL,
  url VARCHAR(255) NOT NULL,
  content_type VARCHAR(64) NOT NULL,
  size_bytes BIGINT NOT NULL,
  uploader_type VARCHAR(16) DEFAULT NULL,
  uploader_id BIGINT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_uploaded_file_stored_name (stored_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='uploaded image files';

CREATE TABLE IF NOT EXISTS verification_code (
  id BIGINT NOT NULL AUTO_INCREMENT,
  mobile VARCHAR(20) NOT NULL,
  scene VARCHAR(32) NOT NULL,
  code VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'UNUSED',
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_verification_code_mobile_scene (mobile, scene, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='mock sms verification codes';

CREATE TABLE IF NOT EXISTS login_session (
  token VARCHAR(128) NOT NULL,
  principal_type VARCHAR(16) NOT NULL,
  principal_id BIGINT NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (token),
  KEY idx_login_session_principal (principal_type, principal_id),
  KEY idx_login_session_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='cookie login sessions';

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  operator_type VARCHAR(16) NOT NULL,
  operator_id BIGINT NOT NULL,
  module VARCHAR(64) NOT NULL,
  action VARCHAR(64) NOT NULL,
  target_id BIGINT DEFAULT NULL,
  before_json JSON DEFAULT NULL,
  after_json JSON DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_log_operator (operator_type, operator_id, created_at),
  KEY idx_audit_log_target (module, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='business audit logs';

INSERT INTO admin_user (id, username, password_hash, display_name, role, status)
VALUES (1, 'superadmin', 'abe2d3ed5419e1a2293c034a6b375a622ff5a60e5ac30f29c461220898ffdd97', 'Admin', 'SUPER_ADMIN', 'ACTIVE')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO store_account (id, account, password_hash, shop_name, contact_mobile, address, status)
VALUES
  (1, 'xinyue_store', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '欣悦生活馆', '13800138000', '北京市朝阳区建国路88号', 'ACTIVE'),
  (2, 'east_store', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '悦享生活馆·东城店', '13800138011', '北京市朝阳区建国路88号', 'ACTIVE'),
  (3, 'west_store', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '悦享生活馆·西城店', '13800138012', '北京市西城区金融大街12号', 'ACTIVE'),
  (4, 'closed_store', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '悦享生活馆·朝阳门店', '13800138013', '北京市东城区朝阳门内大街18号', 'DISABLED')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO recharge_tier (store_id, tier_no, recharge_amount, gift_amount)
VALUES
  (1, 1, 100.00, 10.00),
  (1, 2, 200.00, 20.00),
  (1, 3, 300.00, 35.00),
  (1, 4, 500.00, 100.00),
  (2, 1, 100.00, 10.00),
  (2, 2, 200.00, 20.00),
  (2, 3, 300.00, 35.00),
  (2, 4, 500.00, 100.00),
  (3, 1, 100.00, 10.00),
  (3, 2, 200.00, 20.00),
  (3, 3, 300.00, 35.00),
  (3, 4, 500.00, 100.00),
  (4, 1, 100.00, 10.00),
  (4, 2, 200.00, 20.00),
  (4, 3, 300.00, 35.00),
  (4, 4, 500.00, 100.00)
ON DUPLICATE KEY UPDATE recharge_amount = VALUES(recharge_amount), gift_amount = VALUES(gift_amount);

INSERT INTO store_member (id, store_id, member_no, name, mobile, gender, age, status)
VALUES
  (1, 1, 'M202606160001', '赵天宇', '13600136004', 'MALE', 41, 'ACTIVE'),
  (2, 1, 'M202606160002', '李小晴', '13800138001', 'FEMALE', 28, 'ACTIVE'),
  (3, 1, 'M202606160003', '吴佳宁', '13300133007', 'FEMALE', 45, 'ACTIVE'),
  (4, 1, 'M202606160004', '后四位同号A', '13900138001', 'MALE', 33, 'ACTIVE'),
  (5, 1, 'M202606160005', '余额不足会员', '13700137009', 'FEMALE', 22, 'ACTIVE')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO member_wallet (member_id, recharge_balance, gift_balance, accumulated_recharge, accumulated_gift, accumulated_consumption, recharge_count, consumption_count)
VALUES
  (1, 435.00, 100.00, 500.00, 100.00, 65.00, 1, 1),
  (2, 50.00, 10.00, 100.00, 10.00, 50.00, 1, 1),
  (3, 170.00, 13.80, 100.00, 10.00, 12.00, 1, 1),
  (4, 80.00, 20.00, 0.00, 0.00, 0.00, 0, 0),
  (5, 0.00, 0.00, 0.00, 0.00, 0.00, 0, 0)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;
