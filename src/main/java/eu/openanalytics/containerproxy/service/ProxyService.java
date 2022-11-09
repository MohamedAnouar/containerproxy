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
package eu.openanalytics.containerproxy.service;

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.ProxyFailedToStartException;
import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.backend.strategy.IProxyTestStrategy;
import eu.openanalytics.containerproxy.event.ProxyPauseEvent;
import eu.openanalytics.containerproxy.event.ProxyResumeEvent;
import eu.openanalytics.containerproxy.event.ProxyStartEvent;
import eu.openanalytics.containerproxy.event.ProxyStartFailedEvent;
import eu.openanalytics.containerproxy.event.ProxyStopEvent;
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStartupLog;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.model.store.IProxyStore;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>
 * This service is the entry point for working with proxies.
 * It offers methods to list, start and stop proxies, as well
 * as methods for managing proxy specs.
 * </p><p>
 * A note about security: these methods are considered internal API,
 * and are therefore allowed to bypass security checks.<br/>
 * The caller is always responsible for performing security
 * checks before manipulating proxies.
 * </p>
 */
@Service
public class ProxyService {
		
	private final Logger log = LogManager.getLogger(ProxyService.class);

	@Inject
	private IProxyStore proxyStore;

	@Inject
	private IProxySpecProvider baseSpecProvider;
	
	@Inject
	private IContainerBackend backend;

	@Inject
	private ProxyMappingManager mappingManager;
	
	@Inject
	private UserService userService;
	
	@Inject
	private ApplicationEventPublisher applicationEventPublisher;

	@Inject
	private Environment environment;

	@Inject
	private RuntimeValueService runtimeValueService;

	@Inject
	private SpecExpressionResolver expressionResolver;

	@Inject
	protected IProxyTestStrategy testStrategy;

	@Inject
	private ParametersService parametersService;

	private boolean stopAppsOnShutdown;

	private static final String PROPERTY_STOP_PROXIES_ON_SHUTDOWN = "proxy.stop-proxies-on-shutdown";

	@PostConstruct
	public void init() {
	    stopAppsOnShutdown = Boolean.parseBoolean(environment.getProperty(PROPERTY_STOP_PROXIES_ON_SHUTDOWN, "true"));
	}

	@PreDestroy
	public void shutdown() {
		if (!stopAppsOnShutdown) {
			return;
		}
		for (Proxy proxy : proxyStore.getAllProxies()) {
			try {
				backend.stopProxy(proxy);
			} catch (Exception exception) {
				exception.printStackTrace();
			}
		}
	}


	
	/**
	 * Find the ProxySpec that matches the given ID.
	 * 
	 * @param id The ID to look for.
	 * @return A matching ProxySpec, or null if no match was found.
	 */
	public ProxySpec getProxySpec(String id) {
		if (id == null || id.isEmpty()) return null;
		return findProxySpec(spec -> spec.getId().equals(id), true);
	}
	
	/**
	 * Find the first ProxySpec that matches the given filter.
	 * 
	 * @param filter The filter to match, may be null.
	 * @param ignoreAccessControl True to search in all ProxySpecs, regardless of the current security context.
	 * @return The first ProxySpec found that matches the filter, or null if no match was found.
	 */
	public ProxySpec findProxySpec(Predicate<ProxySpec> filter, boolean ignoreAccessControl) {
		return getProxySpecs(filter, ignoreAccessControl).stream().findAny().orElse(null);
	}
	
	/**
	 * Find all ProxySpecs that match the given filter.
	 * 
	 * @param filter The filter to match, or null.
	 * @param ignoreAccessControl True to search in all ProxySpecs, regardless of the current security context.
	 * @return A List of matching ProxySpecs, may be empty.
	 */
	public List<ProxySpec> getProxySpecs(Predicate<ProxySpec> filter, boolean ignoreAccessControl) {
		return baseSpecProvider.getSpecs().stream()
				.filter(spec -> ignoreAccessControl || userService.canAccess(spec))
				.filter(spec -> filter == null || filter.test(spec))
				.collect(Collectors.toList());
	}
	
	/**
	 * Find a proxy using its ID.
	 * Without authentication check.
	 * 
	 * @param id The ID of the proxy to find.
	 * @return The matching proxy, or null if no match was found.
	 */
	public Proxy getProxy(String id) {
		return proxyStore.getProxy(id);
	}
	
	/**
	 * Find The first proxy that matches the given filter.
	 * 
	 * @param filter The filter to apply while searching, or null.
	 * @param ignoreAccessControl True to search in all proxies, regardless of the current security context.
	 * @return The first proxy found that matches the filter, or null if no match was found.
	 */
	public Proxy findProxy(Predicate<Proxy> filter, boolean ignoreAccessControl) {
		return getProxies(filter, ignoreAccessControl).stream().findAny().orElse(null);
	}
	
	/**
	 * Find all proxies that match an optional filter.
	 * 
	 * @param filter The filter to match, or null.
	 * @param ignoreAccessControl True to search in all proxies, regardless of the current security context.
	 * @return A List of matching proxies, may be empty.
	 */
	public List<Proxy> getProxies(Predicate<Proxy> filter, boolean ignoreAccessControl) {
		// TODO remove filter option
		boolean isAdmin = userService.isAdmin();
		List<Proxy> matches = new ArrayList<>();
		for (Proxy proxy: proxyStore.getAllProxies()) {
			boolean hasAccess = ignoreAccessControl || isAdmin || userService.isOwner(proxy);
			if (hasAccess && (filter == null || filter.test(proxy))) matches.add(proxy);
		}
		return matches;
	}

	/**
	 * Find all proxies that match an optional filter and that are owned by the current user.
	 *
	 * @param filter The filter to match, or null.
	 * @return A List of matching proxies, may be empty.
	 */
	public List<Proxy> getProxiesOfCurrentUser(Predicate<Proxy> filter) {
		List<Proxy> matches = new ArrayList<>();
		for (Proxy proxy: proxyStore.getAllProxies()) {
			boolean hasAccess = userService.isOwner(proxy);
			if (hasAccess && (filter == null || filter.test(proxy))) matches.add(proxy);
		}
		return matches;
	}

	/**
	 * Launch a new proxy using the given ProxySpec.
	 *
	 * @param spec The ProxySpec to base the new proxy on.
	 * @return The newly launched proxy.
	 * @throws ContainerProxyException If the proxy fails to start for any reason.
	 */
	public Proxy startProxy(ProxySpec spec) throws ContainerProxyException, InvalidParametersException {
		String id = UUID.randomUUID().toString();
	    startProxy(userService.getCurrentAuth(), spec,  null, id, null).run();
		return getProxy(id);
    }

	/**
	 * Launch a new proxy using the given ProxySpec.
	 *
	 * @param spec The ProxySpec to base the new proxy on.
	 * @return The newly launched proxy.
	 * @throws ContainerProxyException If the proxy fails to start for any reason.
	 */
	public Command startProxy(Authentication user, ProxySpec spec, List<RuntimeValue> runtimeValues, String proxyId, Map<String, String> parameters) throws ContainerProxyException, InvalidParametersException {
		if (!userService.canAccess(user, spec)) {
			throw new AccessDeniedException(String.format("Cannot start proxy %s: access denied", spec.getId()));
		}

		Proxy.ProxyBuilder proxyBuilder = Proxy.builder();
		proxyBuilder.id(proxyId);
		proxyBuilder.status(ProxyStatus.New);
		proxyBuilder.userId(userService.getUserId(user));
		proxyBuilder.specId(spec.getId());
		proxyBuilder.createdTimestamp(System.currentTimeMillis());

		if (spec.getDisplayName() != null) {
			proxyBuilder.displayName(spec.getDisplayName());
		} else {
			proxyBuilder.displayName(spec.getId());
		}

		if (runtimeValues != null) {
			proxyBuilder.addRuntimeValues(runtimeValues);
		}

		Proxy currentProxy = runtimeValueService.processParameters(user, spec, parameters, proxyBuilder.build());
		proxyStore.addProxy(currentProxy);

		return new Command(() -> {
			ProxyStartupLog.ProxyStartupLogBuilder proxyStartupLog = new ProxyStartupLog.ProxyStartupLogBuilder();
			Pair<ProxySpec, Proxy> r = prepareProxyForStart(user, currentProxy, spec);
			ProxySpec resolvedSpec = r.getKey();
			Proxy startingProxy = r.getValue();

			try {
				startingProxy = backend.startProxy(user, startingProxy, resolvedSpec, proxyStartupLog);
			} catch (ProxyFailedToStartException t) {
				try {
					backend.stopProxy(t.getProxy());
				} catch (Throwable t2) {
					// log error, but ignore it otherwise
					// most important is that we remove the proxy from memory
					log.warn("Error while stopping failed proxy", t2);
				}
				proxyStore.removeProxy(t.getProxy());
				applicationEventPublisher.publishEvent(new ProxyStartFailedEvent(t.getProxy()));
				throw new ContainerProxyException("Container failed to start", t);
			} catch (Throwable t) {
				proxyStore.removeProxy(startingProxy);
				// TODO -> bridge event
				applicationEventPublisher.publishEvent(new ProxyStartFailedEvent(startingProxy));
				throw new ContainerProxyException("Container failed to start", t);
			}

			// TODO
//		if (proxyBuilder.getStatus().equals(ProxyStatus.Stopped) || proxyBuilder.getStatus().equals(ProxyStatus.Stopping)) {
//			log.info(String.format("Pending proxy cleaned up [user: %s] [spec: %s] [id: %s]", proxyBuilder.getUserId(), proxyBuilder.getSpecId(), proxyBuilder.getId()));
//			return proxyBuilder;
//		}

			if (!testStrategy.testProxy(startingProxy)) {
				// TODO catch stopProxy errors
				backend.stopProxy(startingProxy);
				proxyStore.removeProxy(startingProxy);
				applicationEventPublisher.publishEvent(new ProxyStartFailedEvent(startingProxy));
				throw new ContainerProxyException("Container did not respond in time");
			}
			proxyStartupLog.applicationStarted();

			startingProxy = startingProxy.toBuilder()
					.startupTimestamp(System.currentTimeMillis())
					.status(ProxyStatus.Up)
					.build();

			setupProxy(startingProxy);

			log.info(String.format("Proxy activated [user: %s] [spec: %s] [id: %s]", startingProxy.getUserId(), resolvedSpec.getId(), startingProxy.getContainers().get(0).getId()));
			proxyStore.updateProxy(startingProxy);

			applicationEventPublisher.publishEvent(new ProxyStartEvent(startingProxy, proxyStartupLog.succeeded()));
		});
	}


	/**
	 * Stop a running proxy.
	 *
	 * @param user
	 * @param proxy               The proxy to stop.
	 * @param ignoreAccessControl True to allow access to any proxy, regardless of the current security context.
	 */
	public Command stopProxy(Authentication user, Proxy proxy, boolean ignoreAccessControl) {
		if (!ignoreAccessControl && !userService.isAdmin(user) && !userService.isOwner(user, proxy)) {
			throw new AccessDeniedException(String.format("Cannot stop proxy %s: access denied", proxy.getId()));
		}

		Proxy stoppingProxy = proxy.withStatus(ProxyStatus.Stopping);
		proxyStore.updateProxy(stoppingProxy);

		for (Entry<String, URI> target : proxy.getTargets().entrySet()) {
			mappingManager.removeMapping(target.getKey());
		}

		return new Command(() -> {
			Proxy stoppedProxy = proxy.withStatus(ProxyStatus.Stopped);
			try {
				backend.stopProxy(proxy);
				// TODO we may want to remove this
				proxyStore.updateProxy(stoppedProxy);
				log.info(String.format("Proxy released [user: %s] [spec: %s] [id: %s]", proxy.getUserId(), proxy.getSpecId(), proxy.getId()));
				applicationEventPublisher.publishEvent(new ProxyStopEvent(proxy));
			} catch (Exception e) {
				log.error("Failed to release proxy " + proxy.getId(), e);
			}
			try {
				proxyStore.removeProxy(stoppedProxy);
			} catch (Exception e) {
				log.error("Failed to remove proxy " + proxy.getId(), e);
			}
		});
	}

	public Command pauseProxy(Authentication user, Proxy proxy, boolean ignoreAccessControl) {
		if (!ignoreAccessControl && !userService.isAdmin(user) && !userService.isOwner(user, proxy)) {
			throw new AccessDeniedException(String.format("Cannot pause proxy %s: access denied", proxy.getId()));
		}

		if (!backend.supportsPause()) {
			throw new IllegalArgumentException("Trying to pause a proxy when the backend does not support pausing apps");
		}

		Proxy stoppingProxy = proxy.withStatus(ProxyStatus.Pausing);
		proxyStore.updateProxy(stoppingProxy);

		for (Entry<String, URI> target : proxy.getTargets().entrySet()) {
			mappingManager.removeMapping(target.getKey());
		}

		return new Command(() -> {
			try {
				backend.pauseProxy(proxy);
				Proxy stoppedProxy = proxy.withStatus(ProxyStatus.Paused);
				proxyStore.updateProxy(stoppedProxy);
				log.info(String.format("Proxy paused [user: %s] [spec: %s] [id: %s]", proxy.getUserId(), proxy.getSpecId(), proxy.getId()));
				applicationEventPublisher.publishEvent(new ProxyPauseEvent(proxy));
			} catch (Exception e) {
				log.error("Failed to pause proxy " + proxy.getId(), e);
			}
		});
	}

	public Command resumeProxy(Authentication user, Proxy proxy, Map<String, String> parameters, boolean ignoreAccessControl) throws InvalidParametersException {
		// TODO proxystartuplog?
		// TODO admin should not have access?
		if (!ignoreAccessControl && !userService.isAdmin(user) && !userService.isOwner(user, proxy)) {
			throw new AccessDeniedException(String.format("Cannot resume proxy %s: access denied", proxy.getId()));
		}

		if (!backend.supportsPause()) {
			throw new IllegalArgumentException("Trying to pause a proxy when the backend does not support pausing apps");
		}

		// caller may or may not already mark proxy as starting
		Proxy resumingProxy = proxy.withStatus(ProxyStatus.Resuming);
		Proxy parameterizedProxy = runtimeValueService.processParameters(user, getProxySpec(proxy.getSpecId()), parameters, resumingProxy);
		proxyStore.updateProxy(parameterizedProxy);

		return new Command(() -> {
			Proxy result = parameterizedProxy;

			// When resuming the proxy, we *do* want to re-evaluate the environment variables
			// therefore we fetch the latest version of the spec and evaluate SpeL
			ProxySpec spec = baseSpecProvider.getSpec(result.getSpecId());
			Pair<ProxySpec, Proxy> r = prepareProxyForStart(user, result, spec); // TODO support parameters
			spec = r.getKey();
			result = r.getValue();

			try {
				result = backend.resumeProxy(result, spec);
			} catch (ProxyFailedToStartException t) {
				try {
					backend.stopProxy(t.getProxy());
				} catch (Throwable t2) {
					// log error, but ignore it otherwise
					// most important is that we remove the proxy from memory
					log.warn("Error while stopping failed proxy", t2);
				}
				proxyStore.removeProxy(t.getProxy());
				applicationEventPublisher.publishEvent(new ProxyStartFailedEvent(t.getProxy()));
				throw new ContainerProxyException("Container failed to start", t);
			} catch (Throwable t) {
				proxyStore.removeProxy(result);
				applicationEventPublisher.publishEvent(new ProxyStartFailedEvent(result));
				throw new ContainerProxyException("Container failed to start", t);
			}

			// TODO handle stopped pending app

			if (!testStrategy.testProxy(result)) {
				// TODO catch stopProxy errors
				backend.stopProxy(result);
				proxyStore.removeProxy(result);
				applicationEventPublisher.publishEvent(new ProxyStartFailedEvent(result));
				throw new ContainerProxyException("Container did not respond in time");
			}

			result = result.withStatus(ProxyStatus.Up);
			setupProxy(result);

			log.info(String.format("Proxy resumed [user: %s] [spec: %s] [id: %s]", result.getUserId(), result.getSpecId(), result.getId()));
			proxyStore.updateProxy(result);

			applicationEventPublisher.publishEvent(new ProxyResumeEvent(result));
		});
	}

	private Pair<ProxySpec, Proxy> prepareProxyForStart(Authentication user, Proxy proxy, ProxySpec spec) {
		try {
			proxy = runtimeValueService.addRuntimeValuesBeforeSpel(user, spec, proxy);
			proxy = backend.addRuntimeValuesBeforeSpel(user, spec, proxy);

			SpecExpressionContext context = SpecExpressionContext.create(
					proxy,
					spec,
					user.getPrincipal(),
					user.getCredentials());

			// resolve SpEL expression in spec
			spec = spec.resolve(expressionResolver, context);

			// add the runtime values which depend on spel to be resolved (and thus cannot be used in spel expression)
			proxy = runtimeValueService.addRuntimeValuesAfterSpel(spec, proxy);

			return Pair.of(spec, proxy);
		} catch (Throwable t) {
			try {
				backend.stopProxy(proxy); // stop in case we are resuming
			} catch (Throwable t2) {
				// log error, but ignore it otherwise
				// most important is that we remove the proxy from memory
				log.warn("Error while stopping failed proxy", t2);
			}
			proxyStore.removeProxy(proxy);
			applicationEventPublisher.publishEvent(new ProxyStartFailedEvent(proxy));
			throw new ContainerProxyException("Container failed to start", t);
		}
	}


	/**
	 * Add existing Proxy to the ProxyService.
	 * This is used by the AppRecovery feature.
	 * @param proxy
	 */
	public void addExistingProxy(Proxy proxy) {
		proxyStore.addProxy(proxy);

		setupProxy(proxy);

		log.info(String.format("Existing Proxy re-activated [user: %s] [spec: %s] [id: %s]", proxy.getUserId(), proxy.getSpecId(), proxy.getId()));
	}

	/**
	 * Setups the Mapping of and logging of the proxy.
	 */
	private void setupProxy(Proxy proxy) {
		for (Container container : proxy.getContainers()) {
			for (Entry<String, URI> target : container.getTargets().entrySet()) {
				mappingManager.addMapping(proxy.getId(), target.getKey(), target.getValue());
			}
		}
	}

	public static class Command implements Runnable {

		private final Runnable r;

		public Command(Runnable r ) {
			this.r = r;
		}

		@Override
		public void run() {
			r.run();
		}

	}

}
