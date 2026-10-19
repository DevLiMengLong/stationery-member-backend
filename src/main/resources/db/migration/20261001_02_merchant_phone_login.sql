CREATE TABLE store_login_mobile (
  id BIGINT NOT NULL AUTO_INCREMENT,
  store_id BIGINT NOT NULL,
  mobile VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_store_login_mobile_store_mobile (store_id, mobile),
  KEY idx_store_login_mobile_mobile (mobile),
  CONSTRAINT fk_store_login_mobile_store FOREIGN KEY (store_id) REFERENCES store_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='merchant login mobile bindings';

INSERT IGNORE INTO store_login_mobile (store_id, mobile)
SELECT id, contact_mobile
FROM store_account
WHERE contact_mobile REGEXP '^1[3-9][0-9]{9}$'
  AND contact_mobile != '13800000000';

ALTER TABLE verification_code
  MODIFY COLUMN code CHAR(64) NOT NULL COMMENT 'HMAC-SHA256 digest for one-time login codes',
  ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER status;

CREATE TABLE merchant_login_challenge (
  id BIGINT NOT NULL AUTO_INCREMENT,
  token VARCHAR(64) NOT NULL,
  mobile VARCHAR(20) NOT NULL,
  auth_method VARCHAR(16) NOT NULL DEFAULT 'SMS',
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_merchant_login_challenge_token (token),
  KEY idx_merchant_login_challenge_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='short-lived verified mobile store-selection challenges';
