ALTER TABLE store_account
    ADD COLUMN expires_at DATETIME DEFAULT NULL COMMENT 'merchant account expiration time';
