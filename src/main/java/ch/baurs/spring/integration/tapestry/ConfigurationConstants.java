package ch.baurs.spring.integration.tapestry;

public class ConfigurationConstants
{
    public static final String SPRING_CONTEXT_PATH = "server.servlet.context-path";
    /**
     * The name of the Tapestry filter determines the name of the application module class to
     * search for. Usually, this is set in the {@code web.xml}. Defaults to {@code app}.
     */
    public static final String TAPESTRY_FILTER_NAME = "spring.tapestry.filter-name";

    public static final String TAPESTRY_FILTER_NAME_DEFAULT_VALUE = "app";
}
