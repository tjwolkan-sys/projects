package com.powerdialer.engine.dialer;

import java.util.Collection;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;

import com.powerdialer.model.Campaign;

/**
 * FIX-333599 — Aynı kampanya DB'sinde aynı result tablosunu kullanan, o anda canlı (STOP dışında)
 * kardeş kampanyaları bulur.
 *
 * <p>Startup DB check cleanup'ı tablo genelinde {@code is_fetched}'i boşalttığı için kardeş
 * kampanyanın canlı rezervasyonlarını da silebiliyordu (02.10.2026, KFK_TAHSILAT camp 6/17).
 * Kardeşin canlıya geçiş anı ({@link CampaignContext#getLiveSince()}) cleanup'a kesme olarak
 * verilir: o andan önce yapılmış bir rezervasyon kardeşin canlı rezervasyonu olamaz.</p>
 */
public final class SharedResultTableSiblings {

  /** Canlıya geçiş anı bilinmeyen kardeş: hiçbir rezervasyonu silmemek için en erken kesme. */
  static final Date UNKNOWN_LIVE_SINCE_CUTOFF = new Date(0L);

  private SharedResultTableSiblings() {
  }

  /**
   * @return aynı result tablosunu kullanan canlı kardeşlerin en erken canlıya geçiş anı; kardeş
   *         yoksa {@code null} (cleanup bugünkü gibi kesmesiz çalışır)
   */
  public static Date earliestLiveSince(CampaignContext self, Collection<CampaignContext> contexts) {
    if (self == null || contexts == null) {
      return null;
    }
    Campaign selfCampaign = self.getCampaign();
    Date earliest = null;
    for (CampaignContext other : contexts) {
      if (!isSibling(selfCampaign, other)) {
        continue;
      }
      Date liveSince = other.getLiveSince() != null ? other.getLiveSince() : UNKNOWN_LIVE_SINCE_CUTOFF;
      if (earliest == null || liveSince.before(earliest)) {
        earliest = liveSince;
      }
    }
    return earliest;
  }

  private static boolean isSibling(Campaign self, CampaignContext otherContext) {
    if (otherContext == null || otherContext.getCampaign() == null || self == null) {
      return false;
    }
    Campaign other = otherContext.getCampaign();
    if (other == self || (self.getId() != null && self.getId().equals(other.getId()))) {
      return false;
    }
    return sameText(self.getDbResultTable(), other.getDbResultTable())
        && sameText(self.getDbConnectionString(), other.getDbConnectionString());
  }

  private static boolean sameText(String left, String right) {
    return StringUtils.isNotBlank(left) && StringUtils.equalsIgnoreCase(left.trim(), StringUtils.trimToNull(right));
  }
}
