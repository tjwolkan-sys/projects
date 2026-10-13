package com.powerdialer.engine.acd;

import java.util.function.Consumer;

import com.powerdialer.model.Call;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.redis.message.UserPauseMessage;

/**
 * Builder for UserFindingContext, provides dialing behavior for campaign types
 * and agent occupancy
 */
public class UserFindingContextBuilder {

  private final RedisUtil redisUtil;
  private final ACDEngine acdEngine;

  public UserFindingContextBuilder(RedisUtil redisUtil, ACDEngine acdEngine) {
    this.redisUtil = redisUtil;
    this.acdEngine = acdEngine;
  }

  public UserFindingContext buildUserFindingContext(Call call, boolean shouldUseIdleUsers) {
    FindMatchingUserStrategy findMatchingUserStrategy;
    Consumer<Call> finisherAction = null;
    switch (call.getCampaign().getType()) {
      case PREVIEW_FREE:
      case PREVIEW_STRAIGHT:
        findMatchingUserStrategy = new DefaultFindMatchingUserStrategy(acdEngine, call, true);
        break;
      case PREDICTIVE:
        // ALPHA icin PROGRESSIVE_RELEASED ile ayni gerekce gecerlidir (asagidaki aciklama).
        // BETA ve GAMMA'ya ACDEngine zaten shouldUseIdleUsers=false gonderdigi icin ifade onlar
        // uzerinde etkisizdir; davranislari bit-esit kalir.
        findMatchingUserStrategy = new DefaultFindMatchingUserStrategy(acdEngine, call,
            shouldUseIdleUsers && !OriginatePhase.isWithoutReservation(call));
        break;
      case PROGRESSIVE_RELEASED:
        // Originate fazinda agent idle havuzda BIRAKILIR (bkz. OriginatePhase). Bunun calisabilmesi
        // icin aday havuzu da idle kuyrugu OLMAMALIDIR: DefaultFindMatchingUserStrategy
        // .removeFromIdleUsersAndCheck donusu (userIsIdle || !useOnlyIdleUsers) && threshold
        // seklinde oldugundan, useOnlyIdleUsers=true birakilirsa cikarma atlandiginda userIsIdle
        // false kalir, metot false doner ve HIC agent secilemez (kampanya cagri cikaramaz).
        // useOnlyIdleUsers=false ile aday havuzu getAvailableUsersQueueInGroup olur; oradaki
        // minCallsNumber yuk dengeleme filtresi ve UserAvailTimeComparator siralamasi korunur.
        // Answer fazinda (originationDate/startDate/answerDate dolu) eski yol aynen surer ve
        // gercek rezervasyon orada yapilir.
        findMatchingUserStrategy = new DefaultFindMatchingUserStrategy(acdEngine, call,
            shouldUseIdleUsers && !OriginatePhase.isWithoutReservation(call));
        break;
      case PROGRESSIVE_RESERVED:
        findMatchingUserStrategy = new DefaultFindMatchingUserStrategy(acdEngine, call, shouldUseIdleUsers);
        finisherAction = (resultCall) -> {
          if (resultCall.getUser() != null) {
            redisUtil.publishUserPauseMessage(new UserPauseMessage(resultCall.getUser().getId(), true));
          }
        };
        break;
      default:
        findMatchingUserStrategy = null;
    }
    return findMatchingUserStrategy != null ? new UserFindingContext(call, findMatchingUserStrategy::findMatchingUser, finisherAction) : null;
  }
}
