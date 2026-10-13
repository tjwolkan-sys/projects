package com.powerdialer.engine.acd;

import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;

/**
 * Originate (ring) fazinda secilen agent'in idle havuzda BIRAKILIP birakilmayacaginin tek karar
 * noktasi. Hem {@link UserFindingContextBuilder} (aday havuzu secimi) hem
 * {@link DefaultFindMatchingUserStrategy} (idle'dan cikarma) ayni yanitı kullanir; ikisi ayrisirsa
 * agent ne havuzda kalir ne de secilebilir (bkz. asagidaki "R1" notu).
 *
 * <p><b>Kapsam.</b> PREDICTIVE-GAMMA, PREDICTIVE-ALPHA ve PROGRESSIVE_RELEASED. Bu tiplerde on atama
 * SEMBOLIKTIR: gercek secim musteri actiginda (answer fazi) yapilir, agent o an havuzdan alinir ve
 * es zamanli answer'lar {@code DialWithAbandonTimeoutScript.tryLockUser} ile korunur. ALPHA'da
 * pacing zaten {@code DynamicBoostManager}'in dinamik boost degeri ile yapilir; rezervasyonun
 * kaldirilmasi o boost'un carpildigi "avail agent" sayisini gercek degerine getirir.</p>
 *
 * <p><b>Kapsam disi.</b> PROGRESSIVE_RESERVED, PREVIEW_FREE, PREVIEW_STRAIGHT, PREDICTIVE-BETA
 * ve OUTBOUND_IVR. BETA kendi butcesini {@code PredictiveBetaCallManager} uzerinden yonetir ve bu
 * is kapsaminda incelenmedi. Ozellikle PROGRESSIVE_RESERVED'de originate-fazi rezervasyonu hata degil urun
 * garantisinin mekanizmasidir: {@link UserFindingContextBuilder} secim aninda
 * {@code UserPauseMessage(true)} publish eder (hangup'ta {@code ManagerConnectionDecorator} false ile
 * kaldirir) ve {@code UserPopulationEvaluator} bu tipte {@code userId}/{@code assignedUserId} alanlarini
 * cagri basarisiz olsa bile korur. Guard'in "gamma degilse" veya "progressive ise" seklinde
 * genellestirilmemesinin sebebi budur: {@code ACDEngine.resolveUserFindingContext} BETA ve GAMMA
 * disindaki TUM tipleri ayni daldan gecirir.</p>
 *
 * <p><b>Neden gerekliydi (PROGRESSIVE_RELEASED).</b> Agent originate aninda havuzdan cikarilip
 * musteri calarken (uretim olcumu: ring medyan 10,7 sn, p90 34,1 sn) havuz disinda tutuluyordu.
 * 10.08.2026 13:00-14:00 olcumunde 5 agent'lik bir kampanyada idle havuz zamanin %75,6'sinda
 * tamamen bostu ve zaman-agirlikli ortalama havuz buyuklugu 0,30 agent'ti; ayni anda supervisor
 * ekrani 5 agent'i Available gosteriyordu. Havuz bosalinca
 * {@code ACDEngine.isDialingBoostNotReached} bütçesi devreye giriyor ve o bütçeyi de ayni
 * rezervasyonun urettigi calan cagrilar dolduruyordu.</p>
 *
 * <p><b>R1 notu.</b> {@code DefaultFindMatchingUserStrategy.removeFromIdleUsersAndCheck} donusu
 * {@code (userIsIdle || !useOnlyIdleUsers) && threshold} seklindedir. Bu yuzden idle'dan cikarmayi
 * atlamak TEK BASINA yeterli degildir: aday havuzu {@code useOnlyIdleUsers=true} ile idle kuyrugu
 * olarak birakilirsa {@code userIsIdle} false kalir, metot false doner ve HIC agent secilemez.
 * {@link UserFindingContextBuilder} bu yuzden ayni kosulda {@code useOnlyIdleUsers=false} kurar.</p>
 *
 * <p><b>Faz tespiti.</b> {@code originationDate}, originate action gonderilmeden hemen once ayni
 * thread'de set edilir ({@code CallOriginator.processOrigination}) — AMI event gecikmesinden
 * etkilenmez. TEK basina yeterli degildir: FCT caller-id retry'i
 * ({@code ManagerConnectionDecorator.tryFctRetryWithNextCallerId}) {@code originationDate}'i
 * null'layip cagriyi dogrudan yeniden originate eder ve tarih bir daha set edilmez; o cagrinin
 * answer'i originate fazi sanilirdi. Bu yuzden {@code startDate}/{@code answerDate} de kontrol
 * edilir: gercek 1. secimde ucu de null'dur, cagri calmaya/cevaplanmaya gecmisse en az biri dolu
 * olur. Nadir belirsiz yollarda rezervasyon yapan (guvenli) tarafa dusulur.</p>
 */
public final class OriginatePhase {

  private OriginatePhase() {
  }

  /**
   * @param call degerlendirilecek cagri
   * @return cagri originate fazindaysa ve kampanya tipi sembolik on atamaya uygunsa {@code true};
   *         bu durumda agent idle havuzdan cikarilmaz ve aday havuzu idle kuyrugu ile sinirlanmaz
   */
  public static boolean isWithoutReservation(Call call) {
    if (call == null || !isSymbolicPreAssignmentCampaign(call.getCampaign())) {
      return false;
    }
    // Appointed / ASSIGN_TO_SAME_USER callback sembolik DEGIL: sahip idle'dan rezerve edilir
    // (ring boyunca fresh cagri onu kapmasin), answer'da sahibine baglanir.
    return !call.isOwnerBoundCallback()
        && call.getOriginationDate() == null
        && call.getStartDate() == null
        && call.getAnswerDate() == null;
  }

  /**
   * Kampanya tipi on atamayi SEMBOLIK sayan kumeye giriyor mu? Faz kontrolu icermez; answer
   * fazindaki yeniden secim/NOAVAIL kararlari ({@code ACDEngine.trySearchLessOccupiedUser}) bu
   * kumeyi kullanir, cunku orada tarihler zaten doludur.
   *
   * @param campaign degerlendirilecek kampanya
   * @return sembolik on atama kullanan bir tip ise {@code true}
   */
  public static boolean isSymbolicPreAssignmentCampaign(Campaign campaign) {
    return campaign != null
        && (campaign.isGammaPredictiveCampaign()
            || campaign.isAlphaPredictiveCampaign()
            || campaign.isProgressiveReleasedCampaign());
  }
}
