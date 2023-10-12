/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
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
package eu.openanalytics.containerproxy.test.helpers;

import eu.openanalytics.containerproxy.backend.strategy.IProxyTestStrategy;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.util.Retrying;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;

public class NotInternalOnlyTestStrategy implements IProxyTestStrategy {

	@Override
	public boolean testProxy(Proxy proxy) {
		URI targetURI = proxy.getContainers().get(0).getTargets().get(proxy.getId());

		return Retrying.retry((currentAttempt, maxAttempts) -> {
			try {
				if (proxy.getStatus().isUnavailable()) {
					// proxy got stopped while loading -> no need to try to connect it since the container will already be deleted
					return true;
				}
				URL testURL = new URL(targetURI.toString() + "/");
				HttpURLConnection connection = ((HttpURLConnection) testURL.openConnection());
				connection.setInstanceFollowRedirects(false);
				int responseCode = connection.getResponseCode();
				if (Arrays.asList(200, 301, 302, 303, 307, 308).contains(responseCode)) {
					return true;
				}
			} catch (Exception e) {
                return false;
			}
			return false;
		}, 60_000);
	}

}
