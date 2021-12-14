/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.config.spring.beans.factory.annotation;

import org.apache.dubbo.config.AbstractInterfaceConfig;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ModuleConfig;
import org.apache.dubbo.config.MonitorConfig;
import org.apache.dubbo.config.RegistryConfig;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.util.Assert;

import java.lang.annotation.Annotation;
import java.util.List;

import static org.apache.dubbo.config.spring.util.BeanFactoryUtils.getBeans;
import static org.apache.dubbo.config.spring.util.BeanFactoryUtils.getOptionalBean;

/**
 * An Abstract Builder to build {@link AbstractInterfaceConfig Interface Config} Bean that annotated
 * some {@link Annotation annotation}.
 *
 * @see ReferenceBeanBuilder
 * @see AbstractInterfaceConfig
 * @see AnnotationAttributes
 * @since 2.7.3
 */
public abstract class AnnotatedInterfaceConfigBeanBuilder<C extends AbstractInterfaceConfig> {

    protected final Log logger = LogFactory.getLog(getClass());

    protected final AnnotationAttributes attributes;

    protected final ApplicationContext applicationContext;

    protected final ClassLoader classLoader;

    protected Object configBean;

    protected Class<?> interfaceClass;

    protected AnnotatedInterfaceConfigBeanBuilder(AnnotationAttributes attributes, ApplicationContext applicationContext) {
        Assert.notNull(attributes, "The Annotation attributes must not be null!");
        Assert.notNull(applicationContext, "The ApplicationContext must not be null!");
        this.attributes = attributes;
        this.applicationContext = applicationContext;
        this.classLoader = applicationContext.getClassLoader() != null ?
                applicationContext.getClassLoader() : Thread.currentThread().getContextClassLoader();
    }

    /**
     * Build {@link C}
     *
     * @return non-null
     * @throws Exception
     */
    public final C build() throws Exception {
        checkDependencies();
        C configBean = doBuild(); // 创建一个ReferenceBean对象
        configureBean(configBean); // 给ReferenceBean对象的属性赋值
        if (logger.isInfoEnabled()) {
            logger.info("The configBean[type:" + configBean.getClass().getSimpleName() + "] has been built.");
        }
        return configBean;

    }

    private void checkDependencies() {

    }

    /**
     * Builds {@link C Bean}
     *
     * @return {@link C Bean}
     */
    protected abstract C doBuild();


    protected void configureBean(C configBean) throws Exception {
        preConfigureBean(attributes, configBean); // 把@Reference注解中的配置项赋值给configBean
        configureRegistryConfigs(configBean); // 把注册中心配置项赋值给configBean
        configureMonitorConfig(configBean);// 把监控中心配置项赋值给configBean
        configureApplicationConfig(configBean);// 把应用配置项赋值给configBean
        configureModuleConfig(configBean);
        // 设置applicationContext、interfaceName、consumer、methods属性，并调用ReferenceBean对象的afterPropertiesSet方法
        postConfigureBean(attributes, configBean);

    }

    protected abstract void preConfigureBean(AnnotationAttributes attributes, C configBean) throws Exception;


    private void configureRegistryConfigs(C configBean) {
        String[] registryConfigBeanIds = resolveRegistryConfigBeanNames(attributes); // 解析@Refrence注解中配置的registry属性
        // 获得注册中心对应的RegistryConfig对象
        List<RegistryConfig> registryConfigs = getBeans(applicationContext, registryConfigBeanIds, RegistryConfig.class);
        configBean.setRegistries(registryConfigs); // 设置registryConfigs属性值

    }

    private void configureMonitorConfig(C configBean) {
        String monitorBeanName = resolveMonitorConfigBeanName(attributes);
        // 从Spring容器获取MonitorConfig的bean对象
        MonitorConfig monitorConfig = getOptionalBean(applicationContext, monitorBeanName, MonitorConfig.class);
        configBean.setMonitor(monitorConfig);

    }

    private void configureApplicationConfig(C configBean) {
        String applicationConfigBeanName = resolveApplicationConfigBeanName(attributes);
        ApplicationConfig applicationConfig = getOptionalBean(applicationContext, applicationConfigBeanName, ApplicationConfig.class);
        configBean.setApplication(applicationConfig);
    }

    private void configureModuleConfig(C configBean) {
        String moduleConfigBeanName = resolveModuleConfigBeanName(attributes);
        ModuleConfig moduleConfig = getOptionalBean(applicationContext, moduleConfigBeanName, ModuleConfig.class);
        configBean.setModule(moduleConfig);
    }

    /**
     * Resolves the configBean name of {@link ModuleConfig}
     *
     * @param attributes {@link AnnotationAttributes}
     * @return
     */
    protected abstract String resolveModuleConfigBeanName(AnnotationAttributes attributes);

    /**
     * Resolves the configBean name of {@link ApplicationConfig}
     *
     * @param attributes {@link AnnotationAttributes}
     * @return
     */
    protected abstract String resolveApplicationConfigBeanName(AnnotationAttributes attributes);


    /**
     * Resolves the configBean ids of {@link RegistryConfig}
     *
     * @param attributes {@link AnnotationAttributes}
     * @return non-empty array
     */
    protected abstract String[] resolveRegistryConfigBeanNames(AnnotationAttributes attributes);

    /**
     * Resolves the configBean name of {@link MonitorConfig}
     *
     * @param attributes {@link AnnotationAttributes}
     * @return
     */
    protected abstract String resolveMonitorConfigBeanName(AnnotationAttributes attributes);

    /**
     * Configures Bean
     *
     * @param attributes
     * @param configBean
     */
    protected abstract void postConfigureBean(AnnotationAttributes attributes, C configBean) throws Exception;


    public <T extends AnnotatedInterfaceConfigBeanBuilder<C>> T configBean(Object configBean) {
        this.configBean = configBean;
        return (T) this;
    }

    public <T extends AnnotatedInterfaceConfigBeanBuilder<C>> T interfaceClass(Class<?> interfaceClass) {
        this.interfaceClass = interfaceClass;
        return (T) this;
    }
}
