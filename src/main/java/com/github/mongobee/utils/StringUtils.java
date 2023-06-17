package com.github.mongobee.utils;

public class StringUtils {

  private StringUtils() {}

  public static boolean hasText(String string) {
    return string != null && !string.trim().isEmpty();
  }

}
