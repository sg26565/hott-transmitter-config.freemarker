package freemarker.ext.beans;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

/**
 * @author oli@treichels.de
 */
public abstract class BeansIntrospector {
    private static final String FREEMARKER_EXT_BEANS_ANDROID_BEANS_INTROSPECTOR = "freemarker.ext.beans.AndroidBeansIntrospector";
    private static final String FREEMARKER_EXT_BEANS_JAVA_BEANS_INTROSPECTOR = "freemarker.ext.beans.JavaBeansIntrospector";
    private static final String JAVA_BEANS_INTROSPECTOR = "java.beans.Introspector";
    private static final BeansIntrospector INSTANCE;

    static {
        String className;

        try {
            // check for standard JVM
            Class.forName(JAVA_BEANS_INTROSPECTOR);

            // use standard JVM introspector
            className = FREEMARKER_EXT_BEANS_JAVA_BEANS_INTROSPECTOR;
        } catch (final Throwable t) {
            // use Android compativle introspector
            className = FREEMARKER_EXT_BEANS_ANDROID_BEANS_INTROSPECTOR;
        }

        try {
            final Class introspectorClass = Class.forName(className);
            INSTANCE = (BeansIntrospector) introspectorClass.newInstance();
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (final SecurityException e) {
            throw new RuntimeException(e);
        } catch (final InstantiationException e) {
            throw new RuntimeException(e);
        } catch (final IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static BeansIntrospector getInstance() {
        return INSTANCE;
    }

    public abstract void addBeanInfoToClassInrospectionData(final BeansWrapper wrapper, final Map introspData,
            final Class clazz, final Map accessibleMethods) throws Exception;

    public abstract TemplateModel invokeThroughDescriptor(final BeanModel model, final Object desc, final Map classInfo)
            throws IllegalAccessException, InvocationTargetException, TemplateModelException;
}