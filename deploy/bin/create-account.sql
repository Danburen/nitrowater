-- ============================================================
-- create-account.sql - one username + password account (nitrowater_account)
--
-- NOT meant to be run on its own: it reads session variables that
-- create-account.sh sets (and validates) immediately before piping this file to
-- the client. Run it directly and the first insert fails on a NULL username.
--
--   @uid           9xxxxxxxxx, chosen by the script (see the note below)
--   @username      4-20 chars of [A-Za-z0-9_], not phone-shaped
--   @password_hash BCrypt digest, produced by the script
--   @role_id       role.id of USER / ADMIN / SUPER_ADMIN
--   @aes_key_id    active AES_DATA key id (user_data.encryption_key_id)
--   @grant_role    1 = also insert the role mapping, 0 = leave existing roles alone
--
-- Why the split: user.password_hash holds a BCrypt digest ($2a$/$2b$/$2y$, no
-- {id} prefix, cost 10 - Spring Security's BCryptPasswordEncoder). MySQL has no
-- BCrypt function, so the digest has to be produced outside the database; this
-- file only performs the writes, in one implicit transaction.
--
-- Accounts created here have neither a phone nor an email: user_data keeps both
-- hash columns NULL (the unique indexes allow that), so the account signs in with
-- username + password only - same shape as the seed-account-test-users.sql rows.
--
-- uid space: generated uids are `prefix + 9 digits` with prefix 1 (generator.uid.*),
-- i.e. 1xxxxxxxxx, so 9xxxxxxxxx is reserved for hand-made accounts. The script
-- derives it from the username so a retry lands on the same row. If
-- generator.uid.prefix/length ever change, revisit that range.
-- ============================================================

SET NAMES utf8mb4;

-- Three writes that must land together: with autocommit a failure between them
-- would leave a user row without user_data (the account-info API reads it) or a
-- role mapping without a user. The client sends one statement at a time, so the
-- session only needs START TRANSACTION here; if a statement fails the client
-- aborts and the connection close rolls the rest back.
START TRANSACTION;

-- Create, or (when the script saw an existing account) reset its password.
-- account_status / user_type / level are only used on insert: --force must not
-- resurrect an account somebody suspended on purpose, nor re-type it.
INSERT INTO `user`
    (uid, username, password_hash, account_status, user_type, level, exp)
VALUES
    (@uid, @username, @password_hash, 0, 0, 1, 0)
AS new
ON DUPLICATE KEY UPDATE
    password_hash = new.password_hash;

-- The row the account-info API reads; no phone/email, so both hashes stay NULL.
INSERT INTO user_data
    (user_uid, encryption_key_id, email_verified, phone_verified)
VALUES
    (@uid, @aes_key_id, 0, 0)
AS new
ON DUPLICATE KEY UPDATE
    encryption_key_id = new.encryption_key_id;

-- Only on create (or when --role was given explicitly): a bare --force reset
-- must not silently add a role to an account that already had different ones.
-- Not INSERT IGNORE: that would also swallow a bad role_id as a warning and
-- leave a role-less account; ON DUPLICATE KEY only suppresses the re-grant case.
INSERT INTO user_role (uid, role_id)
SELECT @uid, @role_id FROM DUAL WHERE @grant_role = 1
ON DUPLICATE KEY UPDATE role_id = role_id;

COMMIT;

SELECT u.uid, u.username, u.account_status, u.user_type,
       GROUP_CONCAT(r.code ORDER BY r.code) AS roles,
       (ud.phone_hash IS NOT NULL) AS has_phone,
       (ud.email_hash IS NOT NULL) AS has_email
  FROM `user` u
  LEFT JOIN user_data ud ON ud.user_uid = u.uid
  LEFT JOIN user_role ur ON ur.uid = u.uid
  LEFT JOIN `role` r ON r.id = ur.role_id
 WHERE u.uid = @uid
 GROUP BY u.uid, u.username, u.account_status, u.user_type;
