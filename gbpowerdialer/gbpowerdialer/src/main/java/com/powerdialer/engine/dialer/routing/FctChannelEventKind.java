package com.powerdialer.engine.dialer.routing;

/**
 * {@link FctChannelRegistry} olay tipleri. Monitor ekranındaki canlı aktivite feed'i bu tipleri
 * gösterir; tipler sözleşme olduğu için isimleri geçiş notu olmadan değiştirilmez.
 */
public enum FctChannelEventKind {

  /** Numara üzerinde kanal rezerve edildi. */
  ACQUIRE,

  /** Rezervasyon bırakıldı (hangup, cleanup veya cause 34 list-retry). */
  RELEASE,

  /** Kapasite dolu olduğu için rezervasyon reddedildi. */
  REJECT,

  /** Numara cooldown'da olduğu için atlandı. */
  SKIP,

  /** cause 34 alındı, numara cooldown'a alındı. */
  MARK_FULL,

  /** TTL aşıldığı için rezervasyon temizlendi; kaçan release sinyali. */
  PRUNE,

  /** cause 34 sonrası aynı çağrı sıradaki FCT ile yeniden originate edildi. */
  REORIGINATE,

  /**
   * FCT listesi tükendi, çağrı sabit hat caller ID'siyle yeniden originate edildi. Sabit hatta
   * kapasite kavramı olmadığı için rezervasyon tutulmaz; bu yalnız gözlem olayıdır.
   */
  FALLBACK_FIXED_LINE
}
