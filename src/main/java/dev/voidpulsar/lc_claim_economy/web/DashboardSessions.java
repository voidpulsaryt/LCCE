package dev.voidpulsar.lc_claim_economy.web;

import dev.voidpulsar.lc_claim_economy.web.auth.LoginCodeService;
import dev.voidpulsar.lc_claim_economy.web.auth.SessionManager;

/**
 * Single shared instance of each, for the lifetime of the JVM - reached from
 * both {@code /lcce web login} (issues codes) and {@link EmbeddedWebServer}
 * (redeems codes, resolves sessions). A static holder is simpler than
 * plumbing an instance through command/event registration, and matches this
 * codebase's existing pattern for cross-cutting server-lifetime state (see
 * {@code BillingBreakdownStore}).
 */
public final class DashboardSessions {
    public static final LoginCodeService LOGIN_CODES = new LoginCodeService();
    public static final SessionManager SESSIONS = new SessionManager();

    private DashboardSessions() {
    }
}
