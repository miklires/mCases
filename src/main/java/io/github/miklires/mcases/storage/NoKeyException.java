package io.github.miklires.mcases.storage;

public final class NoKeyException extends RuntimeException {
    public NoKeyException(String caseId) {
        super("No key available for case " + caseId);
    }
}
