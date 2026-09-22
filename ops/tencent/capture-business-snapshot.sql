USE stationery_member;

SELECT metric_key, metric_value
FROM (
  SELECT 'count.admin_user' AS metric_key, CAST(COUNT(*) AS CHAR) AS metric_value FROM admin_user
  UNION ALL SELECT 'count.audit_log', CAST(COUNT(*) AS CHAR) FROM audit_log
  UNION ALL SELECT 'count.login_session', CAST(COUNT(*) AS CHAR) FROM login_session
  UNION ALL SELECT 'count.member_wallet', CAST(COUNT(*) AS CHAR) FROM member_wallet
  UNION ALL SELECT 'count.recharge_tier', CAST(COUNT(*) AS CHAR) FROM recharge_tier
  UNION ALL SELECT 'count.store_account', CAST(COUNT(*) AS CHAR) FROM store_account
  UNION ALL SELECT 'count.store_member', CAST(COUNT(*) AS CHAR) FROM store_member
  UNION ALL SELECT 'count.uploaded_file', CAST(COUNT(*) AS CHAR) FROM uploaded_file
  UNION ALL SELECT 'count.verification_code', CAST(COUNT(*) AS CHAR) FROM verification_code
  UNION ALL SELECT 'count.wallet_transaction', CAST(COUNT(*) AS CHAR) FROM wallet_transaction

  UNION ALL SELECT 'sum.member_wallet.recharge_balance', CAST(COALESCE(SUM(recharge_balance), 0.00) AS CHAR) FROM member_wallet
  UNION ALL SELECT 'sum.member_wallet.gift_balance', CAST(COALESCE(SUM(gift_balance), 0.00) AS CHAR) FROM member_wallet
  UNION ALL SELECT 'sum.member_wallet.accumulated_recharge', CAST(COALESCE(SUM(accumulated_recharge), 0.00) AS CHAR) FROM member_wallet
  UNION ALL SELECT 'sum.member_wallet.accumulated_gift', CAST(COALESCE(SUM(accumulated_gift), 0.00) AS CHAR) FROM member_wallet
  UNION ALL SELECT 'sum.member_wallet.accumulated_consumption', CAST(COALESCE(SUM(accumulated_consumption), 0.00) AS CHAR) FROM member_wallet

  UNION ALL SELECT 'max.admin_user.id', CAST(COALESCE(MAX(id), 0) AS CHAR) FROM admin_user
  UNION ALL SELECT 'max.audit_log.id', CAST(COALESCE(MAX(id), 0) AS CHAR) FROM audit_log
  UNION ALL SELECT 'max.recharge_tier.id', CAST(COALESCE(MAX(id), 0) AS CHAR) FROM recharge_tier
  UNION ALL SELECT 'max.store_account.id', CAST(COALESCE(MAX(id), 0) AS CHAR) FROM store_account
  UNION ALL SELECT 'max.store_member.id', CAST(COALESCE(MAX(id), 0) AS CHAR) FROM store_member
  UNION ALL SELECT 'max.uploaded_file.id', CAST(COALESCE(MAX(id), 0) AS CHAR) FROM uploaded_file
  UNION ALL SELECT 'max.wallet_transaction.id', CAST(COALESCE(MAX(id), 0) AS CHAR) FROM wallet_transaction

  UNION ALL SELECT 'max.store_account.updated_at', COALESCE(DATE_FORMAT(MAX(updated_at), '%Y-%m-%d %H:%i:%s'), '') FROM store_account
  UNION ALL SELECT 'max.store_member.updated_at', COALESCE(DATE_FORMAT(MAX(updated_at), '%Y-%m-%d %H:%i:%s'), '') FROM store_member
  UNION ALL SELECT 'max.member_wallet.updated_at', COALESCE(DATE_FORMAT(MAX(updated_at), '%Y-%m-%d %H:%i:%s'), '') FROM member_wallet
  UNION ALL SELECT 'max.wallet_transaction.created_at', COALESCE(DATE_FORMAT(MAX(created_at), '%Y-%m-%d %H:%i:%s'), '') FROM wallet_transaction
  UNION ALL SELECT 'max.audit_log.created_at', COALESCE(DATE_FORMAT(MAX(created_at), '%Y-%m-%d %H:%i:%s'), '') FROM audit_log
  UNION ALL SELECT 'max.uploaded_file.created_at', COALESCE(DATE_FORMAT(MAX(created_at), '%Y-%m-%d %H:%i:%s'), '') FROM uploaded_file

  UNION ALL
  SELECT CONCAT('wallet_transaction.type_status.', type, '.', status),
         CONCAT('count=', COUNT(*),
                ';amount=', CAST(COALESCE(SUM(amount), 0.00) AS CHAR),
                ';gift_amount=', CAST(COALESCE(SUM(gift_amount), 0.00) AS CHAR))
  FROM wallet_transaction
  GROUP BY type, status
) AS snapshot
ORDER BY metric_key;
