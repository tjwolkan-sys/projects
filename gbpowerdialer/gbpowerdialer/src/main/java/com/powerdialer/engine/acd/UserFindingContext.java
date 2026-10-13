package com.powerdialer.engine.acd;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.powerdialer.model.Call;
import com.powerdialer.model.User;

/**
 * Context that hold user finding strategy for call
 */
public class UserFindingContext {

  private Call call;

  /**
   * User supplier for call
   */
  private Supplier<User> userSupplier;

  /**
   * Perform finishing action when user was found
   */
  private Consumer<Call> finisher;

  public UserFindingContext(Call call,
                            Supplier<User> userSupplier,
                            Consumer<Call> finisher) {
    this.call = call;
    this.userSupplier = userSupplier;
    this.finisher = finisher;
  }

  public UserFindingContext(Call call, Supplier<User> userSupplier) {
    this.call = call;
    this.userSupplier = userSupplier;
  }

  public void execute() {
    call.setUser(userSupplier.get());
    if (finisher != null)
      finisher.accept(call);
  }

}
