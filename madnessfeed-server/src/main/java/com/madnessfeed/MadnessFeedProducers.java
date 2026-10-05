package com.madnessfeed;

import com.codahale.metrics.MetricRegistry;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.time.InstantSource;

@Singleton
public class MadnessFeedProducers {

    @Produces
    @Singleton
    public InstantSource instantSource() {
        return InstantSource.system();
    }

    @Produces
    @Singleton
    public MetricRegistry metricRegistry() {
        return new MetricRegistry();
    }
}
