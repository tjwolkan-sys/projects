package com.powerdialer.engine.dialer.routing;

import java.util.Collections;
import java.util.List;

import com.globalbilgi.gbqueueselectionservice.grpc.RouteDetailEntry;

/**
 * QSS getRouteDetails sonucundaki sıralı RouteDetailEntry listesi üzerinde
 * tek yönlü iteration tutar. FCT routing akışında ilk Originate'ten önce
 * doldurulur; cause 34 (TRC 25) hangup'larında bir sonraki entry'ye geçer.
 *
 * Cursor exhaust olduğunda standart retry akışına düşülür.
 */
public final class RouteDetailsCursor {

  private final List<RouteDetailEntry> entries;
  private int position;

  public RouteDetailsCursor(List<RouteDetailEntry> entries) {
    this.entries = entries == null ? Collections.<RouteDetailEntry>emptyList() : entries;
    this.position = 0;
  }

  public boolean isEmpty() {
    return entries.isEmpty();
  }

  public boolean hasCurrent() {
    return position < entries.size();
  }

  public RouteDetailEntry current() {
    if (!hasCurrent()) {
      return null;
    }
    return entries.get(position);
  }

  public boolean hasNext() {
    return position + 1 < entries.size();
  }

  public RouteDetailEntry next() {
    if (!hasNext()) {
      return null;
    }
    position++;
    return entries.get(position);
  }

  public int size() {
    return entries.size();
  }

  public int position() {
    return position;
  }

  /**
   * Mevcut pozisyondan SONRAKİ entry'lerin salt-okunur görünümü. Cursor'ı İLERLETMEZ.
   *
   * <p>HAS_NEXT üretimi için eklendi: gate, "sıradaki adaylardan en az biri cooldown dışında mı"
   * sorusunu rezervasyon yapmadan yanıtlamak zorundadır. {@code next()} ile bakmak cursor'ı
   * tüketeceği ve gerçek karar yolunu bozacağı için ayrı bir okuma yolu gerekir. Liste kısadır
   * (retry.max + 1 mertebesinde), dönen görünüm kopyalamaz.
   */
  public List<RouteDetailEntry> remaining() {
    if (position + 1 >= entries.size()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(entries.subList(position + 1, entries.size()));
  }
}
