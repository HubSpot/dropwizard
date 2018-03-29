package com.yammer.dropwizard.jetty;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.AsyncContextState;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpChannelState;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;

import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.core.Timer;
import com.yammer.metrics.util.RatioGauge;

/**
 * A Jetty {@link Handler} which records various metrics about an underlying {@link Handler}
 * instance.
 */
public class InstrumentedHandler extends HandlerWrapper {
  private final MetricsRegistry metricsRegistry;

  private String name;

  // the requests handled by this handler, excluding active
  private Timer requests;

  // the number of dispatches seen by this handler, excluding active
  private Timer dispatches;

  // the number of active requests
  private Counter activeRequests;

  // the number of active dispatches
  private Counter activeDispatches;

  // the number of requests currently suspended.
  private Counter activeSuspended;

  // the number of requests that have been asynchronously dispatched
  private Meter asyncDispatches;

  // the number of requests that expired while suspended
  private Meter asyncTimeouts;

  private Meter[] responses;

  private Timer getRequests;
  private Timer postRequests;
  private Timer headRequests;
  private Timer putRequests;
  private Timer deleteRequests;
  private Timer optionsRequests;
  private Timer traceRequests;
  private Timer connectRequests;
  private Timer moveRequests;
  private Timer otherRequests;

  private AsyncListener listener;

  /**
   * Create a new instrumented handler using a given metrics registry.
   *
   * @param registry   the registry for the metrics
   *
   */
  public InstrumentedHandler(MetricsRegistry registry) {
    this.metricsRegistry = registry;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  protected void doStart() throws Exception {
    super.doStart();

    this.requests = metricsRegistry.newTimer(getHandler().getClass(), "requests");
    this.dispatches = metricsRegistry.newTimer(getHandler().getClass(), "dispatches");

    this.activeRequests = metricsRegistry.newCounter(getHandler().getClass(), "active-requests");
    this.activeDispatches = metricsRegistry.newCounter(getHandler().getClass(), "active-dispatches");
    this.activeSuspended = metricsRegistry.newCounter(getHandler().getClass(), "active-suspended");

    this.asyncDispatches = metricsRegistry.newMeter(getHandler().getClass(), "async-dispatches", "requests", TimeUnit.SECONDS);
    this.asyncTimeouts = metricsRegistry.newMeter(getHandler().getClass(), "async-timeouts", "requests", TimeUnit.SECONDS);

    this.responses = new Meter[]{
        metricsRegistry.newMeter(getHandler().getClass(), "1xx-responses", "responses", TimeUnit.SECONDS), // 1xx
        metricsRegistry.newMeter(getHandler().getClass(), "2xx-responses", "responses", TimeUnit.SECONDS), // 2xx
        metricsRegistry.newMeter(getHandler().getClass(), "3xx-responses", "responses", TimeUnit.SECONDS), // 3xx
        metricsRegistry.newMeter(getHandler().getClass(), "4xx-responses", "responses", TimeUnit.SECONDS), // 4xx
        metricsRegistry.newMeter(getHandler().getClass(), "5xx-responses", "responses", TimeUnit.SECONDS)  // 5xx
    };

    this.getRequests = metricsRegistry.newTimer(getHandler().getClass(), "get-requests");
    this.postRequests = metricsRegistry.newTimer(getHandler().getClass(), "post-requests");
    this.headRequests = metricsRegistry.newTimer(getHandler().getClass(), "head-requests");
    this.putRequests = metricsRegistry.newTimer(getHandler().getClass(), "put-requests");
    this.deleteRequests = metricsRegistry.newTimer(getHandler().getClass(), "delete-requests");
    this.optionsRequests = metricsRegistry.newTimer(getHandler().getClass(), "options-requests");
    this.traceRequests = metricsRegistry.newTimer(getHandler().getClass(), "trace-requests");
    this.connectRequests = metricsRegistry.newTimer(getHandler().getClass(), "connect-requests");
    this.moveRequests = metricsRegistry.newTimer(getHandler().getClass(), "move-requests");
    this.otherRequests = metricsRegistry.newTimer(getHandler().getClass(), "other-requests");

    metricsRegistry.newGauge(getHandler().getClass(), "percent-4xx-1m", new RatioGauge() {
      @Override
      protected double getNumerator() {
        return responses[3].oneMinuteRate();
      }

      @Override
      protected double getDenominator() {
        return requests.oneMinuteRate();
      }
    });

    metricsRegistry.newGauge(getHandler().getClass(), "percent-4xx-5m", new RatioGauge() {
      @Override
      protected double getNumerator() {
        return responses[3].fiveMinuteRate();
      }

      @Override
      protected double getDenominator() {
        return requests.fiveMinuteRate();
      }
    });

    metricsRegistry.newGauge(getHandler().getClass(), "percent-4xx-15m", new RatioGauge() {
      @Override
      protected double getNumerator() {
        return responses[3].fifteenMinuteRate();
      }

      @Override
      protected double getDenominator() {
        return requests.fifteenMinuteRate();
      }
    });

    metricsRegistry.newGauge(getHandler().getClass(), "percent-5xx-1m", new RatioGauge() {
      @Override
      protected double getNumerator() {
        return responses[4].oneMinuteRate();
      }

      @Override
      protected double getDenominator() {
        return requests.oneMinuteRate();
      }
    });

    metricsRegistry.newGauge(getHandler().getClass(), "percent-5xx-5m", new RatioGauge() {
      @Override
      protected double getNumerator() {
        return responses[4].fiveMinuteRate();
      }

      @Override
      protected double getDenominator() {
        return requests.fiveMinuteRate();
      }
    });

    metricsRegistry.newGauge(getHandler().getClass(), "percent-5xx-15m", new RatioGauge() {
      @Override
      protected double getNumerator() {
        return responses[4].fifteenMinuteRate();
      }

      @Override
      protected double getDenominator() {
        return requests.fifteenMinuteRate();
      }
    });


    this.listener = new AsyncListener() {
      private long startTime;

      @Override
      public void onTimeout(AsyncEvent event) throws IOException {
        asyncTimeouts.mark();
      }

      @Override
      public void onStartAsync(AsyncEvent event) throws IOException {
        startTime = System.currentTimeMillis();
        event.getAsyncContext().addListener(this);
      }

      @Override
      public void onError(AsyncEvent event) throws IOException {
      }

      @Override
      public void onComplete(AsyncEvent event) throws IOException {
        final AsyncContextState state = (AsyncContextState) event.getAsyncContext();
        final HttpServletRequest request = (HttpServletRequest) state.getRequest();
        final HttpServletResponse response = (HttpServletResponse) state.getResponse();
        updateResponses(request, response, startTime);
        if (state.getHttpChannelState().getState() != HttpChannelState.State.DISPATCHED) {
          activeSuspended.dec();
        }
      }
    };
  }

  @Override
  public void handle(String path,
                     Request request,
                     HttpServletRequest httpRequest,
                     HttpServletResponse httpResponse) throws IOException, ServletException {

    activeDispatches.inc();

    final long start;
    final HttpChannelState state = request.getHttpChannelState();
    if (state.isInitial()) {
      // new request
      activeRequests.inc();
      start = request.getTimeStamp();
    } else {
      // resumed request
      start = System.currentTimeMillis();
      activeSuspended.dec();
      if (state.getState() == HttpChannelState.State.DISPATCHED) {
        asyncDispatches.mark();
      }
    }

    try {
      super.handle(path, request, httpRequest, httpResponse);
    } finally {
      final long now = System.currentTimeMillis();
      final long dispatched = now - start;

      activeDispatches.dec();
      dispatches.update(dispatched, TimeUnit.MILLISECONDS);

      if (state.isSuspended()) {
        if (state.isInitial()) {
          state.addListener(listener);
        }
        activeSuspended.inc();
      } else if (state.isInitial()) {
        updateResponses(httpRequest, httpResponse, start);
      }
      // else onCompletion will handle it.
    }
  }

  private Timer requestTimer(String method) {
    final HttpMethod m = HttpMethod.fromString(method);
    if (m == null) {
      return otherRequests;
    } else {
      switch (m) {
        case GET:
          return getRequests;
        case POST:
          return postRequests;
        case PUT:
          return putRequests;
        case HEAD:
          return headRequests;
        case DELETE:
          return deleteRequests;
        case OPTIONS:
          return optionsRequests;
        case TRACE:
          return traceRequests;
        case CONNECT:
          return connectRequests;
        case MOVE:
          return moveRequests;
        default:
          return otherRequests;
      }
    }
  }

  private void updateResponses(HttpServletRequest request, HttpServletResponse response, long start) {
    final int responseStatus = response.getStatus() / 100;
    if (responseStatus >= 1 && responseStatus <= 5) {
      responses[responseStatus - 1].mark();
    }
    activeRequests.dec();
    final long elapsedTime = System.currentTimeMillis() - start;
    requests.update(elapsedTime, TimeUnit.MILLISECONDS);
    requestTimer(request.getMethod()).update(elapsedTime, TimeUnit.MILLISECONDS);
  }
}
