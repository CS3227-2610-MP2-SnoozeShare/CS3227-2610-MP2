# SDD ledger — plan: docs/superpowers/plans/2026-09-27-host-messages-ui.md

Pre-flight: Tasks 1→2 share the host ticket candidate/service boundary; Tasks 2→3 share the
HostConversationRow and booking bubble renderer; Tasks 3→4 share the New Ticket callback; Tasks
3→5 share HostMessagesController cleanup and shell loading. No interface conflicts found.

Task 1: complete (focused ticket-service tests pass; full suite has the pre-existing
`AgentModalTest.escapeAndWindowCloseAlsoRemoveTheScrim` failure).
