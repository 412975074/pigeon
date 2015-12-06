/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.registry;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;

import com.dianping.pigeon.config.ConfigManager;
import com.dianping.pigeon.config.ConfigManagerLoader;
import com.dianping.pigeon.domain.HostInfo;
import com.dianping.pigeon.extension.ExtensionLoader;
import com.dianping.pigeon.log.LoggerLoader;
import com.dianping.pigeon.monitor.Monitor;
import com.dianping.pigeon.monitor.MonitorLoader;
import com.dianping.pigeon.registry.config.DefaultRegistryConfigManager;
import com.dianping.pigeon.registry.config.RegistryConfigManager;
import com.dianping.pigeon.registry.exception.RegistryException;
import com.dianping.pigeon.registry.listener.RegistryEventListener;
import com.dianping.pigeon.registry.listener.ServerInfoListener;
import com.dianping.pigeon.registry.util.Constants;
import com.dianping.pigeon.registry.util.Utils;

public class RegistryManager {

	private static final Logger logger = LoggerLoader.getLogger(RegistryManager.class);

	private Properties props = new Properties();

	private static volatile boolean isInit = false;

	private static Throwable initializeException = null;

	private static RegistryManager instance = new RegistryManager();

	private static RegistryConfigManager registryConfigManager = new DefaultRegistryConfigManager();

	private static Registry registry = ExtensionLoader.getExtension(Registry.class);

	private static ConcurrentHashMap<String, Set<HostInfo>> referencedServiceAddresses = new ConcurrentHashMap<String, Set<HostInfo>>();

	private static ConcurrentHashMap<String, HostInfo> referencedAddresses = new ConcurrentHashMap<String, HostInfo>();

	private static ConfigManager configManager = ConfigManagerLoader.getConfigManager();

	private static ConcurrentHashMap<String, Object> registeredServices = new ConcurrentHashMap<String, Object>();

	Monitor monitor = MonitorLoader.getMonitor();

	private static final boolean fallbackDefaultGroup = configManager.getBooleanValue("pigeon.registry.group.fallback",
			true);

	private static boolean enableLocalConfig = ConfigManagerLoader.getConfigManager().getBooleanValue(
			"pigeon.registry.config.local", false);

	private RegistryManager() {
	}

	public static boolean isInitialized() {
		return isInit;
	}

	public static Throwable getInitializeException() {
		return initializeException;
	}

	public static RegistryManager getInstance() {
		if (!isInit) {
			synchronized (RegistryManager.class) {
				if (!isInit) {
					instance.init(registryConfigManager.getRegistryConfig());
					initializeException = null;
					RegistryEventListener.addListener(new InnerServerInfoListener());
					isInit = true;
				}
			}
		}
		return instance;
	}

	private void init(Properties properties) {
		instance.setProperties(properties);
		String registryType = properties.getProperty(Constants.KEY_REGISTRY_TYPE);
		if (!Constants.REGISTRY_TYPE_LOCAL.equalsIgnoreCase(registryType)) {
			if (registry != null) {
				try {
					registry.init(properties);
				} catch (Throwable t) {
					initializeException = t;
					throw new RuntimeException(t);
				}
			}
		} else {
		}
	}

	public Registry getRegistry() {
		return registry;
	}

	public void setProperty(String key, String value) {
		// 如果是dev环境，可以把当前配置加载进去
		props.put(key, value);
	}

	public void setProperties(Properties props) {
		this.props.putAll(props);
	}

	public Set<String> getReferencedServices() {
		return referencedServiceAddresses.keySet();
	}

	public Set<String> getRegisteredServices() {
		return registeredServices.keySet();
	}

	public boolean isReferencedService(String serviceName, String group) {
		return referencedServiceAddresses.containsKey(serviceName);
	}

	public List<String> getServiceAddressList(String serviceName, String group) throws RegistryException {
		String serviceAddress = getServiceAddress(serviceName, group);
		return Utils.getAddressList(serviceName, serviceAddress);
	}

	public String getServiceAddress(String serviceName, String group) throws RegistryException {
		String serviceKey = getServiceKey(serviceName, group);
		if (props.containsKey(serviceKey)) {
			if (logger.isInfoEnabled()) {
				logger.info("get service address from local properties, service name:" + serviceName + "  address:"
						+ props.getProperty(serviceKey));
			}
			return props.getProperty(serviceKey);
		}
		if (enableLocalConfig) {
			String addr = configManager.getLocalStringValue(Utils.escapeServiceName(serviceKey));
			if (addr == null) {
				try {
					addr = configManager.getLocalStringValue(serviceKey);
				} catch (Throwable e) {
				}
			}
			if (!StringUtils.isBlank(addr)) {
				if (logger.isDebugEnabled()) {
					logger.debug("get service address from local properties, service name:" + serviceName
							+ "  address:" + addr);
				}
				return addr;
			}
		}
		if (registry != null) {
			String addr = registry.getServiceAddress(serviceName, group, fallbackDefaultGroup);
			return addr;
		}

		return null;
	}

	private String getServiceKey(String serviceName, String group) {
		if (StringUtils.isBlank(group)) {
			return serviceName;
		} else {
			return serviceName + "?" + group;
		}
	}

	public int getServiceWeightFromCache(String serverAddress) {
		HostInfo hostInfo = referencedAddresses.get(serverAddress);
		if (hostInfo != null) {
			return hostInfo.getWeight();
		}
		return Constants.DEFAULT_WEIGHT;
	}

	public int getServiceWeight(String serverAddress, boolean readCache) {
		if (readCache) {
			HostInfo hostInfo = referencedAddresses.get(serverAddress);
			if (hostInfo != null) {
				return hostInfo.getWeight();
			}
		}
		int weight = Constants.DEFAULT_WEIGHT;
		if (registry != null) {
			try {
				weight = registry.getServerWeight(serverAddress);
				HostInfo hostInfo = referencedAddresses.get(serverAddress);
				if (hostInfo != null) {
					hostInfo.setWeight(weight);
				}
			} catch (Throwable e) {
				logger.error("failed to get weight for " + serverAddress, e);
			}
		}
		return weight;
	}

	public int getServiceWeight(String serverAddress) {
		return getServiceWeight(serverAddress, true);
	}

	/*
	 * Update service weight in local cache. Will not update to registry center.
	 */
	public void setServiceWeight(String serviceAddress, int weight) {
		HostInfo hostInfo = referencedAddresses.get(serviceAddress);
		if (hostInfo == null) {
			if (!serviceAddress.startsWith(configManager.getLocalIp())) {
				logger.warn("weight not found for address:" + serviceAddress);
			}
			return;
		}
		hostInfo.setWeight(weight);
		logger.info("set " + serviceAddress + " weight to " + weight);
	}

	public void registerService(String serviceName, String group, String serviceAddress, int weight)
			throws RegistryException {
		if (registry != null) {
			registry.registerService(serviceName, group, serviceAddress, weight);
			registeredServices.putIfAbsent(serviceName, serviceAddress);
			monitor.logEvent("PigeonService.register", serviceName, "weight=" + weight + "&group=" + group);
		}
	}

	public void setServerWeight(String serverAddress, int weight) throws RegistryException {
		if (registry != null) {
			registry.setServerWeight(serverAddress, weight);
			monitor.logEvent("PigeonService.weight", weight + "", "");
		}
	}

	public void unregisterService(String serviceName, String serviceAddress) throws RegistryException {
		unregisterService(serviceName, Constants.DEFAULT_GROUP, serviceAddress);
	}

	public void unregisterService(String serviceName, String group, String serviceAddress) throws RegistryException {
		if (registry != null) {
			registry.unregisterService(serviceName, group, serviceAddress);
			registeredServices.remove(serviceName);
			monitor.logEvent("PigeonService.unregister", serviceName, "group=" + group);
		}
	}

	public void addServiceAddress(String serviceName, String host, int port, int weight) {
		Utils.validateWeight(host, port, weight);

		HostInfo hostInfo = new HostInfo(host, port, weight);

		Set<HostInfo> hostInfos = referencedServiceAddresses.get(serviceName);
		if (hostInfos == null) {
			hostInfos = Collections.newSetFromMap(new ConcurrentHashMap<HostInfo, Boolean>());
			Set<HostInfo> oldHostInfos = referencedServiceAddresses.putIfAbsent(serviceName, hostInfos);
			if (oldHostInfos != null) {
				hostInfos = oldHostInfos;
			}
		}
		hostInfos.add(hostInfo);

		if (!referencedAddresses.containsKey(hostInfo.getConnect())) {
			referencedAddresses.put(hostInfo.getConnect(), hostInfo);
			if (registry != null) {
				String app = registry.getServerApp(hostInfo.getConnect());
				hostInfo.setApp(app);
				String version = registry.getServerVersion(hostInfo.getConnect());
				hostInfo.setVersion(version);
			}
		}
	}

	public void removeServiceAddress(String serviceName, HostInfo hostInfo) {
		Set<HostInfo> hostInfos = referencedServiceAddresses.get(serviceName);
		if (hostInfos == null || !hostInfos.contains(hostInfo)) {
			logger.info("address:" + hostInfo + " is not in address list of service " + serviceName);
			return;
		}
		hostInfos.remove(hostInfo);
		logger.info("removed address:" + hostInfo + " from service:" + serviceName);

		// If server is not referencd any more, remove from server list
		if (!isAddressReferenced(hostInfo)) {
			referencedAddresses.remove(hostInfo.getConnect());
		}
	}

	private boolean isAddressReferenced(HostInfo hostInfo) {
		for (String key : referencedServiceAddresses.keySet()) {
			Set<HostInfo> hostInfos = referencedServiceAddresses.get(key);
			if (hostInfos.contains(hostInfo)) {
				logger.info("address:" + hostInfo + " still been referenced for service:" + key);
				return true;
			}
		}
		return false;
	}

	public Set<HostInfo> getReferencedServiceAddresses(String serviceName) {
		Set<HostInfo> hostInfos = referencedServiceAddresses.get(serviceName);
		if (hostInfos == null || hostInfos.size() == 0) {
			logger.info("empty address list for service:" + serviceName);
		}
		return hostInfos;
	}

	public Map<String, Set<HostInfo>> getAllReferencedServiceAddresses() {
		return referencedServiceAddresses;
	}

	public String getReferencedApp(String serverAddress) {
		HostInfo hostInfo = referencedAddresses.get(serverAddress);
		String app = null;
		if (hostInfo != null) {
			app = hostInfo.getApp();
			// if (app == null && registry != null) {
			// app = registry.getServerApp(serverAddress);
			// hostInfo.setApp(app);
			// }
			return app;
		}
		return "";
	}

	public void setReferencedApp(String serverAddress, String app) {
		HostInfo hostInfo = referencedAddresses.get(serverAddress);
		if (hostInfo != null) {
			hostInfo.setApp(app);
		}
	}

	public void setServerApp(String serverAddress, String app) {
		if (registry != null) {
			registry.setServerApp(serverAddress, app);
		}
	}

	public void unregisterServerApp(String serverAddress) {
		if (registry != null) {
			registry.unregisterServerApp(serverAddress);
		}
	}

	public void setReferencedVersion(String serverAddress, String version) {
		HostInfo hostInfo = referencedAddresses.get(serverAddress);
		if (hostInfo != null) {
			hostInfo.setVersion(version);
		}
	}

	public void setServerVersion(String serverAddress, String version) {
		if (registry != null) {
			registry.setServerVersion(serverAddress, version);
		}
	}

	public String getReferencedVersion(String serverAddress) {
		HostInfo hostInfo = referencedAddresses.get(serverAddress);
		String version = null;
		if (hostInfo != null) {
			version = hostInfo.getVersion();
			// if (version == null && registry != null) {
			// version = registry.getServerVersion(serverAddress);
			// hostInfo.setVersion(version);
			// }
			return version;
		}
		return null;
	}

	public void unregisterServerVersion(String serverAddress) {
		if (registry != null) {
			registry.unregisterServerVersion(serverAddress);
		}
	}

	static class InnerServerInfoListener implements ServerInfoListener {

		@Override
		public void onServerAppChange(String serverAddress, String app) {
			HostInfo hostInfo = referencedAddresses.get(serverAddress);
			if (hostInfo != null) {
				hostInfo.setApp(app);
			}
		}

		@Override
		public void onServerVersionChange(String serverAddress, String version) {
			HostInfo hostInfo = referencedAddresses.get(serverAddress);
			if (hostInfo != null) {
				hostInfo.setVersion(version);
			}
		}
	}

	/**
	 * 
	 * @author chenchongze
	 * @param serviceName
	 * @param group
	 * @param hosts
	 */
	public void setServerService(String serviceName, String group, String hosts) throws RegistryException {
		if (registry != null) {
			registry.setServerService(serviceName, group, hosts);
			monitor.logEvent("PigeonService.setHosts", serviceName, "swimlane=" + group + "&hosts=" + hosts);
		}
	}

	public void delServerService(String serviceName, String group) throws RegistryException {
		if (registry != null) {
			registry.delServerService(serviceName, group);
			monitor.logEvent("PigeonService.delService", serviceName, "swimlane=" + group);
		}
	}
}
