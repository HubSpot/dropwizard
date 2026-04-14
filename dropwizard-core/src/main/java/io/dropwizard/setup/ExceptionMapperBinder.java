package io.dropwizard.setup;

import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.jersey.errors.EofExceptionWriterInterceptor;
import io.dropwizard.jersey.errors.IllegalStateExceptionMapper;
import io.dropwizard.jersey.errors.LoggingExceptionMapper;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.jersey.optional.EmptyOptionalExceptionMapper;
import io.dropwizard.jersey.validation.JerseyViolationExceptionMapper;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.WriterInterceptor;

/**
 * An HK2 binder that registers all the default exception mappers while allowing users to override
 * individual exception mappers without disabling all others.
 */
public class ExceptionMapperBinder extends AbstractBinder {
    private final boolean showDetails;
    private final MetricRegistry metricRegistry;

    public ExceptionMapperBinder(boolean showDetails, MetricRegistry metricRegistry) {
        this.showDetails = showDetails;
        this.metricRegistry = metricRegistry;
    }

    @Override
    protected void configure() {
        bind(new LoggingExceptionMapper<Throwable>() {
        }).to(ExceptionMapper.class);
        bind(new JerseyViolationExceptionMapper()).to(ExceptionMapper.class);
        bind(new JsonProcessingExceptionMapper(isShowDetails())).to(ExceptionMapper.class);
        bind(new EarlyEofExceptionMapper()).to(ExceptionMapper.class);
        bind(new EmptyOptionalExceptionMapper()).to(ExceptionMapper.class);
        bind(new IllegalStateExceptionMapper()).to(ExceptionMapper.class);
        bind(new EofExceptionWriterInterceptor(metricRegistry)).to(WriterInterceptor.class);
    }

    public boolean isShowDetails() {
        return showDetails;
    }
}
