package com.beyond.wbs.document.instruction.render.exception;

public class InstructionUploadException extends RuntimeException {
    public InstructionUploadException(String message) {
        super(message);
    }

    public InstructionUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
