package com.captainalm.utils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Provides the ability to output an object with all its fields.
 *
 * @author Captain ALM
 */
public class ObjectToString {
    /**
     * Gets the string representation of the passed object with its non-static fields.
     *
     * @param objectIn The object to output.
     * @return The string representation.
     */
    public static String toStringAllNonStatic(Object objectIn) {
        if (objectIn == null) return "null";
        StringBuilder b = new StringBuilder(objectIn + " [");
        for (Field f : objectIn.getClass().getFields())
        {
            if (!Modifier.isStatic(f.getModifiers()))
            {
                try
                {
                    Object obj = f.get(objectIn);
                    b.append(f.getName()).append("=").append((obj == null) ? "null" : obj.toString()).append(" ");
                } catch (IllegalAccessException e) {}
            }
        }
        b.append(']');
        return b.toString();
    }
    /**
     * Gets the string representation of the passed object with its non-static getters.
     *
     * @param objectIn The object to output.
     * @return The string representation.
     */
    public static String toStringAllNonStaticGetters(Object objectIn) {
        StringBuilder b = new StringBuilder(objectIn + " [");
        for (Method m : objectIn.getClass().getMethods())
        {
            if (!Modifier.isStatic(m.getModifiers()) && m.getReturnType() != void.class && m.getParameterCount() == 0) {
                try
                {
                    Object obj = m.invoke(objectIn);
                    b.append(m.getName()).append("()=").append((obj == null) ? "null" : obj.toString()).append(" ");
                } catch (IllegalAccessException | InvocationTargetException e) {}
            }
        }
        b.append(']');
        return b.toString();
    }
    /**
     * Gets the string representation of the passed object with its non-static getters that return integers or booleans.
     *
     * @param objectIn The object to output.
     * @return The string representation.
     */
    public static String toStringAllNonStaticIntBoolGetters(Object objectIn) {
        StringBuilder b = new StringBuilder(objectIn + " [");
        for (Method m : objectIn.getClass().getMethods())
        {
            if (!Modifier.isStatic(m.getModifiers()) && (m.getReturnType() == int.class || m.getReturnType() == Integer.class || m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class) && m.getParameterCount() == 0) {
                try
                {
                    Object obj = m.invoke(objectIn);
                    b.append(m.getName()).append("()=").append((obj == null) ? "null" : obj.toString()).append(" ");
                } catch (IllegalAccessException | InvocationTargetException e) {}
            }
        }
        b.append(']');
        return b.toString();
    }
}
