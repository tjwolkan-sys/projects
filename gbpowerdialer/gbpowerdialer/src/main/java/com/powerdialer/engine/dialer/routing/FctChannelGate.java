package com.powerdialer.engine.dialer.routing;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.globalbilgi.gbqueueselectionservice.grpc.RouteDetailEntry;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;

/**
 * {@link FctChannelRegistry} ile çağrı akışı arasındaki tek karar noktası. Cursor üzerinde
 * kapasitesi uygun ilk FCT'yi bulur, rezervasyonu alır ve caller ID'yi günceller.
 *
 * <p>Rezervasyon originate'e en yakın noktada (CallOriginator.processOrigination başında) alınır:
 * LSC async işlemi ve preview timeout süresince kanal boşa tutulmaz.
 *
 * <p>Gate yalnız FCT routing açık POOL kampanyalarda ve preview olmayan akışta devrededir. Preview
 * kampanyalarda originate'i webphone attığı için rezervasyon tutulmaz; FCT routing'in kendisi
 * (liste + cause 34 iterasyonu) preview'da bugünkü gibi çalışmaya devam eder.
 */
@Component
public class FctChannelGate {

  private static final Logger LOG = LoggerFactory.getLogger(FctChannelGate.class);

  /** QSS sözleşmesindeki trunk formatı. AMI başlığına yazılacağı için burada da doğrulanır. */
  private static final Pattern TRUNK_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");

  @Autowired
  private FctChannelRegistry registry;

  /**
   * Tüm uygun FCT'ler dolu iken çağrının kuyrukta kaç originate tick'i bekleyeceği. Aşılırsa
   * mevcut "callerId bulunamadı" akışına düşülür; böylece çağrı sonsuza kadar kuyrukta dönmez.
   */
  @Value("${fct.channel.defer.limit:10}")
  private int deferLimit = 10;

  /**
   * cause 34 sonrası AYNI çağrı içinde kaç kez sıradaki FCT ile yeniden originate edilebileceği.
   *
   * <p>Varsayılan 30.09.2026'da (Faz 2) 0'dan 1'e çıkarıldı. 08.09'daki "kanal-zamanı maliyeti
   * kesin" gerekçesi Asterisk {@code h} extension guard'ıyla hafifledi: {@code HAS_NEXT=1} gelen
   * cause 34 bacağında Disconnect/ODBC/hangup_data/EXEC/qos adımları atlandığı için ara bacak yan
   * etkisiz kalıyor. Sıradaki FCT'ye tek geçiş hakkı bu nedenle makul.
   *
   * <p>Gate kill switch ile kapatıldığında bu sınır uygulanmaz (04.05 davranışı birebir geri gelir).
   */
  @Value("${fct.channel.list.retry.max:1}")
  private int listRetryMax = 1;

  /**
   * FCT listesi tükendikten sonra sabit hat caller ID'siyle kaç kez yeniden originate edilebileceği.
   *
   * <p>Bu bütçe {@link #listRetryMax}'ten AYRIDIR ve onun guard'ının dışında değerlendirilir: sabit
   * hat FCT kapasitesiyle ilgili değildir, bu yüzden {@code fct.channel.list.retry.max = 0} iken
   * bile fallback çalışır. Varsayılan 1, çünkü kaynak tek bir {@code Campaign.callerId} alanıdır;
   * birden fazla sabit hat gerekirse bu değer liste boyutuyla sınırlanır.
   */
  @Value("${fct.fixed.line.retry.max:1}")
  private int fixedLineRetryMax = 1;

  /** FCT routing akışı (cursor kurulumu ve cause 34 iterasyonu) bu çağrı için geçerli mi? */
  public boolean isFctRoutingPath(Call call) {
    Campaign campaign = call == null ? null : call.getCampaign();
    return campaign != null
        && campaign.isFctRoutingEnabled()
        && Constants.CALLER_ID_BEHAVIOUR.POOL.equals(campaign.getCallerIdBehaviour());
  }

  /** Kapasite rezervasyonu bu çağrı için geçerli mi? Preview kampanyalar kapsam dışıdır. */
  public boolean isGatePath(Call call) {
    return isFctRoutingPath(call) && !call.getCampaign().isPreviewCampaign();
  }

  /**
   * Originate öncesi kanal rezerve eder. Cursor'ın mevcut pozisyonundan başlar; dolu veya
   * cooldown'daki entry'leri atlayarak ilerler ve ilk rezerve edilebilir numarayı caller ID yapar.
   *
   * @return true: kanal rezerve edildi (veya gate bu çağrı için pasif). false: listedeki hiçbir FCT
   *         uygun değil, çağrı originate edilmemeli.
   */
  public boolean reserveForOrigination(Call call) {
    if (!isGatePath(call)) {
      return true;
    }
    RouteDetailsCursor cursor = call.getRouteDetailsCursor();
    if (cursor == null || cursor.isEmpty()) {
      return true;
    }
    RouteDetailEntry entry = cursor.current();
    while (entry != null) {
      if (tryAcquire(call, entry)) {
        return true;
      }
      entry = cursor.next();
    }
    LOG.warn("FCT kapasite gate: listedeki tum FCT'ler dolu veya cooldown'da, originate atlanacak."
        + " callId={}, campaignId={}, listSize={}", call.getId(), campaignId(call), cursor.size());
    return false;
  }

  /**
   * cause 34 sonrası çalışır: hangup'ı veren numaranın rezervasyonunu bırakır, numarayı cooldown'a
   * alır ve cursor'daki sıradaki uygun FCT için rezervasyon dener.
   *
   * @return true: yeni numara rezerve edildi, aynı Call için tekrar Originate atılabilir.
   *         false: uygun entry kalmadı, standart sonuç akışına düşülmeli.
   */
  public boolean reserveNextAfterNoCircuit(Call call) {
    RouteDetailsCursor cursor = call == null ? null : call.getRouteDetailsCursor();
    if (cursor == null) {
      return false;
    }
    releaseAndMarkFull(call);
    RouteDetailEntry next = cursor.next();
    while (next != null) {
      if (tryAcquire(call, next)) {
        return true;
      }
      next = cursor.next();
    }
    return false;
  }

  /**
   * Çağrının tuttuğu kanalı bırakır. Idempotenttir; yaşam döngüsünün birden fazla noktasından
   * çağrılması güvenlidir (hangup, expired call, forceful stop, preview timeout, originate hatası).
   */
  public void release(Call call) {
    if (call == null || !isGatePath(call)) {
      return;
    }
    registry.release(call.getCallerId(), call.getId());
  }

  /** Kampanya forceful stop sonrası artık rezervasyon kalmaması için safety net. */
  public void releaseAllForCampaign(Long campaignId) {
    registry.releaseAllForCampaign(campaignId);
  }

  /**
   * cause 34 veren numaranın rezervasyonunu bırakır ve numarayı cooldown'a alır. In-call retry
   * yapılmayacak olsa bile çağrılır: "bu FCT dolu" bilgisi diğer çağrılar için değerlidir.
   */
  public void releaseAndMarkFull(Call call) {
    String number = call == null ? null : call.getCallerId();
    if (StringUtils.isBlank(number)) {
      return;
    }
    registry.release(number, call.getId());
    registry.markFull(number);
  }

  /**
   * {@code HAS_NEXT} sözleşmesi bu çağrı için geçerli mi?
   *
   * <p>{@link #hasNextCallerId}'den AYRI bir sorudur: o "değer 1 mi 0 mı", bu "değişken hiç
   * gönderilsin mi". Kill switch kapalıyken değişken HİÇ gönderilmez — {@code 0} göndermek de
   * yeterli olurdu ama dialplan tarafında boş değer 04.05.2026 davranışına birebir denk geldiği
   * için kill switch iki tarafı da tek adımda eski hâline döndürür.
   */
  public boolean isHasNextContractActive(Call call) {
    return isGatePath(call) && registry != null && registry.isGateEnabled();
  }

  /**
   * Asterisk'e gönderilen {@code HAS_NEXT} değişkeninin kaynağı: "bu bacak cause 34 ile düşerse
   * dialer aynı Call'ı yeni bir caller ID ile tekrar originate edecek mi?"
   *
   * <p>Sözleşme tek yönlü tehlikelidir: {@code HAS_NEXT=1} gönderip re-originate ETMEMEK, Asterisk
   * tarafında {@code h} extension'ının Disconnect/ODBC/hangup_data adımlarını atlamasına ve o
   * bacağın hangup kaydının hiç oluşmamasına yol açar. Tersi (0 gönderip yine de re-originate
   * etmek) yalnızca eski davranıştır. Bu yüzden şüphede {@code false} döneriz ve bu metot gerçek
   * karar yoluyla (cause 34 sonrası {@code tryFctRetryWithNextCallerId}) AYNI yardımcılardan
   * türetilir; ayrışma {@code FctChannelGateHasNextTest} eşdeğerlik testiyle kilitlenir.
   */
  public boolean hasNextCallerId(Call call) {
    if (!isGatePath(call) || registry == null || !registry.isGateEnabled()) {
      return false;
    }
    return hasNextFctCandidate(call) || hasFixedLineFallback(call);
  }

  /**
   * Cursor'da rezerve edilebilecek bir sonraki FCT adayı var mı? Rezervasyon YAPMAZ, yalnız bakar.
   *
   * <p>Kontrol {@code reserveNextAfterNoCircuit}'in yapacağıyla aynıdır: sınır aşılmamış olmalı,
   * cursor'da entry kalmalı ve kalan entry'lerden en az biri cooldown dışında olmalı. Kapasite
   * doluluğu bilinçli olarak burada DEĞERLENDİRİLMEZ: doluluk originate anıyla hangup anı arasında
   * değişebilir ve rezervasyon denemesi olmadan kesin bilinemez; cooldown ise numara bazında
   * kalıcı bir sinyaldir.
   */
  public boolean hasNextFctCandidate(Call call) {
    if (!isListRetryAllowed(call.getFctListRetryCount())) {
      return false;
    }
    RouteDetailsCursor cursor = call.getRouteDetailsCursor();
    if (cursor == null) {
      return false;
    }
    for (RouteDetailEntry entry : cursor.remaining()) {
      if (entry != null && StringUtils.isNotBlank(entry.getPhoneNumber())
          && !registry.isInCooldown(entry.getPhoneNumber())) {
        return true;
      }
    }
    return false;
  }

  /**
   * FCT listesi tükendiğinde sabit hatla devam edilebilir mi?
   *
   * <p>Kaynak {@code Campaign.callerId}'dir (A′ kararı, 30.09.2026): kolon zaten var, kampanya
   * bazlıdır ve anlamı birebir örtüşür. Boş bırakılması "fallback kapalı" demektir — özellik
   * doldurulana kadar kendiliğinden pasiftir. Bütçe {@code fct.fixed.line.retry.max} olup FCT
   * list-retry bütçesinden AYRIDIR; {@code fct.channel.list.retry.max = 0} iken bile çalışır.
   * Registry'ye BAKILMAZ: sabit hatta kapasite kavramı yoktur.
   */
  public boolean hasFixedLineFallback(Call call) {
    return fixedLineCallerId(call) != null && call.getFixedLineAttemptCount() < fixedLineRetryMax;
  }

  /**
   * Sıradaki sabit hat caller ID'si; yoksa veya bütçe dolduysa {@code null}. Registry rezervasyonu
   * YAPMAZ. {@link #hasFixedLineFallback} ile aynı predicate'ten türer, ayrışamaz.
   */
  public String nextFixedLineCallerId(Call call) {
    return hasFixedLineFallback(call) ? fixedLineCallerId(call) : null;
  }

  /** Kampanyanın sabit hat caller ID'si; gate pasifken veya tanımsızken {@code null}. */
  private String fixedLineCallerId(Call call) {
    if (!isGatePath(call) || registry == null || !registry.isGateEnabled()) {
      return null;
    }
    String callerId = call.getCampaign().getCallerId();
    return StringUtils.isBlank(callerId) ? null : callerId;
  }

  /**
   * Aynı çağrı içinde sıradaki FCT ile yeniden originate etmeye izin var mı?
   *
   * <p>Gate kill switch ile kapalıysa sınır uygulanmaz — 04.05 FCT routing davranışı birebir
   * korunur (geri dönüş yolu tek property). Gate açıkken `fct.channel.list.retry.max` geçerlidir
   * ve varsayılanı 0'dır.
   *
   * @param alreadyRetried bu çağrı için şimdiye kadar yapılan in-call retry sayısı
   */
  public boolean isListRetryAllowed(int alreadyRetried) {
    if (registry == null || !registry.isGateEnabled()) {
      return true;
    }
    return alreadyRetried < listRetryMax;
  }

  private boolean tryAcquire(Call call, RouteDetailEntry entry) {
    if (entry == null || StringUtils.isBlank(entry.getPhoneNumber())) {
      return false;
    }
    // Doğrulanmış trunk bir kez hesaplanır ve hem registry'ye (monitor ekranı) hem Call'a (AMI
    // değişkeni) AYNI değer gider; ekranda görünen ile Asterisk'e gönderilen ayrışamaz.
    String trunk = safeTrunk(entry.getTrunk(), entry.getPhoneNumber());
    boolean acquired = registry.tryAcquire(entry.getPhoneNumber(), entry.getType(), entry.getAttempts(),
        entry.getCapacity(), trunk, call.getId(), campaignId(call));
    if (acquired) {
      // Caller ID ve trunk AYNI noktada set edilir: ikisi QSS'te aynı entry'nin alanları olduğu
      // için ayrışmaları (numara A, trunk B) çağrıyı yanlış trunk'tan çıkarır.
      call.setCallerId(entry.getPhoneNumber());
      call.setFctTrunk(trunk);
    }
    return acquired;
  }

  /**
   * QSS'ten gelen trunk değerini AMI'ye yazılmadan önce doğrular.
   *
   * <p>Değer {@code Variable: DIALER_TRUNK=<deger>} başlığına gidiyor; satır sonu veya {@code :}
   * içeren bir değer AMI protokolünü bozar ve aynı bağlantıdaki SONRAKİ action'ları da etkiler.
   * QSS kendi tarafında aynı formatı doğruluyor; bu ikinci kapı, bozuk bir kaydın AMI bağlantısını
   * düşürmesini engeller. Geçersizse {@code null} dönülür ve çağrı dialplan varsayılan trunk'ına
   * düşer — trunk yüzünden çağrı kaybedilmez.
   */
  private String safeTrunk(String trunk, String phoneNumber) {
    if (StringUtils.isBlank(trunk)) {
      return null;
    }
    if (!TRUNK_PATTERN.matcher(trunk).matches()) {
      LOG.warn("FCT trunk degeri gecersiz, varsayilan trunk'a dusuluyor: number={}, trunk={}",
          phoneNumber, trunk.replaceAll("[^A-Za-z0-9_.-]", "?"));
      return null;
    }
    return trunk;
  }

  private Long campaignId(Call call) {
    return call.getCampaign() == null ? null : call.getCampaign().getId();
  }

  public int getDeferLimit() {
    return deferLimit;
  }

  public void setDeferLimit(int deferLimit) {
    this.deferLimit = deferLimit;
  }

  public int getListRetryMax() {
    return listRetryMax;
  }

  public void setListRetryMax(int listRetryMax) {
    this.listRetryMax = listRetryMax;
  }

  public int getFixedLineRetryMax() {
    return fixedLineRetryMax;
  }

  public void setFixedLineRetryMax(int fixedLineRetryMax) {
    this.fixedLineRetryMax = fixedLineRetryMax;
  }

  public FctChannelRegistry getRegistry() {
    return registry;
  }

  /** Test seam: registry'yi enjekte etmek için (Spring dışında kurulan testler). */
  public void setRegistry(FctChannelRegistry registry) {
    this.registry = registry;
  }
}
