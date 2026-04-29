package me.kall.appwrapper.app;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

public final class Executable {
    public static void runCommand(@NotNull List<String> command, boolean print) {
        String[] commandArray = command.toArray(String[]::new);
        if (Executable.runCommand(commandArray, print) == null) {
            System.err.println("Error executing command " + Arrays.toString(commandArray));
        }
    }

    public static @Nullable String runCommand(@NotNull String[] command, boolean print) {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException exception) {
            System.err.println("Exception building process for command " + Arrays.toString(command));
            exception.printStackTrace(System.err);
            return null;
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (print) System.out.println(line);
                output.append(line.trim()).append("\n");
            }
        } catch (IOException exception) {
            System.err.println("Exception reading input stream for command " + Arrays.toString(command));
            exception.printStackTrace(System.err);
            return null;
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) System.err.println("Command exited with code " + exitCode + ": " + Arrays.toString(command));
        } catch (InterruptedException exception) {
            System.err.println("Exception finalizing process for command " + Arrays.toString(command));
            return null;
        }

        return output.toString().trim();
    }
}