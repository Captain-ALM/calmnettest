package com.captainalm.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Provides a console wrapper for standard stream.
 *
 * @author Captain ALM
 */
public class Console {

    /**
     * Writes to {@link System#out}.
     *
     * @param dataOut The string to write.
     */
    public static void write(String dataOut) {
        System.out.print(dataOut);
    }

    /**
     * Writes to {@link System#out} terminated by a new line.
     *
     * @param dataOut The string to write.
     */
    public static void writeLine(String dataOut) {
        System.out.println(dataOut);
    }

    /**
     * Reads a string from {@link System#in} terminated by a new line.
     *
     * @return The read string.
     */
    public static String readString() {
        if (System.console() != null) return System.console().readLine(); else {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line = "";
            try {
                line = reader.readLine();
            } catch (IOException e) {
            }
            return line;
        }
    }

    /**
     * Reads a character from {@link System#in}.
     *
     * @return The read character.
     */
    public static char readCharacter() {
        char[] chars = readString().toCharArray();
        return (chars.length > 0) ? chars[0] : (char) 0;
    }

    /**
     * Reads an integer from {@link System#in} terminated by a new line.
     *
     * @return The integer or null.
     */
    public static Integer readInt() {
        try {
            return Integer.parseInt(readString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Reads a long from {@link System#in} terminated by a new line.
     *
     * @return The long or null.
     */
    public static Long readLong() {
        try {
            return Long.parseLong(readString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
