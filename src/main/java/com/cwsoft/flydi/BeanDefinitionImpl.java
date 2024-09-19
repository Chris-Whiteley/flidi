package com.cwsoft.flydi;

import lombok.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;


// todo: At some point move logic currently in FlyDI (e.g. around the method findBeanToInject()) into here and
// make FlyDI use this instead i.e. put this type into the FlyDI maps.
@Getter
public class BeanDefinitionImpl implements BeanDefinition {
   private final Class<?> beanClass;
   private final Constructor<?> constructor;
   private final Collection<Method> setterMethods;

   @Setter
   private Object bean;


   @Builder
   public BeanDefinitionImpl(Class<?> beanClass, Constructor<?> constructor, @Singular Collection<Method> setterMethods) {
      this.beanClass = beanClass;
      this.constructor = constructor;
      this.setterMethods = setterMethods;
   }


   @Override
   public boolean providesImplementationFor(Class<?> clazz) {
      boolean providesImplementation = false;
      Class beanClass = bean.getClass();

//      if (clazz.isAssignableFrom(beanClass)) {
//         // check the generics...
//         clazz.
//
//
//
//
//      }
//
//
//      Class superclass1 = classFromBean.getSuperclass();
//      Type genericSuperclass1 = classFromBean.getGenericSuperclass();
//      Class[] intfs1 = classFromBean.getInterfaces();
//      Type[] genintfs1 = classFromBean.getGenericInterfaces();
//
//
//
//
//      (clazz.)


      return providesImplementation;
   }
}
