package com.snoozeshare.ui.admin.accounts;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import com.snoozeshare.service.AccountSummary;

/** Live search over the displayed cell texts of an account row (spec § 3.1.1). */
public final class AccountSearch {

    private AccountSearch() {
    }

    /** True when {@code query} is blank or is contained, ignoring case, in any displayed cell text. */
    public static boolean matches(AccountSummary account, String query, ZoneId zone) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<String> cells = List.of(account.displayName(), account.email(), AccountText.role(account.role()),
                AccountText.joined(account.createdAt(), zone), AccountText.status(account.status()));
        return cells.stream().anyMatch(cell -> cell.toLowerCase(Locale.ROOT).contains(needle));
    }
}
