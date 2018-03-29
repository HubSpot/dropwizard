package com.yammer.dropwizard.jetty.tests;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.UserIdentity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.google.common.net.HttpHeaders;
import com.yammer.dropwizard.jetty.AsyncRequestLog;
import com.yammer.metrics.core.Clock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.spi.AppenderAttachableImpl;

public class AsyncRequestLogTest {
    private final Clock clock = mock(Clock.class);
    @SuppressWarnings("unchecked")
    private final Appender<ILoggingEvent> appender = mock(Appender.class);
    private final AppenderAttachableImpl<ILoggingEvent> appenders = new AppenderAttachableImpl<ILoggingEvent>();
    private final AsyncRequestLog asyncRequestLog = new AsyncRequestLog(clock, appenders, TimeZone.getTimeZone("UTC"));

    private final Request request = mock(Request.class);
    private final Response response = mock(Response.class);

    @Before
    public void setUp() throws Exception {

        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getTimeStamp()).thenReturn(TimeUnit.SECONDS.toMillis(1353042047));
        when(request.getMethod()).thenReturn("GET");
        when(request.getHttpURI()).thenReturn(new HttpURI("/test/things?yay"));
        when(request.getProtocol()).thenReturn("HTTP/1.1");

        when(response.getStatus()).thenReturn(200);
        when(response.getContentCount()).thenReturn(8290L);

        when(clock.time()).thenReturn(TimeUnit.SECONDS.toMillis(1353042049));

        appenders.addAppender(appender);

        asyncRequestLog.start();
    }

    @After
    public void tearDown() throws Exception {
        if (asyncRequestLog.isRunning()) {
            asyncRequestLog.stop();
        }
    }

    @Test
    public void startsAndStops() throws Exception {
        asyncRequestLog.stop();

        verify(appender, timeout(1000)).stop();
    }

    @Test
    public void logsRequests() throws Exception {
        final ILoggingEvent event = logAndCapture();

        assertThat(event.getFormattedMessage())
                .isEqualTo("10.0.0.1 - - [16/Nov/2012:05:00:47 +0000] \"GET /test/things?yay HTTP/1.1\" 200 8290 1000 2000");

        assertThat(event.getLevel())
                .isEqualTo(Level.INFO);
    }

    @Test
    public void logsForwardedFor() throws Exception {
        when(request.getHeader(HttpHeaders.X_FORWARDED_FOR)).thenReturn("123.123.123.123");

        final ILoggingEvent event = logAndCapture();
        assertThat(event.getFormattedMessage())
                .isEqualTo("123.123.123.123 - - [16/Nov/2012:05:00:47 +0000] \"GET /test/things?yay HTTP/1.1\" 200 8290 1000 2000");
    }

    @Test
    public void logsPrincipal() throws Exception {
        final Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("coda");

        final UserIdentity identity = mock(UserIdentity.class);
        when(identity.getUserPrincipal()).thenReturn(principal);

        final Authentication.User user = mock(Authentication.User.class);
        when(user.getUserIdentity()).thenReturn(identity);

        when(request.getAuthentication()).thenReturn(user);

        final ILoggingEvent event = logAndCapture();
        assertThat(event.getFormattedMessage())
                .isEqualTo("10.0.0.1 - coda [16/Nov/2012:05:00:47 +0000] \"GET /test/things?yay HTTP/1.1\" 200 8290 1000 2000");
    }

    private ILoggingEvent logAndCapture() {
        asyncRequestLog.log(request, response);

        final ArgumentCaptor<ILoggingEvent> captor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(appender, timeout(1000)).doAppend(captor.capture());

        return captor.getValue();
    }
}
