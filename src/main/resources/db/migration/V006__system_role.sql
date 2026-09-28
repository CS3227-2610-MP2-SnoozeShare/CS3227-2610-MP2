-- SQLite cannot alter a CHECK constraint, so rebuild users with role SYSTEM allowed.
-- MigrationRunner runs this with foreign keys off, and column order matches V001 + V003.
CREATE TABLE users_new (
    userId TEXT PRIMARY KEY,
    role TEXT NOT NULL CHECK (role IN ('GUEST', 'HOST', 'AGENT', 'SYSTEM')),
    displayName TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    accountStatus TEXT NOT NULL CHECK (accountStatus IN ('ACTIVE', 'SUSPENDED')),
    registrationCode TEXT,
    createdAt TEXT NOT NULL,
    suspensionReason TEXT
);

INSERT INTO users_new (userId, role, displayName, email, accountStatus, registrationCode, createdAt, suspensionReason)
SELECT userId, role, displayName, email, accountStatus, registrationCode, createdAt, suspensionReason FROM users;

UPDATE users_new SET role = 'SYSTEM', accountStatus = 'ACTIVE'
WHERE userId = 'a0000000-0000-0000-0000-0000000000ff';

DROP TABLE users;

ALTER TABLE users_new RENAME TO users;
