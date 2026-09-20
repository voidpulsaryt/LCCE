/* Shared JS helpers for the Claim Economy web pages (leaderboard + dashboard).
   Plain global (no ES module) - the built-in web server just serves static
   files, and a plain <script src> include avoids any module/MIME edge cases. */
const LcceShared = (function () {
  const COIN_NAMES = ["Copper", "Iron", "Gold", "Emerald", "Diamond", "Netherite"];
  const COIN_STEP = 10;

  function formatCopper(amount) {
    if (!amount || amount <= 0) return "0c";
    let parts = [];
    let remaining = amount;
    for (let i = COIN_NAMES.length - 1; i >= 0; i--) {
      const unit = Math.pow(COIN_STEP, i);
      const count = Math.floor(remaining / unit);
      if (count > 0) {
        parts.push(count + " " + COIN_NAMES[i][0]);
        remaining -= count * unit;
      }
    }
    return parts.length ? parts.join(" ") : amount + "c";
  }

  function formatMinutes(minutes) {
    if (!minutes || minutes <= 0) return "?";
    if (minutes % 1440 === 0) return (minutes / 1440) + "d";
    if (minutes % 60 === 0) return (minutes / 60) + "h";
    return minutes + "m";
  }

  function escapeHtml(str) {
    const div = document.createElement("div");
    div.textContent = str == null ? "" : String(str);
    return div.innerHTML;
  }

  /** Fetches the {siteName, accentColor, logoUrl, customCss, dashboardEnabled} theme payload, or null on any failure. */
  async function fetchTheme() {
    try {
      const res = await fetch("/api/theme", { cache: "no-store" });
      if (!res.ok) return null;
      return await res.json();
    } catch (e) {
      return null;
    }
  }

  return { formatCopper, formatMinutes, escapeHtml, fetchTheme };
})();
