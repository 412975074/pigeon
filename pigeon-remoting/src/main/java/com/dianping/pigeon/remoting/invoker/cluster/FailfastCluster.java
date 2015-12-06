package com.dianping.pigeon.remoting.invoker.cluster;

import org.apache.logging.log4j.Logger;

import com.dianping.pigeon.log.LoggerLoader;
import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
import com.dianping.pigeon.remoting.common.exception.NetworkException;
import com.dianping.pigeon.remoting.common.process.ServiceInvocationHandler;
import com.dianping.pigeon.remoting.common.util.Constants;
import com.dianping.pigeon.remoting.invoker.Client;
import com.dianping.pigeon.remoting.invoker.ClientManager;
import com.dianping.pigeon.remoting.invoker.config.InvokerConfig;
import com.dianping.pigeon.remoting.invoker.domain.InvokerContext;
import com.dianping.pigeon.remoting.invoker.exception.RemoteInvocationException;
import com.dianping.pigeon.remoting.invoker.exception.RequestTimeoutException;
import com.dianping.pigeon.remoting.invoker.exception.ServiceUnavailableException;
import com.dianping.pigeon.remoting.invoker.util.InvokerUtils;

public class FailfastCluster implements Cluster {

	private ClientManager clientManager = ClientManager.getInstance();

	private static final Logger logger = LoggerLoader.getLogger(FailfastCluster.class);

	@Override
	public InvocationResponse invoke(ServiceInvocationHandler handler, InvokerContext invocationContext)
			throws Throwable {
		InvokerConfig<?> invokerConfig = invocationContext.getInvokerConfig();
		InvocationRequest request = InvokerUtils.createRemoteCallRequest(invocationContext, invokerConfig);

		boolean timeoutRetry = invokerConfig.isTimeoutRetry();
		if (!timeoutRetry) {
			Client remoteClient = clientManager.getClient(invokerConfig, request, null);
			invocationContext.setClient(remoteClient);
			try {
				return handler.handle(invocationContext);
			} catch (NetworkException e) {
				if (Constants.INVOKER_RETRY_NETWORK_EXCEPTION) {
					remoteClient = clientManager.getClient(invokerConfig, request, null);
					invocationContext.setClient(remoteClient);
					return handler.handle(invocationContext);
				} else {
					throw e;
				}
			}
		} else {
			int retry = invokerConfig.getRetries();
			RequestTimeoutException lastError = null;
			int maxInvokeTimes = retry + 1;
			int invokeTimes = 0;
			for (int index = 0; index < maxInvokeTimes; index++) {
				Client clientSelected = null;
				try {
					clientSelected = clientManager.getClient(invokerConfig, request, null);
				} catch (ServiceUnavailableException e) {
					if (invokeTimes > 0) {
						logger.error("Invoke method[" + invocationContext.getMethodName() + "] on service["
								+ invokerConfig.getUrl() + "] failed with " + invokeTimes + " times");
						throw lastError;
					} else {
						throw e;
					}
				}
				try {
					invokeTimes++;
					invocationContext.setClient(clientSelected);
					InvocationResponse response = null;
					try {
						response = handler.handle(invocationContext);
					} catch (NetworkException e) {
						if (Constants.INVOKER_RETRY_NETWORK_EXCEPTION) {
							clientSelected = clientManager.getClient(invokerConfig, request, null);
							invocationContext.setClient(clientSelected);
							response = handler.handle(invocationContext);
						} else {
							throw e;
						}
					}

					if (lastError != null) {
						logger.warn("Retry method[" + invocationContext.getMethodName() + "] on service["
								+ invokerConfig.getUrl() + "] succeed after " + invokeTimes
								+ " times, last failed error: " + lastError.getMessage(), lastError);
					}
					return response;
				} catch (RequestTimeoutException e) {
					lastError = e;
				}
			}
			if (lastError != null) {
				throw lastError;
			} else {
				throw new RemoteInvocationException("Invoke method[" + invocationContext.getMethodName()
						+ "] on service[" + invokerConfig.getUrl() + "] failed with " + invokeTimes
						+ " times, last error: " + (lastError != null ? lastError.getMessage() : ""), lastError != null
						&& lastError.getCause() != null ? lastError.getCause() : lastError);
			}
		}
	}

	@Override
	public String getName() {
		return Constants.CLUSTER_FAILFAST;
	}

}
