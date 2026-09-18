package com.ney.moonsalary.registry;

import com.ney.moonsalary.config.type.SalaryGroupSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Группа зарплат.
 * Неизменяемая модель, создаётся из конфигурации.
 */
public class SalaryGroup {

    private final String name;
    private final double salary;
    private final int priority;
    private final List<String> messages;
    private final List<String> commands;

    public SalaryGroup(@NotNull SalaryGroupSettings settings) {

        this.name = settings.name();
        this.salary = settings.salary();
        this.priority = settings.priority();
        this.messages = settings.messages();
        this.commands = settings.commands();

    }

    public String getName() {
        return name;
    }

    public double getSalary() {
        return salary;
    }

    public int getPriority() {
        return priority;
    }

    public List<String> getMessages() {
        return messages;
    }

    public List<String> getCommands() {
        return commands;
    }

    public boolean hasCommands() {
        return !commands.isEmpty();
    }

    public boolean hasMessages() {
        return !messages.isEmpty();
    }
}
