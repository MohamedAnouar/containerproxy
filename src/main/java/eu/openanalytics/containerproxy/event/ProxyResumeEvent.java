/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.containerproxy.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class ProxyResumeEvent extends BridgeableEvent {

    String proxyId;
    String userId;
    String specId;

    @JsonCreator
    public ProxyResumeEvent(@JsonProperty("source") String source,
                            @JsonProperty("proxyId") String proxyId,
                            @JsonProperty("userId") String userId,
                            @JsonProperty("specId") String specId) {
        super(source);
        this.proxyId = proxyId;
        this.userId = userId;
        this.specId = specId;
    }

    public ProxyResumeEvent(Proxy proxy) {
        this(SOURCE_NOT_AVAILABLE, proxy.getId(), proxy.getUserId(), proxy.getSpecId());
    }

    @Override
    public ProxyResumeEvent withSource(String source) {
        return new ProxyResumeEvent(source, proxyId, userId, specId);
    }

}
