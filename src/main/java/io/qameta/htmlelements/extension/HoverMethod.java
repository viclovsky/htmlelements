package io.qameta.htmlelements.extension;

import com.google.common.base.Predicate;
import io.qameta.htmlelements.context.Context;
import io.qameta.htmlelements.exception.WebPageException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import static io.qameta.htmlelements.context.Store.DRIVER_KEY;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@HandleWith(HoverMethod.Extension.class)
public @interface HoverMethod {

    class Extension implements MethodHandler {

        @Override
        public Object handle(Context context, Object proxy, Method method, Object[] args) throws Throwable {
            WebDriver driver = context.getStore().get(DRIVER_KEY, WebDriver.class)
                    .orElseThrow(() -> new WebPageException("WebDriver is missing"));

            try {
                new WebDriverWait(driver, 5)
                        .ignoring(AssertionError.class)
                        .until((Predicate<WebDriver>) (d) -> {
                            Actions actions = new Actions(driver);
                            actions.moveToElement((WebElement) proxy).perform();
                            return true;
                        });
            } catch (TimeoutException e) {
                throw e.getCause();
            }
            return proxy;
        }
    }

}
