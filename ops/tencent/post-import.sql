USE stationery_member;

START TRANSACTION;
DELETE FROM login_session;
DELETE FROM verification_code;
COMMIT;
