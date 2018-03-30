package com.yammer.dropwizard.jetty;

import java.util.concurrent.BlockingQueue;

import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.util.RatioGauge;

public class InstrumentedQueuedThreadPool extends QueuedThreadPool {
  private final MetricsRegistry metricsRegistry;

  public InstrumentedQueuedThreadPool(@Name("registry") MetricsRegistry registry) {
    this(registry, 200);
  }

  public InstrumentedQueuedThreadPool(@Name("registry") MetricsRegistry registry,
                                      @Name("maxThreads") int maxThreads) {
    this(registry, maxThreads, 8);
  }

  public InstrumentedQueuedThreadPool(@Name("registry") MetricsRegistry registry,
                                      @Name("maxThreads") int maxThreads,
                                      @Name("minThreads") int minThreads) {
    this(registry, maxThreads, minThreads, 60000);
  }

  public InstrumentedQueuedThreadPool(@Name("registry") MetricsRegistry registry,
                                      @Name("maxThreads") int maxThreads,
                                      @Name("minThreads") int minThreads,
                                      @Name("idleTimeout") int idleTimeout) {
    this(registry, maxThreads, minThreads, idleTimeout, null);
  }

  public InstrumentedQueuedThreadPool(@Name("registry") MetricsRegistry registry,
                                      @Name("maxThreads") int maxThreads,
                                      @Name("minThreads") int minThreads,
                                      @Name("idleTimeout") int idleTimeout,
                                      @Name("queue") BlockingQueue<Runnable> queue) {
    super(maxThreads, minThreads, idleTimeout, queue);
    this.metricsRegistry = registry;
  }

  @Override
  protected void doStart() throws Exception {
    super.doStart();
    metricsRegistry.newGauge(QueuedThreadPool.class, "utilization", new RatioGauge() {
      @Override
      protected double getNumerator() {
        return getThreads() - getIdleThreads();
      }

      @Override
      protected double getDenominator() {
        return getThreads();
      }
    });
    metricsRegistry.newGauge(QueuedThreadPool.class, "utilization-max", new RatioGauge() {
      @Override
      protected double getNumerator() {
        return getThreads() - getIdleThreads();
      }

      @Override
      protected double getDenominator() {
        return getMaxThreads();
      }
    });
    metricsRegistry.newGauge(QueuedThreadPool.class, "size", new Gauge<Integer>() {
      @Override
      public Integer value() {
        return getThreads();
      }
    });
    metricsRegistry.newGauge(QueuedThreadPool.class, "jobs", new Gauge<Integer>() {
      @Override
      public Integer value() {
        // This assumes the QueuedThreadPool is using a BlockingArrayQueue or
        // ArrayBlockingQueue for its queue, and is therefore a constant-time operation.
        return getQueue().size();
      }
    });
  }
}
