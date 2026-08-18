package com.biopay.agent.face;

public class FaceRecognitionException extends Exception {
    public FaceRecognitionException(String message) {
        super(message);
    }

    public FaceRecognitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
