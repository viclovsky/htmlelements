package io.qameta.htmlelements.handler;

import io.qameta.htmlelements.annotation.FindBy;
import io.qameta.htmlelements.context.Context;
import io.qameta.htmlelements.exception.NotImplementedException;
import io.qameta.htmlelements.proxy.Proxies;
import io.qameta.htmlelements.util.ReflectionUtils;
import io.qameta.htmlelements.waiter.SlowLoadableComponent;
import org.hamcrest.Matcher;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.internal.Locatable;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.htmlelements.util.ReflectionUtils.getMethods;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;

public class WebBlockMethodHandler implements InvocationHandler {

    private static final String FILTER_KEY = "filter";

    private static final String CONVERTER_KEY = "convert";

    private final Supplier targetProvider;

    private final Context context;

    private final Class[] targetClasses;

    public WebBlockMethodHandler(Context context, Supplier targetProvider, Class... targetClasses) {
        this.targetProvider = targetProvider;
        this.targetClasses = targetClasses;
        this.context = context;
    }

    private Class[] getTargetClasses() {
        return targetClasses;
    }

    private Supplier getTargetProvider() {
        return this.targetProvider;
    }

    private Context getContext() {
        return context;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        // default
        if (method.isDefault()) {
            return invokeDefaultMethod(proxy, method, args);
        }

        //context
        if (Context.class.equals(method.getReturnType())) {
            return getContext();
        }

        Class<?>[] targetClass = getTargetClasses();

        // web element proxy
        if (getMethods(targetClass).contains(method.getName())) {
            return invokeTargetMethod(getTargetProvider(), method, args);
        }

        // extension
        if ("waitUntil".equals(method.getName())) {
            return invokeWaitUntilMethod(proxy, method, args);
        }

        // extension
        if ("filter".equals(method.getName())) {
            return invokeFilterMethod(proxy, method, args);
        }

        if ("convert".equals(method.getName())) {
            return invokeConvertMethod(proxy, method, args);
        }

        // extension
        if ("should".equals(method.getName())) {
            return invokeShouldMethod(proxy, method, args);
        }

        // extension
        if ("toString".equals(method.getName())) {
            return String.format("{name: %s, selector: %s}",
                    getContext().getName(),
                    getContext().getSelector()
            );
        }

        Class<?> proxyClass = method.getReturnType();

        String name = ReflectionUtils.getName(method, args);
        String selector = ReflectionUtils.getSelector(method, args);

        Context childContext = getContext().newChildContext(name, selector, method.getReturnType());

        // html element proxy (recurse)
        if (method.isAnnotationPresent(FindBy.class) && WebElement.class.isAssignableFrom(proxyClass)) {
            return createProxy(
                    method.getReturnType(),
                    childContext,
                    () -> ((SearchContext) proxy).findElement(By.xpath(selector)),
                    WebElement.class, Locatable.class
            );
        }

        // html element list proxy (recurse)
        if (method.isAnnotationPresent(FindBy.class) && List.class.isAssignableFrom(method.getReturnType())) {
            return createProxy(method.getReturnType(), childContext, () -> {
                List<WebElement> originalElements = ((SearchContext) proxy).findElements(By.xpath(selector));
                Type methodReturnType = ((ParameterizedType) method
                        .getGenericReturnType()).getActualTypeArguments()[0];
                return (List) originalElements.stream()
                        .map(element -> createProxy((Class<?>) methodReturnType, childContext, () -> element, WebElement.class))
                        .collect(toList());
            }, List.class);
        }

        throw new NotImplementedException(method);
    }

    private Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable {
        Class<?> declaringClass = method.getDeclaringClass();
        Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class
                .getDeclaredConstructor(Class.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(declaringClass, MethodHandles.Lookup.PRIVATE)
                .unreflectSpecial(method, declaringClass)
                .bindTo(proxy)
                .invokeWithArguments(args);
    }

    @SuppressWarnings("unchecked")
    private Object invokeTargetMethod(Supplier<?> targetProvider, Method method, Object[] args)
            throws Throwable {
        return ((SlowLoadableComponent<Object>) () -> {
            if (List.class.isAssignableFrom(getTargetClasses()[0])) {

                Stream targetStream = ((List) targetProvider.get()).stream();

                List<Predicate> filters = getContext().getStore().containsKey(FILTER_KEY) ?
                        (List<Predicate>) getContext().getStore().get(FILTER_KEY) : new ArrayList<>();
                for (Predicate filter : filters) {
                    targetStream = targetStream.filter(filter);
                }

                List<Function> converters = getContext().getStore().containsKey(CONVERTER_KEY) ?
                        (List<Function>) getContext().getStore().get(CONVERTER_KEY) : new ArrayList<>();
                for (Function converter : converters) {
                    targetStream = targetStream.map(converter);
                }

                Object target = targetStream.collect(Collectors.toList());
                return method.invoke(target, args);
            } else {
                Object target = targetProvider.get();
                return method.invoke(target, args);
            }
        }).get();
    }

    @SuppressWarnings({"unchecked", "unused"})
    private Object invokeShouldMethod(Object proxy, Method method, Object[] args) throws Throwable {
        Matcher matcher = (Matcher) args[0];
        return ((SlowLoadableComponent<Object>) () -> {
            assertThat(proxy, matcher);
            return proxy;
        }).get();
    }

    @SuppressWarnings({"unchecked", "unused"})
    private Object invokeWaitUntilMethod(Object proxy, Method method, Object[] args) throws Throwable {
        Predicate predicate = (Predicate) args[0];
        return ((SlowLoadableComponent<Object>) () -> {
            if (predicate.test(proxy)) {
                return proxy;
            }
            throw new NoSuchElementException("No such element exception");
        }).get();
    }

    @SuppressWarnings({"unchecked", "unused"})
    private Object invokeFilterMethod(Object proxy, Method method, Object[] args) throws Throwable {
        Map<String, Object> store = getContext().getStore();
        List<Predicate> predicates = store.containsKey(FILTER_KEY) ?
                (List<Predicate>) store.get(FILTER_KEY) : new ArrayList<>();
        predicates.add((Predicate) args[0]);
        store.put(FILTER_KEY, predicates);
        return proxy;
    }

    @SuppressWarnings({"unchecked", "unused"})
    private Object invokeConvertMethod(Object proxy, Method method, Object[] args) throws Throwable {
        Map<String, Object> store = getContext().getStore();
        List<Function> matchers = store.containsKey(CONVERTER_KEY) ?
                (List<Function>) store.get(CONVERTER_KEY) : new ArrayList<>();
        matchers.add((Function) args[0]);
        store.put(CONVERTER_KEY, matchers);
        return proxy;
    }

    private Object createProxy(Class<?> proxyClass, Context context,
                               Supplier supplier, Class... targetClass) {
        return Proxies.simpleProxy(
                proxyClass, new WebBlockMethodHandler(context, supplier, targetClass)
        );
    }

}
