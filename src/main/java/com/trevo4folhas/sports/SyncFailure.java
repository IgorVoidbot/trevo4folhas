package com.trevo4folhas.sports;

/** Safe, fixed internal codes; provider payloads and credentials never become exception text. */
public final class SyncFailure extends RuntimeException {
  private final String code;

  public SyncFailure(String code) {
    super("Sports synchronization blocked: " + code);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
