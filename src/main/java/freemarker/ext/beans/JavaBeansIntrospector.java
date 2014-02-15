package freemarker.ext.beans;

import java.beans.BeanInfo;
import java.beans.IndexedPropertyDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

/**
 * @author oli@treichels.de
 */
public class JavaBeansIntrospector extends BeansIntrospector {
    public void addBeanInfoToClassInrospectionData(final BeansWrapper wrapper, final Map introspData,
            final Class clazz, final Map accessibleMethods) throws Exception {
        final BeanInfo beanInfo = Introspector.getBeanInfo(clazz);

        final PropertyDescriptor[] pda = beanInfo.getPropertyDescriptors();
        final int pdaLength = pda != null ? pda.length : 0;
        for (int i = pdaLength - 1; i >= 0; --i) {
            addPropertyDescriptorToClassIntrospectionData(wrapper, pda[i], clazz, accessibleMethods, introspData);
        }

        if (wrapper.getExposureLevel() < BeansWrapper.EXPOSE_PROPERTIES_ONLY) {
            final MethodDescriptor[] mda = beanInfo.getMethodDescriptors();
            final int mdaLength = mda != null ? mda.length : 0;
            for (int i = mdaLength - 1; i >= 0; --i) {
                final MethodDescriptor md = mda[i];
                final Method publicMethod = BeansWrapper.getAccessibleMethod(md.getMethod(), accessibleMethods);
                if (publicMethod != null && wrapper.isSafeMethod(publicMethod)) {

                    final String methodKey = publicMethod.getName();
                    if (methodKey != null) {
                        final Object previous = introspData.get(methodKey);
                        if (previous instanceof Method) {
                            // Overloaded method - replace method with a method
                            // map
                            final OverloadedMethods overloadedMethods = new OverloadedMethods(wrapper);
                            overloadedMethods.addMethod((Method) previous);
                            overloadedMethods.addMethod(publicMethod);
                            introspData.put(methodKey, overloadedMethods);
                            // remove parameter type information
                            BeansWrapper.getArgTypes(introspData).remove(previous);
                        } else if (previous instanceof OverloadedMethods) {
                            // Already overloaded method - add new overload
                            ((OverloadedMethods) previous).addMethod(publicMethod);
                        } else {
                            // Simple method (this far)
                            introspData.put(methodKey, publicMethod);
                            BeansWrapper.getArgTypes(introspData).put(publicMethod, publicMethod.getParameterTypes());
                        }
                    }
                }
            }
        } // end if(exposureLevel < EXPOSE_PROPERTIES_ONLY)
    }

    private void addPropertyDescriptorToClassIntrospectionData(final BeansWrapper wrapper, PropertyDescriptor pd,
            final Class clazz, final Map accessibleMethods, final Map classMap) {
        if (pd instanceof IndexedPropertyDescriptor) {
            IndexedPropertyDescriptor ipd = (IndexedPropertyDescriptor) pd;
            final Method readMethod = ipd.getIndexedReadMethod();
            final Method publicReadMethod = BeansWrapper.getAccessibleMethod(readMethod, accessibleMethods);
            if (publicReadMethod != null && wrapper.isSafeMethod(publicReadMethod)) {
                try {
                    if (readMethod != publicReadMethod) {
                        ipd = new IndexedPropertyDescriptor(ipd.getName(), ipd.getReadMethod(), null, publicReadMethod,
                                null);
                    }
                    classMap.put(ipd.getName(), ipd);
                    BeansWrapper.getArgTypes(classMap).put(publicReadMethod, publicReadMethod.getParameterTypes());
                } catch (final IntrospectionException e) {
                    BeansWrapper.logger.warn("Failed creating a publicly-accessible " + "property descriptor for "
                            + clazz.getName() + " indexed property " + pd.getName() + ", read method "
                            + publicReadMethod, e);
                }
            }
        } else {
            final Method readMethod = pd.getReadMethod();
            final Method publicReadMethod = BeansWrapper.getAccessibleMethod(readMethod, accessibleMethods);
            if (publicReadMethod != null && wrapper.isSafeMethod(publicReadMethod)) {
                try {
                    if (readMethod != publicReadMethod) {
                        pd = new PropertyDescriptor(pd.getName(), publicReadMethod, null);
                        pd.setReadMethod(publicReadMethod);
                    }
                    classMap.put(pd.getName(), pd);
                } catch (final IntrospectionException e) {
                    BeansWrapper.logger.warn("Failed creating a publicly-accessible " + "property descriptor for "
                            + clazz.getName() + " property " + pd.getName() + ", read method " + publicReadMethod, e);
                }
            }
        }
    }

    public TemplateModel invokeThroughDescriptor(final BeanModel model, final Object desc, final Map classInfo)
            throws IllegalAccessException, InvocationTargetException, TemplateModelException {
        // See if this particular instance has a cached implementation
        // for the requested feature descriptor
        TemplateModel member;
        synchronized (this) {
            if (model.memberMap != null) {
                member = (TemplateModel) model.memberMap.get(desc);
            } else {
                member = null;
            }
        }

        if (member != null) {
            return member;
        }

        TemplateModel retval = BeanModel.UNKNOWN;
        if (desc instanceof IndexedPropertyDescriptor) {
            final Method readMethod = ((IndexedPropertyDescriptor) desc).getIndexedReadMethod();
            retval = member = new SimpleMethodModel(model.object, readMethod, BeansWrapper.getArgTypes(classInfo,
                    readMethod), model.wrapper);
        } else if (desc instanceof PropertyDescriptor) {
            final PropertyDescriptor pd = (PropertyDescriptor) desc;
            retval = model.wrapper.invokeMethod(model.object, pd.getReadMethod(), null);
            // (member == null) condition remains, as we don't cache these
        } else if (desc instanceof Field) {
            retval = model.wrapper.wrap(((Field) desc).get(model.object));
            // (member == null) condition remains, as we don't cache these
        } else if (desc instanceof Method) {
            final Method method = (Method) desc;
            retval = member = new SimpleMethodModel(model.object, method, BeansWrapper.getArgTypes(classInfo, method),
                    model.wrapper);
        } else if (desc instanceof OverloadedMethods) {
            retval = member = new OverloadedMethodsModel(model.object, (OverloadedMethods) desc);
        }

        // If new cacheable member was created, cache it
        if (member != null) {
            synchronized (this) {
                if (model.memberMap == null) {
                    model.memberMap = new HashMap();
                }
                model.memberMap.put(desc, member);
            }
        }
        return retval;
    }
}