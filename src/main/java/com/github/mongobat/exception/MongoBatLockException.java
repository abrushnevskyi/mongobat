package com.github.mongobat.exception;

/**
 * Error while can not obtain process lock
 */
public class MongoBatLockException extends MongoBatException {
  public MongoBatLockException(String message) {
    super(message);
  }
}
