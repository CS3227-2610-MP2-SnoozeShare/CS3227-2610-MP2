-- Money rows in audit_log now carry the running balance, so wallet_transactions can go (V008).
ALTER TABLE audit_log ADD COLUMN balanceAfter REAL;

UPDATE audit_log
SET balanceAfter = (SELECT t.balanceAfter FROM wallet_transactions t WHERE t.transactionId = audit_log.entityId)
WHERE entityType = 'WalletTransaction' AND walletAdjustment IS NOT NULL;

-- Legacy transactions that were never audited (pre-W12 data and adopted databases), oldest first.
INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState,
    walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp, balanceAfter)
SELECT lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-'
           || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(6))),
       actor.userId, actor.displayName, t.type, 'WalletTransaction', t.transactionId, NULL, NULL,
       t.amount, NULL, w.userId, owner.displayName, t.relatedBookingId, t.relatedTicketId, t.createdAt,
       t.balanceAfter
FROM wallet_transactions t
JOIN wallets w ON w.walletId = t.walletId
JOIN users owner ON owner.userId = w.userId
JOIN users actor ON actor.userId = COALESCE(t.initiatedBy, w.userId)
WHERE NOT EXISTS (SELECT 1 FROM audit_log l WHERE l.entityId = t.transactionId)
ORDER BY t.createdAt, t.transactionId;

-- The System wallet.
INSERT INTO wallets (walletId, userId, balance, currency, updatedAt)
SELECT '30000000-0000-0000-0000-0000000000ff', u.userId, 0, 'SGD', '2026-01-01T00:00:00Z'
FROM users u
WHERE u.role = 'SYSTEM' AND NOT EXISTS (SELECT 1 FROM wallets w WHERE w.userId = u.userId);

-- Fees already taken: one PLATFORM_FEE row per legacy payout, with a running System balance.
INSERT INTO audit_log (logId, actorUserId, actorName, actionType, entityType, entityId, beforeState, afterState,
    walletAdjustment, reason, subjectUserId, subjectName, bookingId, ticketId, timestamp, balanceAfter)
SELECT lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-'
           || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(6))),
       actor.userId, actor.displayName, 'PLATFORM_FEE', 'WalletTransaction',
       lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-'
           || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(6))),
       NULL, NULL, t.feeAmount, '3% platform fee on payout', sys.userId, sys.displayName,
       t.relatedBookingId, t.relatedTicketId, t.createdAt,
       SUM(t.feeAmount) OVER (ORDER BY t.createdAt, t.transactionId)
FROM wallet_transactions t
JOIN wallets w ON w.walletId = t.walletId
JOIN users actor ON actor.userId = COALESCE(t.initiatedBy, w.userId)
JOIN users sys ON sys.role = 'SYSTEM'
WHERE t.type = 'BOOKING_PAYOUT' AND COALESCE(t.feeAmount, 0) > 0
ORDER BY t.createdAt, t.transactionId;

UPDATE wallets
SET balance = (SELECT COALESCE(SUM(l.walletAdjustment), 0) FROM audit_log l
               WHERE l.actionType = 'PLATFORM_FEE' AND l.subjectUserId = wallets.userId),
    updatedAt = '2026-09-27T00:00:00Z'
WHERE userId IN (SELECT userId FROM users WHERE role = 'SYSTEM');

CREATE INDEX idx_audit_money ON audit_log (subjectUserId, walletAdjustment)
