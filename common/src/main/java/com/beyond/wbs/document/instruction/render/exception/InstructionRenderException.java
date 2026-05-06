package com.beyond.wbs.document.instruction.render.exception;

public class InstructionRenderException extends RuntimeException {
    public InstructionRenderException(String message) {
        super(message);
    }

    public InstructionRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
