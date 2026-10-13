package com.powerdialer.exceptions;

/**
 * QSS {@code getRouteDetails} çağrısına ULAŞILAMADI: gRPC channel kurulmamış veya çağrı hata attı.
 *
 * <p>Bu, {@link NoAvailablePoolCallerIdException}'dan bilinçli olarak ayrıdır ve ayrım davranışsaldır:
 * <ul>
 *   <li>Boş liste (QSS "grup bulunamadı" veya route yok) = <b>kalıcı</b> konfigürasyon eksikliği →
 *       {@code NoAvailablePoolCallerIdException} → çağrı retry'sız iptal edilir.</li>
 *   <li>Bu exception = <b>geçici</b> erişim sorunu (QSS/Eureka kesintisi) → çağrı retry'a izin
 *       verilerek iptal edilir. Aksi hâlde kısa bir kesintide o an originate edilen tüm çağrılar
 *       kalıcı olarak kaybedilirdi.</li>
 * </ul>
 */
public class RouteDetailsUnavailableException extends Exception {

  private static final long serialVersionUID = 1L;

  public RouteDetailsUnavailableException(String message) {
    super(message);
  }

  public RouteDetailsUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
