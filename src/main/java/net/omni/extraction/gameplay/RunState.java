package net.omni.extraction.gameplay;

public enum RunState {

    IDLE,
    IN_RUN;

    public boolean isInRun() {
        return this == IN_RUN;
    }
}
