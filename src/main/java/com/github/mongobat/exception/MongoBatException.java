package com.github.mongobat.exception;

/**
 * @author abelski
 */
public class MongoBatException extends Exception {
  public MongoBatException(String message) {
    super(message);
  }

  public MongoBatException(String message, Throwable cause) {
    super(message, cause);
  }
}
