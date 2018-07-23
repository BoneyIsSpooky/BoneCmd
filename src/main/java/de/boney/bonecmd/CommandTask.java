package de.boney.bonecmd;

@FunctionalInterface
public interface CommandTask {
    void execute(Arguments args);
}
