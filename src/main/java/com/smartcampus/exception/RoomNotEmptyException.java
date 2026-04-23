package com.smartcampus.exception;

/**
 *
 * @author chenuli
 */
public class RoomNotEmptyException extends RuntimeException {
    public RoomNotEmptyException(String message) {
        super(message);
    }
}