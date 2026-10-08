ALTER TABLE login_session
  ADD COLUMN wechat_binding_id BIGINT DEFAULT NULL,
  ADD KEY idx_login_session_wechat_binding (wechat_binding_id),
  ADD CONSTRAINT fk_login_session_wechat_binding FOREIGN KEY (wechat_binding_id)
    REFERENCES store_wechat_binding (id) ON DELETE CASCADE;
