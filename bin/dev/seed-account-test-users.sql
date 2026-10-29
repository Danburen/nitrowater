-- ============================================================
-- seed-account-test-users.sql
-- Quick password-only accounts for the SSO account database.
--
-- Prerequisite: nitrowater-account has started at least once, so Flyway applied the
-- V1 baseline and EncryptedKeyService created an ACTIVE AES_DATA key.
-- Run through seed-account-test-users.ps1 (it checks the key) or:
--   mysql -h 127.0.0.1 -u root -p nitrowater_account < seed-account-test-users.sql
--
-- Shared password: Passw0rd123
-- These accounts deliberately have no phone/email, so they exercise username/password
-- login across COMMON/ADMIN/SYSTEM/TEST/SUSPENDED without depending on Redis/SMS.
-- ============================================================

SET NAMES utf8mb4;

SET @pwd_hash = '$2a$10$GKnj3/XzulY.pI6nvQMQvemI7SN2pwe7xP4JBO/lVuvymDQjej4Zu';
SET @aes_key_id = (
    SELECT key_id
      FROM encryption_data_key
     WHERE key_purpose = 'AES_DATA' AND key_status = 1
     ORDER BY created_at DESC, id DESC
     LIMIT 1
);

-- Deterministic 90000000xx uids never collide with the 10-digit generated uid space
-- (generator prefix=1, length=10).
INSERT INTO `user`
    (uid, username, password_hash, account_status, user_type, level, exp, nickname)
VALUES
    (9000000001, 'testops',      @pwd_hash, 0, 5, 1, 0, 'System Ops'),
    (9000000002, 'testlab',      @pwd_hash, 0, 6, 1, 0, 'Lab Test'),
    (9000000003, 'testdisabled', @pwd_hash, 1, 0, 1, 0, 'Disabled'),
    (9000000004, 'testnopwd',    NULL,      0, 0, 1, 0, 'No Password'),
    (9000000005, 'testadminpw',  @pwd_hash, 0, 1, 1, 0, 'Pwd Admin')
ON DUPLICATE KEY UPDATE
    password_hash  = VALUES(password_hash),
    account_status = VALUES(account_status),
    user_type      = VALUES(user_type),
    nickname       = VALUES(nickname);

-- A user_data row is required by the account-info API. It can carry no phone/email:
-- both hash columns are NULL, and MySQL unique indexes allow multiple NULLs.
INSERT INTO user_data
    (user_uid, encryption_key_id, email_verified, phone_verified)
SELECT u.uid, @aes_key_id, 0, 0
  FROM `user` u
 WHERE u.username IN ('testops', 'testlab', 'testdisabled', 'testnopwd', 'testadminpw')
ON DUPLICATE KEY UPDATE
    encryption_key_id = VALUES(encryption_key_id);

INSERT IGNORE INTO user_role (uid, role_id)
SELECT u.uid, r.id
  FROM `user` u
  JOIN `role` r
 WHERE (u.username = 'testops'      AND r.code = 'SUPER_ADMIN')
    OR (u.username = 'testadminpw'  AND r.code = 'ADMIN')
    OR (u.username IN ('testlab', 'testdisabled', 'testnopwd') AND r.code = 'USER');

-- Verification query for manual runs.
SELECT u.uid, u.username, u.user_type, u.account_status,
       GROUP_CONCAT(r.code ORDER BY r.code) AS roles,
       (ud.phone_hash IS NOT NULL) AS has_phone,
       (ud.email_hash IS NOT NULL) AS has_email
  FROM `user` u
  LEFT JOIN user_data ud ON ud.user_uid = u.uid
  LEFT JOIN user_role ur ON ur.uid = u.uid
  LEFT JOIN `role` r ON r.id = ur.role_id
 WHERE u.username IN ('testops', 'testlab', 'testdisabled', 'testnopwd', 'testadminpw')
 GROUP BY u.uid, u.username, u.user_type, u.account_status
 ORDER BY u.uid;
