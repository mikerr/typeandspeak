
package com.googamaphone.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectUtils {
    public static Class<?> getClass(String className) {
        Class<?> result = null;

        try {
            result = Class.forName(className);
        } catch (ClassNotFoundException e) {
            // Do nothing.
        }

        return result;
    }

    public static Method getMethod(Class<?> targetClass, String name, Class<?>... parameterTypes) {
        if (targetClass == null) {
            return null;
        }

        Method result = null;

        try {
            result = targetClass.getMethod(name, parameterTypes);
        } catch (SecurityException e) {
            // Do nothing.
        } catch (NoSuchMethodException e) {
            // Do nothing.
        }

        return result;
    }

    public static Object invoke(Object receiver, Method method, Object defaultResult,
            Object... args) {
        if (method == null) {
            return defaultResult;
        }

        Object result = defaultResult;

        try {
            result = method.invoke(receiver, args);
        } catch (IllegalArgumentException e) {
            // Do nothing.
        } catch (IllegalAccessException e) {
            // Do nothing.
        } catch (InvocationTargetException e) {
            // Do nothing.
        }

        return result;
    }
}
