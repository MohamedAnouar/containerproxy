/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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
package eu.openanalytics.containerproxy.model.runtime.runtimevalues;

public class HeartbeatTimeoutKey extends RuntimeValueKey<Long> {

    private HeartbeatTimeoutKey() {
        super("openanalytics.eu/sp-heartbeat-timeout",
                "SHINYPROXY_HEARTBEAT_TIMEOUT",
                false,
                true,
                false,
                true, Long.class);
    }

    public static HeartbeatTimeoutKey inst = new HeartbeatTimeoutKey();

}
