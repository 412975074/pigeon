/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.provider.util;

import java.util.List;

import com.dianping.pigeon.monitor.Monitor;
import com.dianping.pigeon.monitor.MonitorLoader;
import com.dianping.pigeon.monitor.MonitorTransaction;
import com.dianping.pigeon.remoting.common.domain.InvocationContext.TimePhase;
import com.dianping.pigeon.remoting.common.domain.InvocationContext.TimePoint;
import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
import com.dianping.pigeon.remoting.common.monitor.SizeMonitor;
import com.dianping.pigeon.remoting.common.util.Constants;
import com.dianping.pigeon.remoting.provider.domain.ProviderChannel;
import com.dianping.pigeon.remoting.provider.domain.ProviderContext;
import com.dianping.pigeon.remoting.provider.process.ProviderProcessInterceptor;
import com.dianping.pigeon.remoting.provider.process.ProviderProcessInterceptorFactory;
import com.dianping.pigeon.remoting.provider.process.statistics.ProviderStatisticsHolder;

public final class ProviderHelper {

	private static final Monitor monitor = MonitorLoader.getMonitor();

	private static ThreadLocal<ProviderContext> tlContext = new ThreadLocal<ProviderContext>();

	public static void setContext(ProviderContext context) {
		tlContext.set(context);
	}

	public static ProviderContext getContext() {
		ProviderContext context = tlContext.get();
		tlContext.remove();
		return context;
	}

	public static void clearContext() {
		tlContext.remove();
	}

	public static void writeSuccessResponse(ProviderContext context, Object returnObj) {
		if (Constants.REPLY_MANUAL && context != null) {
			context.getTimeline().add(new TimePoint(TimePhase.B, System.currentTimeMillis()));
			InvocationRequest request = context.getRequest();
			InvocationResponse response = ProviderUtils.createSuccessResponse(request, returnObj);
			ProviderChannel channel = context.getChannel();
			MonitorTransaction transaction = null;
			if (Constants.PROVIDER_CALLBACK_MONITOR_ENABLE) {
				MonitorTransaction currentTransaction = monitor.getCurrentServiceTransaction();
				try {
					if (currentTransaction == null) {
						transaction = monitor.createTransaction("PigeonServiceCallback", context.getMethodUri(),
								context);
						if (transaction != null) {
							transaction.setStatusOk();
							monitor.logEvent("PigeonService.app", request.getApp(), "");
							String reqSize = SizeMonitor.getInstance().getLogSize(request.getSize());
							if (reqSize != null) {
								monitor.logEvent("PigeonService.requestSize", reqSize, "" + request.getSize());
							}
						}
					}
				} catch (Throwable e) {
					monitor.logMonitorError(e);
				}
				if (request.getCallType() != Constants.CALLTYPE_NOREPLY) {
					try {
						channel.write(response);
					} finally {
						if (Constants.PROVIDER_CALLBACK_MONITOR_ENABLE) {
							if (response != null && response.getSize() > 0) {
								String respSize = SizeMonitor.getInstance().getLogSize(response.getSize());
								if (respSize != null) {
									monitor.logEvent("PigeonService.responseSize", respSize, "" + response.getSize());
								}
							}
							if (transaction != null) {
								try {
									transaction.complete();
								} catch (Throwable e) {
									monitor.logMonitorError(e);
								}
							}
						}
					}
				}
				ProviderStatisticsHolder.flowOut(request);
			}
			List<ProviderProcessInterceptor> interceptors = ProviderProcessInterceptorFactory.getInterceptors();
			for (ProviderProcessInterceptor interceptor : interceptors) {
				interceptor.postInvoke(request, response);
			}
		}
	}

	public static void writeFailureResponse(ProviderContext context, Throwable exeption) {
		if (Constants.REPLY_MANUAL) {
			InvocationRequest request = context.getRequest();
			InvocationResponse response = ProviderUtils.createServiceExceptionResponse(request, exeption);
			ProviderChannel channel = context.getChannel();
			channel.write(response);
			ProviderStatisticsHolder.flowOut(request);
			List<ProviderProcessInterceptor> interceptors = ProviderProcessInterceptorFactory.getInterceptors();
			for (ProviderProcessInterceptor interceptor : interceptors) {
				interceptor.postInvoke(request, response);
			}
		}
	}
}
