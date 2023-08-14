package ru.russianinvestments.piapi.core.utils;

import ru.russianinvestments.piapi.core.exception.ReadonlyModeViolationException;
import ru.russianinvestments.piapi.core.exception.SandboxModeViolationException;

import java.time.Instant;

public class ValidationUtils {
  private static final String TO_IS_NOT_AFTER_FROM_MESSAGE = "Окончание периода не может быть раньше начала.";
  private static final String WRONG_PAGE_MESSAGE = "Номерами страниц могут быть только положительные числа.";


  public static void checkPage(int page) {
    if (page < 0) {
      throw new IllegalArgumentException(WRONG_PAGE_MESSAGE);
    }
  }

  public static void checkFromTo(Instant from, Instant to) {
    if (from.isAfter(to)) {
      throw new IllegalArgumentException(TO_IS_NOT_AFTER_FROM_MESSAGE);
    }
  }

  public static void checkReadonly(boolean readonlyMode) {
    if (readonlyMode) {
      throw new ReadonlyModeViolationException();
    }
  }

  public static void checkSandbox(boolean sandboxMode) {
    if (sandboxMode) {
      throw new SandboxModeViolationException();
    }
  }
}