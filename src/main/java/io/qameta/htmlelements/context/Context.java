package io.qameta.htmlelements.context;

import io.qameta.htmlelements.extension.ExtensionRegistry;
import org.openqa.selenium.WebDriver;

import java.util.Optional;

public class Context {

    private Context parent;

    private Store store;

    private ExtensionRegistry registry;

    private Context() {
        this.store = new DefaultStore();
    }

    public Optional<Context> getParent() {
        return Optional.ofNullable(parent);
    }

    private void setParent(Context parent) {
        this.parent = parent;
    }

    public Store getStore() {
        return store;
    }

    public ExtensionRegistry getRegistry() {
        return registry;
    }

    private void setRegistry(ExtensionRegistry registry) {
        this.registry = registry;
    }

    public Context newChildContext(Class<?> proxyClass) {
        Context childContext = new Context();
        childContext.setRegistry(ExtensionRegistry.create(proxyClass));
        childContext.setParent(this);
        //extension
        getStore().get("driver", WebDriver.class).ifPresent(driver -> {
            childContext.getStore().put("driver", driver);
        });
        return childContext;
    }

    public static Context newWebPageContext(Class<?> webPageClass, WebDriver driver) {
        Context context = new Context();
        context.setRegistry(ExtensionRegistry.create(webPageClass));
        context.getStore().put("driver", driver);
        return context;
    }


}
