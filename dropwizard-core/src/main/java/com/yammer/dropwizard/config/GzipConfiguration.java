package com.yammer.dropwizard.config;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Set;

import javax.validation.constraints.NotNull;

import org.eclipse.jetty.server.Handler;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.yammer.dropwizard.jetty.BiDiGzipHandler;
import com.yammer.dropwizard.util.Size;

@SuppressWarnings("UnusedDeclaration")
public class GzipConfiguration {
    @JsonProperty
    private boolean enabled = true;

    @JsonProperty
    @NotNull
    private Size minimumEntitySize = Size.bytes(256);

    @JsonProperty
    private Size bufferSize = Size.kilobytes(8);

    @JsonProperty
    private ImmutableSet<String> excludedUserAgents = ImmutableSet.of();

    @JsonProperty
    private ImmutableSet<String> compressedMimeTypes = ImmutableSet.of();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Size getMinimumEntitySize() {
        return minimumEntitySize;
    }

    public void setMinimumEntitySize(Size size) {
        this.minimumEntitySize = checkNotNull(size);
    }

    public Size getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(Size size) {
        this.bufferSize = checkNotNull(size);
    }

    public ImmutableSet<String> getExcludedUserAgents() {
        return excludedUserAgents;
    }

    public void setExcludedUserAgents(Set<String> userAgents) {
        this.excludedUserAgents = ImmutableSet.copyOf(userAgents);
    }

    public ImmutableSet<String> getCompressedMimeTypes() {
        return compressedMimeTypes;
    }

    public void setCompressedMimeTypes(Set<String> mimeTypes) {
        this.compressedMimeTypes = ImmutableSet.copyOf(mimeTypes);
    }

    public Handler build(Handler handler) {
        if (!isEnabled()) {
            return handler;
        } else {
            final BiDiGzipHandler gzipHandler = new BiDiGzipHandler();
            gzipHandler.setHandler(handler);
            gzipHandler.setInflateNoWrap(true);

            final Size minEntitySize = getMinimumEntitySize();
            gzipHandler.setMinGzipSize((int) minEntitySize.toBytes());

            final Size bufferSize = getBufferSize();
            gzipHandler.setInputBufferSize((int) bufferSize.toBytes());

            final ImmutableSet<String> userAgents = getExcludedUserAgents();
            if (!userAgents.isEmpty()) {
                gzipHandler.setExcludedAgentPatterns(Iterables.toArray(userAgents, String.class));
            }

            final ImmutableSet<String> mimeTypes = getCompressedMimeTypes();
            if (!mimeTypes.isEmpty()) {
                gzipHandler.setIncludedMimeTypes(Iterables.toArray(mimeTypes, String.class));
            }

            return gzipHandler;
        }
    }
}
