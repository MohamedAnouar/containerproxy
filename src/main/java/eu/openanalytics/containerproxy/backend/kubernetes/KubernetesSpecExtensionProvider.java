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
package eu.openanalytics.containerproxy.backend.kubernetes;


import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "proxy")
public class KubernetesSpecExtensionProvider {

    private List<KubernetesSpecExtension> specs;

    @Inject
    private IProxySpecProvider proxySpecProvider;

    @PostConstruct
    public void postInit() {
        specs.forEach(specExtension -> {
            proxySpecProvider.getSpec(specExtension.getId()).addSpecExtension(specExtension);
        });
    }

    public void setSpecs(List<KubernetesSpecExtension> specs) {
        this.specs = specs;
    }

    public List<KubernetesSpecExtension> getSpecs() {
        return specs;
    }

}
