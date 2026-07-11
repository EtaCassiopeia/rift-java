package io.github.etacassiopeia.rift.spring;

import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

/**
 * Injects {@link InjectRift}/{@link InjectImposter} fields and drives {@link Reset} between test
 * methods/classes, using the {@link RiftTestContext} bean {@link RiftContextCustomizer} registers.
 * Contexts without {@code @EnableRift} have no such bean and are left untouched.
 */
public final class RiftTestExecutionListener extends AbstractTestExecutionListener {

    @Override
    public int getOrder() {
        return 5000;
    }

    @Override
    public void prepareTestInstance(TestContext testContext) {
        RiftTestContext riftTestContext = riftTestContext(testContext);
        if (riftTestContext == null) {
            return;
        }
        Object testInstance = testContext.getTestInstance();
        ReflectionUtils.doWithFields(testContext.getTestClass(), field -> injectField(field, testInstance, riftTestContext));
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        RiftTestContext riftTestContext = riftTestContext(testContext);
        if (riftTestContext != null && riftTestContext.reset() == Reset.PER_TEST) {
            riftTestContext.resetConfiguredImposters();
        }
    }

    /**
     * Reads {@code @EnableRift.reset()} straight off the test class, rather than through the {@link
     * RiftTestContext} bean, so a class not configured for {@link Reset#PER_CLASS} never forces the
     * application context to load this early — before {@code RiftSpringTestBase}-style static
     * initializers (e.g. publishing an admin URI system property) have had a chance to run. When the
     * class genuinely is {@link Reset#PER_CLASS}, the context load this triggers is still best-effort:
     * a failure here is swallowed so it doesn't fail the class before a single test runs.
     */
    @Override
    public void beforeTestClass(TestContext testContext) {
        EnableRift enableRift = AnnotatedElementUtils.findMergedAnnotation(testContext.getTestClass(), EnableRift.class);
        if (enableRift == null || enableRift.reset() != Reset.PER_CLASS) {
            return;
        }
        RiftTestContext riftTestContext;
        try {
            riftTestContext = riftTestContext(testContext);
        } catch (IllegalStateException e) {
            // Best-effort: only the context load may legitimately race the test lifecycle here. A
            // reset failure (below) is a real fault and must propagate rather than be swallowed.
            return;
        }
        if (riftTestContext != null) {
            riftTestContext.resetConfiguredImposters();
        }
    }

    private static void injectField(Field field, Object testInstance, RiftTestContext riftTestContext) {
        InjectRift injectRift = field.getAnnotation(InjectRift.class);
        InjectImposter injectImposter = field.getAnnotation(InjectImposter.class);
        if (injectRift == null && injectImposter == null) {
            return;
        }
        ReflectionUtils.makeAccessible(field);
        Object value = injectImposter != null ? riftTestContext.imposter(injectImposter.value()) : riftTestContext.rift();
        ReflectionUtils.setField(field, testInstance, value);
    }

    private static RiftTestContext riftTestContext(TestContext testContext) {
        ApplicationContext applicationContext = testContext.getApplicationContext();
        if (applicationContext.getBeanNamesForType(RiftTestContext.class).length == 0) {
            return null;
        }
        return applicationContext.getBean(RiftTestContext.class);
    }
}
