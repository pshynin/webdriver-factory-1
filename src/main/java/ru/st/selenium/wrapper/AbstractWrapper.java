/*
 * Copyright 2013 Alexei Barantsev
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package ru.st.selenium.wrapper;

import com.google.common.base.Throwables;
import org.openqa.selenium.WebDriver;

import java.lang.reflect.*;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractWrapper<T> {

  private final T original;
  private final WebDriverWrapper driverWrapper;

  public AbstractWrapper(final WebDriverWrapper driverWrapper, final T original) {
    this.original = original;
    this.driverWrapper = driverWrapper;
  }

  public final T getWrappedOriginal() {
    return original;
  }

  public WebDriverWrapper getDriverWrapper() {
    return driverWrapper;
  }

  /**
   * Builds a {@link java.lang.reflect.Proxy} implementing all interfaces of original object. It will delegate calls to
   * wrapper when wrapper implements the requested method otherwise to original object.
   *
   * @param driverWrapper        the underlying driver's wrapper
   * @param original             the underlying original object
   * @param wrapperClass         the class of a wrapper
   */
  public final static <T> T wrapOriginal(final WebDriverWrapper driverWrapper, final T original, final Class<? extends AbstractWrapper<T>> wrapperClass) {
    AbstractWrapper<T> wrapper = null;
    Constructor<? extends AbstractWrapper<T>> constructor = null;
    if (driverWrapper == null) { // top level WebDriverWrapper
      try {
        constructor = wrapperClass.getConstructor(WebDriver.class);
      } catch (Exception e) {
      }
      if (constructor == null) {
        throw new Error("Wrapper class " + wrapperClass + " does not provide an appropriate constructor");
      }
      try {
        wrapper = constructor.newInstance(original);
      } catch (Exception e) {
        throw new Error("Can't create a new wrapper object", e);
      }

    } else { // enclosed wrapper
      if (wrapperClass.getEnclosingClass() != null) {
        try {
          constructor = wrapperClass.getConstructor(wrapperClass.getEnclosingClass(), original.getClass());
        } catch (Exception e) {
        }
      }
      if (constructor == null) {
        try {
          constructor = wrapperClass.getConstructor(WebDriverWrapper.class, original.getClass());
        } catch (Exception e) {
        }
      }
      if (constructor == null) {
        throw new Error("Wrapper class " + wrapperClass + " does not provide an appropriate constructor");
      }
      try {
        wrapper = constructor.newInstance(driverWrapper, original);
      } catch (Exception e) {
        throw new Error("Can't create a new wrapper object", e);
      }
    }
    return wrapper.wrapOriginal();
  }

  /**
   * Builds a {@link java.lang.reflect.Proxy} implementing all interfaces of original object. It will delegate calls to
   * wrapper when wrapper implements the requested method otherwise to original object.
   */
  public final T wrapOriginal() {
    final T original = getWrappedOriginal();
    final Set<Class<?>> wrapperInterfaces = extractInterfaces(this);

    final InvocationHandler handler = new InvocationHandler() {
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
          if (wrapperInterfaces.contains(method.getDeclaringClass())) {
            beforeMethod(method, args);
            Object result = callMethod(method, args);
            afterMethod(method, result, args);
            return result;
          }
          return method.invoke(original, args);
        } catch (InvocationTargetException e) {
          return onError(method, e, args);
        }
      }
    };

    Set<Class<?>> allInterfaces = extractInterfaces(original);
    allInterfaces.addAll(wrapperInterfaces);
    Class<?>[] allInterfacesArray = allInterfaces.toArray(new Class<?>[allInterfaces.size()]);

    return (T) Proxy.newProxyInstance(
        this.getClass().getClassLoader(),
        allInterfaces.toArray(allInterfacesArray),
        handler);
  }

  protected void beforeMethod(Method method, Object[] args) {
  }

  protected Object callMethod(Method method, Object[] args) throws Throwable {
    return method.invoke(this, args);
  }

  protected void afterMethod(Method method, Object res, Object[] args) {
  }

  protected Object onError(Method method, InvocationTargetException e, Object[] args) {
    throw Throwables.propagate(e.getTargetException());
  }

  private static Set<Class<?>> extractInterfaces(final Object object) {
    return extractInterfaces(object.getClass());
  }

  private static Set<Class<?>> extractInterfaces(final Class<?> clazz) {
    Set<Class<?>> allInterfaces = new HashSet<Class<?>>();
    extractInterfaces(allInterfaces, clazz);

    return allInterfaces;
  }

  private static void extractInterfaces(final Set<Class<?>> collector, final Class<?> clazz) {
    if (clazz == null || Object.class.equals(clazz)) {
      return;
    }

    final Class<?>[] classes = clazz.getInterfaces();
    for (Class<?> interfaceClass : classes) {
      collector.add(interfaceClass);
      for (Class<?> superInterface : interfaceClass.getInterfaces()) {
        collector.add(superInterface);
        extractInterfaces(collector, superInterface);
      }
    }
    extractInterfaces(collector, clazz.getSuperclass());
  }
}
