ALTER TABLE store_account
    ADD COLUMN sms_quota INT NOT NULL DEFAULT 0 COMMENT 'available SMS notification count';
