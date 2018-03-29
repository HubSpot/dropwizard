package com.yammer.dropwizard.config;

import java.util.EnumSet;
import java.util.EventListener;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.sun.jersey.spi.container.servlet.ServletContainer;
import com.yammer.dropwizard.jersey.JacksonMessageBodyProvider;
import com.yammer.dropwizard.jetty.ContextRoutingHandler;
import com.yammer.dropwizard.jetty.InstrumentedHandler;
import com.yammer.dropwizard.jetty.InstrumentedQueuedThreadPool;
import com.yammer.dropwizard.jetty.RoutingHandler;
import com.yammer.dropwizard.jetty.UnbrandedErrorHandler;
import com.yammer.dropwizard.servlets.ThreadNameFilter;
import com.yammer.dropwizard.tasks.TaskServlet;
import com.yammer.dropwizard.util.Duration;
import com.yammer.metrics.HealthChecks;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.HealthCheck;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.reporting.AdminServlet;
import com.yammer.metrics.util.DeadlockHealthCheck;

/*
 * A factory for creating instances of {@link org.eclipse.jetty.server.Server} and configuring Servlets
 * 
 * Registers {@link com.yammer.metrics.core.HealthCheck}s, both default and user defined
 * 
 * Creates instances of {@link org.eclipse.jetty.server.Connector},
 * configured by {@link com.yammer.dropwizard.config.HttpConfiguration} for external and admin port
 * 
 * Registers {@link org.eclipse.jetty.server.Handler}s for admin and service Servlets.
 * {@link TaskServlet} 
 * {@link AdminServlet}
 * {@link com.sun.jersey.spi.container.servlet.ServletContainer} with all resources in {@link DropwizardResourceConfig} 
 * 
 * */
public class ServerFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerFactory.class);

    private final HttpConfiguration config;
    private final RequestLogHandlerFactory requestLogHandlerFactory;

    public ServerFactory(HttpConfiguration config, String name) {
        this.config = config;
        this.requestLogHandlerFactory = new RequestLogHandlerFactory(config.getRequestLogConfiguration(),
                                                                     name);
    }

    public Server buildServer(Environment env) throws ConfigurationException {
        HealthChecks.defaultRegistry().register(new DeadlockHealthCheck());
        for (HealthCheck healthCheck : env.getHealthChecks()) {
            HealthChecks.defaultRegistry().register(healthCheck);
        }

        if (env.getHealthChecks().isEmpty()) {
            LOGGER.warn('\n' +
                             "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
                             "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
                             "!    THIS SERVICE HAS NO HEALTHCHECKS. THIS MEANS YOU WILL NEVER KNOW IF IT    !\n" +
                             "!    DIES IN PRODUCTION, WHICH MEANS YOU WILL NEVER KNOW IF YOU'RE LETTING     !\n" +
                             "!     YOUR USERS DOWN. YOU SHOULD ADD A HEALTHCHECK FOR EACH DEPENDENCY OF     !\n" +
                             "!     YOUR SERVICE WHICH FULLY (BUT LIGHTLY) TESTS YOUR SERVICE'S ABILITY TO   !\n" +
                             "!      USE THAT SERVICE. THINK OF IT AS A CONTINUOUS INTEGRATION TEST.         !\n" +
                             "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
                             "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
            );
        }

        final Server server = createServer();
        server.setHandler(createHandler(server, env));
        server.addBean(env);
        return server;
    }

    private Server createServer() {
        final Server server = new Server(createThreadPool());

        ErrorHandler errorHandler = new UnbrandedErrorHandler();
        errorHandler.setServer(server);
        server.addBean(errorHandler);

        server.setStopAtShutdown(true);
        server.setStopTimeout(config.getShutdownGracePeriod().toMilliseconds());

        // TODO add connectors/handlers
        /*
        server.addConnector(createExternalConnector());

        // if we're dynamically allocating ports, no worries if they are the same (i.e. 0)
        if (config.getAdminPort() == 0 || (config.getAdminPort() != config.getPort()) ) {
            server.addConnector(createInternalConnector());
        }
         */

        return server;
    }

    private Handler createHandler(Server server, Environment env) {
        /*
        if (requestLogHandlerFactory.isEnabled()) {
            collection.addHandler(requestLogHandlerFactory.build());
        }
        */

        final Handler applicationHandler = createAppServlet(server, env, Metrics.defaultRegistry());
        final Handler adminHandler = createAdminServlet(server, env, Metrics.defaultRegistry());

        // simple server

        final Connector conn = config.build(server, Metrics.defaultRegistry(), env.getName(), null);

        server.addConnector(conn);

        final ContextRoutingHandler routingHandler = new ContextRoutingHandler(ImmutableMap.of(
            config.getRootPath(), applicationHandler,
            "/admin", adminHandler
        ));

        // default server

        final RoutingHandler routingHandler = buildRoutingHandler(environment.metrics(),
            server,
            applicationHandler,
            adminHandler);

        //

        final Handler gzipHandler = config.getGzipConfiguration().build(routingHandler);
        server.setHandler(addStatsHandler(addRequestLog(server, gzipHandler, env.getName())));

        return server;
    }

    protected Handler createAppServlet(Server server, Environment env, MetricsRegistry metricsRegistry) {
        final ServletContextHandler handler = new ServletContextHandler();

        handler.addFilter(ThreadNameFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        handler.setBaseResource(env.getBaseResource());

        if (!env.getProtectedTargets().isEmpty()) {
            handler.setProtectedTargets(env.getProtectedTargets().toArray(new String[env.getProtectedTargets().size()]));
        }

        for (ImmutableMap.Entry<String, ServletHolder> entry : env.getServlets().entrySet()) {
            handler.addServlet(entry.getValue(), entry.getKey());
        }

        final ServletContainer jerseyContainer = env.getJerseyServletContainer();
        if (jerseyContainer != null) {
            env.addProvider(new JacksonMessageBodyProvider(env.getObjectMapperFactory().build(), env.getValidator()));
            final ServletHolder jerseyHolder = new ServletHolder(jerseyContainer);
            jerseyHolder.setInitOrder(Integer.MAX_VALUE);
            handler.addServlet(jerseyHolder, config.getRootPath());
        }

        for (ImmutableMap.Entry<String, FilterHolder> entry : env.getFilters().entries()) {
            handler.addFilter(entry.getValue(), entry.getKey(), EnumSet.of(DispatcherType.REQUEST));
        }

        for (EventListener listener : env.getServletListeners()) {
            handler.addEventListener(listener);
        }

        for (Map.Entry<String, String> entry : config.getContextParameters().entrySet()) {
            handler.setInitParameter( entry.getKey(), entry.getValue() );
        }

        handler.setSessionHandler(env.getSessionHandler());

        final InstrumentedHandler instrumented = new InstrumentedHandler(metricsRegistry);
        instrumented.setServer(server);
        instrumented.setHandler(handler);
        return instrumented;
    }

    protected Handler createAdminServlet(Server server, Environment env, MetricsRegistry metrics) {
        final ServletContextHandler handler = new ServletContextHandler();
        handler.setServer(server);

        handler.addServlet(new ServletHolder(new TaskServlet(env.getTasks())), "/tasks/*");
        handler.addServlet(new ServletHolder(new AdminServlet()), "/*");

        if (config.getAdminPort() != 0 && config.getAdminPort() == config.getPort()) {
            handler.setContextPath("/admin");
            handler.setConnectorNames(new String[]{"main"});
        } else {
            handler.setConnectorNames(new String[]{"internal"});
        }

        if (config.getAdminUsername().isPresent() || config.getAdminPassword().isPresent()) {
            handler.setSecurityHandler(basicAuthHandler(config.getAdminUsername().or(""), config.getAdminPassword().or("")));
        }

        return handler;
    }

    private SecurityHandler basicAuthHandler(String username, String password) {
        final HashLoginService loginService = new HashLoginService();
        loginService.putUser(username, Credential.getCredential(password), new String[] {"user"});
        loginService.setName("admin");

        final Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"user"});
        constraint.setAuthenticate(true);

        final ConstraintMapping constraintMapping = new ConstraintMapping();
        constraintMapping.setConstraint(constraint);
        constraintMapping.setPathSpec("/*");

        final ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName("admin");
        csh.addConstraintMapping(constraintMapping);
        csh.setLoginService(loginService);

        return csh;
    }

    private ThreadPool createThreadPool() {
        int minThreads = config.getMinThreads();
        int maxThreads = config.getMaxThreads();
        int maxQueuedRequests = config.getAcceptQueueSize();
        final BlockingQueue<Runnable> queue = new BlockingArrayQueue<Runnable>(
            minThreads,
            maxThreads,
            maxQueuedRequests
        );
        final InstrumentedQueuedThreadPool threadPool = new InstrumentedQueuedThreadPool(
            Metrics.defaultRegistry(),
            maxThreads,
            minThreads,
            (int) Duration.minutes(1).toMilliseconds(),
            queue
        );
        threadPool.setName("dw");
        return threadPool;
    }

    private Connector createInternalConnector() {
        final SocketConnector connector = new SocketConnector();
        connector.setHost(config.getBindHost().orNull());
        connector.setPort(config.getAdminPort());
        connector.setName("internal");
        connector.setThreadPool(new QueuedThreadPool(8));
        return connector;
    }
}
