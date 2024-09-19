package com.cwsoft.flydi;

public interface BeanDefinition {
    Class<?> getBeanClass();
    Object getBean();
    boolean providesImplementationFor(Class<?> clazz);
}
