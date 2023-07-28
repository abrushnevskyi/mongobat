package com.github.mongobat.exception;

/**
 * Error while connection to MongoDB
 *
 * @author lstolowski
 * @since 27/07/2014
 */
public class MongoBatConnectionException extends MongoBatException {
  public MongoBatConnectionException(String message, Exception baseException) {
    super(message, baseException);
  }
}
