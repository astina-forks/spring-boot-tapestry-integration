package ch.baurs.spring.integration.tapestry;

import org.apache.tapestry5.SymbolConstants;
import org.apache.tapestry5.http.internal.SingleKeySymbolProvider;
import org.apache.tapestry5.http.internal.TapestryAppInitializer;
import org.apache.tapestry5.http.internal.TapestryHttpInternalConstants;
import org.apache.tapestry5.http.internal.util.DelegatingSymbolProvider;
import org.apache.tapestry5.ioc.Registry;
import org.apache.tapestry5.ioc.internal.services.SystemPropertiesSymbolProvider;
import org.apache.tapestry5.ioc.services.ServiceActivityScoreboard;
import org.apache.tapestry5.ioc.services.SymbolProvider;
import org.apache.tapestry5.modules.TapestryModule;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * Post-processor that orchestrates the whole integration
 */
public class TapestryBeanFactoryPostProcessor implements BeanFactoryPostProcessor, Ordered {

    protected final AnnotationConfigServletWebServerApplicationContext applicationContext;

    private Registry registry = null;
    private TapestryAppInitializer appInitializer = null;

    public TapestryBeanFactoryPostProcessor(AnnotationConfigServletWebServerApplicationContext applicationContext) {
        super();
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        String filterName = applicationContext.getEnvironment().getProperty(ConfigurationConstants.TAPESTRY_FILTER_NAME, ConfigurationConstants.TAPESTRY_FILTER_NAME_DEFAULT_VALUE);
        SymbolProvider combinedProvider = setupTapestryContext();
        String executionMode = combinedProvider.valueForSymbol(SymbolConstants.EXECUTION_MODE);
        String appModule = findAppModuleClass(filterName, combinedProvider);
        LogHelper.info("TB: About to start Tapestry app {}, executionMode: {} ", appModule, executionMode);
        appInitializer = new TapestryAppInitializer(LogHelper.LOG, combinedProvider, filterName, executionMode);
        appInitializer.addModules(new SpringModuleDef(applicationContext));
        appInitializer.addModules(TapestryModule.class);
        LogHelper.info("TB: creating tapestry registry");
        registry = appInitializer.createRegistry();

        beanFactory.addBeanPostProcessor(new TapestryFilterPostProcessor());

        registerTapestryServices(applicationContext.getBeanFactory(),
                servicesPackage(combinedProvider.valueForSymbol(TapestryHttpInternalConstants.TAPESTRY_APP_PACKAGE_PARAM)),
                registry);

        // This will scan and find TapestryFilter which in turn will be post
        // processed be TapestryFilterPostProcessor completing tapestry initialisation
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(applicationContext);
        scanner.scan(TapestryBeanFactoryPostProcessor.class.getPackage().getName());

    }

    private class TapestryFilterPostProcessor implements BeanPostProcessor {

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean.getClass() == TapestryFilter.class) {
                LogHelper.info("TB: About to start TapestryFilter, begin Registry initialization");
                registry.performRegistryStartup();
                registry.cleanupThread();
                appInitializer.announceStartup();
                LogHelper.info("TB: About to start TapestryFilter, Registry initialization complete");
            }
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            return bean;
        }

    }

    protected SymbolProvider setupTapestryContext() {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();

        //read contextPath from two possible properties
        String servletContextPath = environment.getProperty(SymbolConstants.CONTEXT_PATH, environment.getProperty(ConfigurationConstants.SPRING_CONTEXT_PATH, ""));

        return new DelegatingSymbolProvider(
                new SystemPropertiesSymbolProvider(),
                new SingleKeySymbolProvider(SymbolConstants.CONTEXT_PATH, servletContextPath),
                new ApplicationContextSymbolProvider(applicationContext),
                new SingleKeySymbolProvider(SymbolConstants.EXECUTION_MODE, "production")
        );
    }

    protected String findAppModuleClass(String filterName, SymbolProvider combinedProvider) {
        String appPackage = combinedProvider.valueForSymbol(TapestryHttpInternalConstants.TAPESTRY_APP_PACKAGE_PARAM);

        /* From the Tapestry docs:
         *
         * > Tapestry looks for your application module class in the services package (under the root package) of your
         * > application. It capitalizes the <filter-name> and appends "Module". In the previous example, because the
         * > filter name was "app" and the application's root package name is "org.example.myapp", the module class
         * > would be org.example.myapp.services.AppModule.
         *
         * > If such a class exists, it is added to the IoC Registry. It is not an error for your application to not
         * > have a module class, though any non-trivial application will have one.
         *
         * We're assuming that we have a non-trivial application!
         */
        String appModuleClassName = servicesPackage(appPackage) + "." + StringUtils.capitalize(filterName) + "Module";

        try {
            Class.forName(appModuleClassName);
        } catch (ClassNotFoundException e) {
            String message = String.format("Tapestry application module not found. Check the properties '%s' ('%s') and '%s' ('%s') in your environment (e.g. application.properties)", TapestryHttpInternalConstants.TAPESTRY_APP_PACKAGE_PARAM, appPackage, ConfigurationConstants.TAPESTRY_FILTER_NAME, filterName);
            throw new IllegalStateException(message);
        }
        LogHelper.info("Found Tapestry AppModule class: {} ", appModuleClassName);

        return appModuleClassName;
    }

    protected String servicesPackage(String appPackage) {
        return (StringUtils.hasText(appPackage) ? appPackage + "." : "") + "services";
    }

    protected void registerTapestryServices(ConfigurableListableBeanFactory beanFactory, String servicesPackage,
                                            Registry registry) {
        ServiceActivityScoreboard scoreboard = registry.getService(ServiceActivityScoreboard.class);
        scoreboard.getServiceActivity().forEach(service -> {
            if (service.getServiceInterface().getPackage().getName().startsWith(servicesPackage)
                    || !service.getMarkers().isEmpty() || service.getServiceInterface().getName().contains("tapestry5")) {
                Object proxy = registry.getService(service.getServiceId(), (Class<?>) service.getServiceInterface());
                beanFactory.registerResolvableDependency(service.getServiceInterface(), proxy);
                LogHelper.debug("TB: tapestry service {} exposed to spring", service.getServiceId());
            }
        });
        beanFactory.registerResolvableDependency(Registry.class, registry);
        LogHelper.info("TB: tapestry Registry registered with spring (Still pending initialization)");
    }

    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static String defaultString(final String str) {
        return defaultString(str, "");
    }

    private static String defaultString(final String str, final String defaultStr) {
        return str == null ? defaultStr : str;
    }

}
