package com.auxiruta.commons.time;

import java.time.Clock;

/** Provee el reloj UTC que las aplicaciones pueden sustituir en pruebas temporales. */
public final class UtcClock {
  private UtcClock() {}

  public static Clock system() {
    return Clock.systemUTC();
  }
}
