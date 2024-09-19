package com.cwsoft.flydi;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.scanners.*;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import javax.annotation.ManagedBean;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@SuppressWarnings("unused")
@Slf4j
public class FlyDI {
    private final BeanScannerConfig beanScannerConfig;

    @Getter
    private Reflections reflections;
    private final Map<String, Object> beansByName = new ConcurrentHashMap<>();
    private final Map<String, Object> beansByClass = new ConcurrentHashMap<>();
    private final DependencyGraph<String> dependencyGraph = new DependencyGraph<>();

    private @Getter
    static FlyDI instanceOf;

    public FlyDI(BeanScannerConfig beanScannerConfig) {
        this.beanScannerConfig = beanScannerConfig;
        instanceOf = this;
    }

    public void scanForBeans() {
        buildReflections(beanScannerConfig);
        addBean(this);
        findAndInstantiateBeans(beanScannerConfig.getSystem());
    }

    public void injectBeans() {

        log.trace("Perform required injections of each @ManagedBean ");

        beansByClass.values().forEach(bean -> {
            try {
                injectBean(bean);
                addDependency(bean);
            } catch (InjectError | BeanProcessingError ex) {
                log.error("Unrecoverable error shutting down", ex);
                System.exit(1);
            }
        });
    }

    public void injectBean(Object bean) throws InjectError, BeanProcessingError {
        checkForDependsOnDependencies(bean);

        for (final Method method : getAllMethods(bean.getClass())) {
            if (method.isAnnotationPresent(Inject.class)) {

                Object beanToInject = null;

                try {
                    method.setAccessible(true);
                    beanToInject = findBeanToInject(method);
                    method.invoke(bean, beanToInject);
                    addDependency(bean, beanToInject);
                } catch (Exception ex) {
                    String beanToInjectName = beanToInject != null ? beanToInject.toString() : "";
                    String msg = String.format("Failed to inject %s into method %s of class %s", beanToInject, method.getName(), bean.getClass());
                    throw new InjectError(msg, ex);
                }
            }
        }
    }

    public Collection<String> getBeanNames() {
        Collection<String> beanNames = new HashSet<>(beansByName.keySet());
        beanNames.addAll(beansByClass.keySet());
        return beanNames;
    }

    public Collection<Object> getAllBeans() {
        Collection<Object> beans = new HashSet<>(beansByName.values());
        beans.addAll(beansByClass.values());
        return beans;
    }

    public void runPostConstructors() {
        try {
            log.trace("For each each @ManagedBean run the @PostConstruct methods using dependency order");

            Collection<String> classNamesInDependencyOrder = dependencyGraph.topologicalSort();

            classNamesInDependencyOrder.forEach(className -> {
                Object bean = beansByClass.get(className);
                try {
                    invokePostConstructIfPresent(bean);
                } catch (Exception ex) {
                    log.error("Error running post constructor", ex);
                    System.exit(1);
                }
            });

            log.trace("Finished running post constructors {} classes checked.", classNamesInDependencyOrder.size());

        } catch (DependencyGraph.CircularDependencyException e) {
            log.error("Circular Dependency detected. ", e);
            System.exit(1);
        }
    }

    public void invokePostConstructIfPresent(Object bean) throws PostConstructError, BeanProcessingError {
        for (final Method method : getAllMethods(bean.getClass())) {
            if (method.isAnnotationPresent(PostConstruct.class)) {
                try {
                    log.trace("running post constructor on class {}", bean.getClass().getName());
                    method.setAccessible(true);
                    method.invoke(bean);
                } catch (InvocationTargetException ex) {
                    throw new PostConstructError(String.format("Failed invoking @PostConstruct on Class %s Method %s", bean.getClass().getName(), method.getName()), ex.getTargetException());
                } catch (Exception ex) {
                    log.error("Failed to invoke PostConstruct on Class {} Method {}", bean.getClass().getName(), method.getName(), ex);
                    throw new PostConstructError(String.format("Failed invoking @PostConstruct on Class %s Method %s", bean.getClass().getName(), method.getName()), ex);
                }
            }
        }
    }

    public Object getBean(String beanName) {

        Object bean = beansByName.get(beanName);
        if (bean != null) return bean;

        bean = beansByAssignableName.get(beanName);
        if (bean != null) return bean;

        bean = beansByClass.get(beanName);
        if (bean == null) log.error("Bean {} not found. {} Beans available.", beanName, beansByName.size());
        return bean;
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> requiredType) {
        T bean = (T) beansByClass.get(requiredType.getName());

        if (null == bean) {
            bean = (T) beansByAssignableClass.get(requiredType.getName());

            if (null == bean) {
                log.error("Bean of type {} not found. {} Beans available.", requiredType, beansByName.size());
            }
        }

        return bean;
    }

    @SuppressWarnings("unchecked")
    public <T> T getPrototypeBean(Class<T> requiredType) {
        var bean = newInstance(requiredType);
        injectBean(bean);
        invokePostConstructIfPresent(bean);
        return bean;
    }

    @SuppressWarnings("WeakerAccess")
    public void addBean(Object beanToAdd) {
        log.trace("Adding bean with name: {} Class: {}", beanToAdd.getClass().getSimpleName(), beanToAdd.getClass().getName());
        beansByName.put(beanToAdd.getClass().getSimpleName(), beanToAdd);
        beansByClass.put(beanToAdd.getClass().getName(), beanToAdd);
    }

    @SuppressWarnings("WeakerAccess")
    public void addBean(String name, Object beanToAdd) {
        log.trace("Adding bean with name: {} Class: {}", name, beanToAdd.getClass().getName());
        beansByName.put(name, beanToAdd);
        beansByClass.put(beanToAdd.getClass().getName(), beanToAdd);
    }

    public void addAssignableBean(Class superClazz, Object assignableFromBean) {
        beansByAssignableClass.put(superClazz.getName(), assignableFromBean);
        beansByAssignableName.put(superClazz.getSimpleName(), assignableFromBean);
    }

    public Optional<Constructor> findDefaultConstructor(Class<?> forClass) {

        Constructor[] allConstructors = forClass.getDeclaredConstructors();

        return Stream.of(allConstructors)
                .filter(constructor -> constructor.getParameterTypes().length == 0)
                .findFirst();
    }

    public static List<Method> getAllMethods(Class<?> forClass) throws BeanProcessingError {
        try {
            List<Method> allMethods = new ArrayList<>();
            Class<?> clazz = forClass;

            // Iterate through the class hierarchy
            while (clazz != null) {
                allMethods.addAll(Arrays.asList(clazz.getDeclaredMethods()));
                // Move to the superclass
                clazz = clazz.getSuperclass();
            }

            return allMethods;
        } catch (Exception | NoClassDefFoundError ex) {
            String msg = String.format("Failed to obtain methods of class %s", forClass);
            throw new BeanProcessingError(msg, ex);
        }
    }

    private void buildReflections(BeanScannerConfig beanScannerConfig) {

        final ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();

        StringBuilder includeString = new StringBuilder();
        final FilterBuilder filterBuilder = new FilterBuilder();

        for (String pkg : beanScannerConfig.getPackagesToInclude()) {
            configurationBuilder.addUrls(ClasspathHelper.forPackage(pkg));
            filterBuilder.include(pkg + "\\..*");
        }

        for (String pkgToExclude : beanScannerConfig.getPackagesToExclude()) {
            filterBuilder.exclude(pkgToExclude + "\\..*");
        }

        configurationBuilder.filterInputsBy(filterBuilder)
                .setScanners(new SubTypesScanner(),
                        new TypeAnnotationsScanner(),
                        new FieldAnnotationsScanner(),
                        new MethodAnnotationsScanner(),
                        new MethodParameterScanner(),
                        new MethodParameterNamesScanner(),
                        new MemberUsageScanner());

        reflections = new Reflections(configurationBuilder);
        addBean("Reflections", reflections);
    }

    private void findAndInstantiateBeans(String system) {

        String managedBeanName = "";

        try {

            for (final Class<?> clazz : reflections.getTypesAnnotatedWith(ManagedBean.class)) {
                managedBeanName = clazz.getName();
                String beanValue = clazz.getAnnotation(ManagedBean.class).value();

                if (beanValue.isEmpty() || beanValue.equalsIgnoreCase(system)) {
                    createBean(clazz.getSimpleName(), clazz);
                } else {
                    log.trace("Skipping bean with name: {} Class: {} For System {} ", clazz.getSimpleName(), clazz.getName(), beanValue.toUpperCase());
                }
            }

        } catch (Exception ex) {
            log.error("Failed while instantiating @ManagedBean {}", managedBeanName, ex);
            log.error("Unrecoverable error shutting down");
            System.exit(1);
        }
    }

    private void createBean(String name, final Class<?> clazz) throws InstantiationException {
        log.trace("Creating bean with name: {} Class: {}", name, clazz.getName());

        var defaultConstructorOptional = findDefaultConstructor(clazz);

        try {
            if (defaultConstructorOptional.isPresent()) {
                var defaultConstructor = defaultConstructorOptional.get();
                defaultConstructor.setAccessible(true);
                final Object newBean = defaultConstructor.newInstance();
                beansByName.put(name, newBean);
                beansByClass.put(clazz.getName(), newBean);
            } else {
                log.error("@ManagedBean failed to create new instance of {}, no default constructor found", name);
                log.error("Unrecoverable error shutting down");
                System.exit(1);
            }
        } catch (Exception ex) {
            log.error("@ManagedBean failed to create new instance of {}, check it has a default constructor {}", name, ex);
            log.error("Unrecoverable error shutting down");
            System.exit(1);
        }

    }

    private <T> T newInstance(Class<T> requiredType) {
        try {
            Optional<Constructor> defaultConstructorOptional = findDefaultConstructor(requiredType);
            Constructor defaultConstructor = defaultConstructorOptional.get();
            defaultConstructor.setAccessible(true);
            return (T) defaultConstructor.newInstance();
        } catch (Exception ex) {
            String msg = String.format("Failed to create new instance of %s , check it has a default constructor", requiredType);
            throw new BeanProcessingError(msg, ex);
        }
    }

    private void checkForDependsOnDependencies(Object bean) {
        if (bean.getClass().isAnnotationPresent(DependsOn.class)) {
            DependsOn dependsOnAnnotation = bean.getClass().getAnnotation(DependsOn.class);
            Arrays.stream(dependsOnAnnotation.value())
                    .forEach(dependency -> {
                        Object dependsOnBean = beansByName.get(dependency);

                        if (dependsOnBean == null) {
                            String msg = String.format("Could not find named bean in class @DependsOn annotation. Dependency: %s Class: %s", dependency, bean.getClass());
                            throw new InjectError(msg);
                        }

                        addDependency(bean, dependsOnBean);
                    });
        }
    }

    private final Map<String, Object> beansByAssignableClass = new ConcurrentHashMap<>();
    private final Map<String, Object> beansByAssignableName = new ConcurrentHashMap<>();

    private Object findBeanToInject(Method injectMethod) throws InjectError {

        Class<?>[] paramsTypes = injectMethod.getParameterTypes();

        if (paramsTypes.length == 0) {
            String msg = String.format("Failed to inject into method %s - method has no parameters", injectMethod.getName());
            throw new InjectError(msg);
        }

        if (paramsTypes.length > 1) {
            String msg = String.format("Failed to inject into method %s - method has more than one parameter", injectMethod.getName());
            throw new InjectError(msg);
        }

        AtomicReference<Object> beanToInject = new AtomicReference<>(null);
        // check for @Named
        Named namedBean = injectMethod.getAnnotation(Named.class);
        if (null != namedBean) {
            beanToInject.set(beansByName.get(namedBean.value()));

            if (beanToInject.get() == null) {
                String msg = String.format("Could not find named bean to inject. Named Bean: %s Method: %s", namedBean.value(), injectMethod.getName());
                throw new InjectError(msg);
            }

            return beanToInject.get();
        }

        // not named bean...
        // can we find it by its parameter name
        Parameter[] parameters = injectMethod.getParameters();
        beanToInject.set(beansByName.get(parameters[0].getName()));

        if (null != beanToInject.get() && paramsTypes[0].isAssignableFrom(beanToInject.get().getClass())) {
            return beanToInject.get();
        }

        // can we find it by its method name
        var beanNameFromMethodName = injectMethod.getName().substring(3);
        if (!beanNameFromMethodName.isEmpty()) {
            var bean = beansByName.get(beanNameFromMethodName);
            if (bean == null) {
                // try with first char lowercase
                String firstCharLowercase = beanNameFromMethodName.substring(0, 1).toLowerCase() + beanNameFromMethodName.substring(1);
                bean = beansByName.get(firstCharLowercase);
            }

            if (null != bean) {
                // check if assignable
                if (paramsTypes[0].isAssignableFrom(bean.getClass())) {
                    return bean;
                }
            }
        }

        // dow we have a bean that is assignable from this type
        beanToInject.set(beansByAssignableClass.get(paramsTypes[0].getName()));
        if (null != beanToInject.get()) return beanToInject.get();

        // can we find an exact instance of this class
        beanToInject.set(beansByClass.get(paramsTypes[0].getName()));
        if (null != beanToInject.get()) return beanToInject.get();

        // can we find it as an assignable bean i.e. one that where this argument is a superclass or superinterface of the bean
        Class<?> paramClass = paramsTypes[0];
        Type paramType = injectMethod.getGenericParameterTypes()[0];

        Collection<Map.Entry<String, Object>> assignableEntries =
                beansByClass.entrySet()
                        .stream()
                        .filter(stringObjectEntry -> beanProvidesImplementation(paramClass, paramType, stringObjectEntry.getValue())
                        )
                        .toList();

        if (assignableEntries.size() == 1) {
            // found a single matching entry, good!
            Map.Entry<String, Object> assignableEntry = assignableEntries.stream().findFirst().get();
            beanToInject.set(assignableEntry.getValue());
            addAssignableBean(paramClass, assignableEntry.getValue());
            return beanToInject.get();
        }

        if (assignableEntries.size() > 1) {
            // multiple match we don't know which to choose
            String msg = String.format("Found multiple matching beans to inject for Type: %s Method: %s",
                    paramsTypes[0].getName(), injectMethod.getName());

            msg = msg +
                    String.format("/n%s, the following beans are assignable %s, consider using @Named annotation", injectMethod.getName(), assignableEntries);

            throw new InjectError(msg);
        }

        // sorry, can't find it run out of options!
        String msg = String.format("Could not find bean to inject Type: %s Method: %s", paramsTypes[0].getName(), injectMethod.getName());
        throw new InjectError(msg);
    }

    private boolean beanProvidesImplementation(Class<?> paramClass, Type paramType, Object bean) {
        Class<?> beanClass = bean.getClass();

        if (paramClass.isAssignableFrom(beanClass)) {

            if (!(paramType instanceof ParameterizedType)) {
                // we are not dealing with generics so this is good enough
                return true;
            }

            return providesGenericImplementation(paramClass, (ParameterizedType) paramType, bean, beanClass, new HashMap<>());
        }
        return false;
    }


    private boolean providesGenericImplementation(Class paramClass, ParameterizedType genericParamType, Object
            bean, Class checkClass, Map<TypeVariable, Type> genericActualTypesMap) {
        if (Object.class.equals(checkClass)) return false;

        // is it assignable from the superclass
        if (paramClass.isAssignableFrom(checkClass.getSuperclass())) {

            if (checkClass.getGenericSuperclass().equals(paramClass)) {
                return true;
            }

            if (checkClass.getGenericSuperclass() instanceof ParameterizedType) {
                // are the generic params the same?
                ParameterizedType genericSuperType = (ParameterizedType) checkClass.getGenericSuperclass();

                if (genericSuperType.equals(genericParamType)) {
                    return true;
                }

                Type[] superClassActualArguments = genericSuperType.getActualTypeArguments();
                TypeVariable[] superClassTypeParameters = checkClass.getSuperclass().getTypeParameters();

                for (int i = 0; i < superClassTypeParameters.length; i++) {
                    genericActualTypesMap.put(superClassTypeParameters[i], superClassActualArguments[i]);
                }
            }

            return providesGenericImplementation(paramClass, genericParamType, bean, checkClass.getSuperclass(), genericActualTypesMap);

        } else {
            // must be assignable from one of the interfaces
            Class[] intfs = checkClass.getInterfaces();
            Type[] genintfs = checkClass.getGenericInterfaces();

            for (int i = 0; i < intfs.length; i++) {
                Class intf = intfs[i];

                if (paramClass.isAssignableFrom(intf)) {

                    // parameter is assignable from this interface
                    Type genintf = genintfs[i];
                    if (genintf.equals(genericParamType)) {
                        return true;
                    }

                    // the following if statement is if we are dealing with generics
                    if (genintf instanceof ParameterizedType) {

                        // are the generic params the same?
                        ParameterizedType genintfType = (ParameterizedType) genintf;
                        if (genericTypesMatch(genericParamType.getActualTypeArguments(), genintfType.getActualTypeArguments(), genericActualTypesMap)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    private boolean genericTypesMatch(Type[] requiredTypes, Type[]
            implementationTypes, Map<TypeVariable, Type> genericActualTypesMap) {
        boolean match = false;

        if (requiredTypes.length == implementationTypes.length) {
            match = true;

            for (int i = 0; i < requiredTypes.length; i++) {
                Type requiredType = requiredTypes[i];
                Type implementationType = implementationTypes[i];
                Type implementationActualType = genericActualTypesMap.get(implementationType);
                if (implementationActualType != null) implementationType = implementationActualType;
                if (!implementationType.equals(requiredType)) {
                    match = false;
                    break;
                }
            }
        }

        return match;
    }

    private void addDependency(Object clazz, Object inject) {
        if (beanScannerConfig.isInScannedPackages(clazz) && beanScannerConfig.isInScannedPackages(inject) ) {
            dependencyGraph.addDependency(clazz.getClass().getName(), inject.getClass().getName());
        }
    }

    private void addDependency(Object clazz) {
        if (beanScannerConfig.isInScannedPackages(clazz)) {
            dependencyGraph.add(clazz.getClass().getName());
        }
    }
}
