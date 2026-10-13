package com.powerdialer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.powerdialer.dto.FctRegistrySnapshotDTO;
import com.powerdialer.engine.dialer.routing.FctChannelRegistry;
import com.powerdialer.util.Constants;

/**
 * FCT Kanal Monitörü ekranının veri kaynağı. Salt okunurdur; registry durumunu değiştiren bir
 * endpoint sunulmaz.
 *
 * <p>Snapshot kısa süreli cache'lenir: ekran 1 saniyelik aralıkla poll edilebildiği ve birden fazla
 * admin oturumu olabildiği için registry kilitlerine gereksiz dokunulmaz.
 *
 * <p>Yanıt müşteri verisi taşımaz: yalnız kendi FCT numaraları, dialer interaction id'leri ve zaman
 * damgaları döner ({@code calledNumber} taşınmaz).
 */
@RestController
@RequestMapping(value = "/fctChannel")
public class FctChannelMonitorController {

  @Autowired
  private FctChannelRegistry fctChannelRegistry;

  @Value("${fct.channel.snapshot.cache.ms:1000}")
  private long snapshotCacheMs = 1000L;

  private volatile FctRegistrySnapshotDTO cachedSnapshot;
  private volatile long cachedAtMs;

  @RequestMapping(value = "/snapshot", method = RequestMethod.GET)
  @PreAuthorize("hasAuthority('" + Constants.PERMISSION_DIALER_ADMINISTRATION + "')")
  public FctRegistrySnapshotDTO snapshot() {
    long now = System.currentTimeMillis();
    FctRegistrySnapshotDTO snapshot = cachedSnapshot;
    if (snapshot != null && snapshotCacheMs > 0L && now - cachedAtMs < snapshotCacheMs) {
      return snapshot;
    }
    FctRegistrySnapshotDTO fresh = fctChannelRegistry.snapshot();
    cachedSnapshot = fresh;
    cachedAtMs = now;
    return fresh;
  }
}
