/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.invoker.callback;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.Logger;

import com.dianping.pigeon.log.LoggerLoader;
import com.dianping.pigeon.monitor.Monitor;
import com.dianping.pigeon.monitor.MonitorLoader;
import com.dianping.pigeon.monitor.MonitorTransaction;
import com.dianping.pigeon.remoting.common.domain.InvocationContext.TimePhase;
import com.dianping.pigeon.remoting.common.domain.InvocationContext.TimePoint;
import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
import com.dianping.pigeon.remoting.common.exception.InvalidParameterException;
import com.dianping.pigeon.remoting.common.exception.RpcException;
import com.dianping.pigeon.remoting.common.monitor.SizeMonitor;
import com.dianping.pigeon.remoting.common.util.Constants;
import com.dianping.pigeon.remoting.invoker.domain.InvokerContext;
import com.dianping.pigeon.remoting.invoker.exception.RequestTimeoutException;
import com.dianping.pigeon.remoting.invoker.util.InvokerUtils;

public class ServiceFutureImpl extends CallbackFuture implements Future {

	private static final Logger logger = LoggerLoader.getLogger(ServiceFutureImpl.class);

	private static final Monitor monitor = MonitorLoader.getMonitor();

	private long timeout = Long.MAX_VALUE;

	private Thread callerThread;

	private MonitorTransaction transaction;

	private InvokerContext invocationContext;

	public ServiceFutureImpl(InvokerContext invocationContext, long timeout) {
		super();
		this.timeout = timeout;
		this.invocationContext = invocationContext;
		callerThread = Thread.currentThread();
		transaction = monitor.getCurrentCallTransaction();
	}

	@Override
	public Object get() throws InterruptedException, ExecutionException {
		try {
			return get(this.timeout);
		} catch (RequestTimeoutException e) {
			throw new ExecutionException(this.timeout + "ms timeout", e);
		} catch (InterruptedException e) {
			throw e;
		} catch (Throwable e) {
			throw new ExecutionException(e);
		}
	}

	public Object get(long timeoutMillis) throws InterruptedException, ExecutionException {
		InvocationResponse response = null;
		long start = System.currentTimeMillis();
		if (transaction != null) {
			transaction.addData("FutureTimeout", timeoutMillis);
			invocationContext.getTimeline().add(new TimePoint(TimePhase.F, start));
		}
		try {
			response = super.getResponse(timeoutMillis);
			if (transaction != null && response != null) {
				String size = SizeMonitor.getInstance().getLogSize(response.getSize());
				if (size != null) {
					transaction.logEvent("PigeonCall.responseSize", size, "" + response.getSize());
				}
				invocationContext.getTimeline().add(new TimePoint(TimePhase.R, response.getCreateMillisTime()));
				invocationContext.getTimeline().add(new TimePoint(TimePhase.F, System.currentTimeMillis()));
			}
		} catch (Throwable e) {
			RuntimeException rpcEx = null;
			if (e instanceof RpcException) {
				rpcEx = (RpcException) e;
			} else {
				rpcEx = new RpcException(e);
			}
			if (e instanceof RequestTimeoutException) {
				if (Constants.INVOKER_LOG_TIMEOUT_EXCEPTION) {
					logger.error(rpcEx);
				}
			} else {
				logger.error(rpcEx);
			}
			monitor.logError(rpcEx);
			if (transaction != null) {
				try {
					transaction.setStatusError(e);
				} catch (Throwable e2) {
					monitor.logMonitorError(e2);
				}
			}
			throw rpcEx;
		} finally {
			if (transaction != null) {
				try {
					transaction.complete(start);
				} catch (Throwable e) {
					monitor.logMonitorError(e);
				}
			}
		}
		setResponseContext(response);

		if (response.getMessageType() == Constants.MESSAGE_TYPE_SERVICE) {
			return response.getResponse();
		} else if (response.getMessageType() == Constants.MESSAGE_TYPE_EXCEPTION) {
			RpcException cause = InvokerUtils.toRpcException(response);
			logger.error("error with future call", cause);
			monitor.logError("error with future call", cause);
			throw cause;
		} else if (response.getMessageType() == Constants.MESSAGE_TYPE_SERVICE_EXCEPTION) {
			RuntimeException cause = InvokerUtils.toApplicationRuntimeException(response);
			if (Constants.INVOKER_LOG_APP_EXCEPTION) {
				logger.error("error with remote business future call", cause);
				monitor.logError("error with remote business future call", cause);
			}
			throw cause;
		} else {
			RpcException e = new InvalidParameterException("unsupported response with message type:"
					+ response.getMessageType());
			monitor.logError(e);
			throw e;
		}

	}

	@Override
	public Object get(long timeout, TimeUnit unit) throws java.lang.InterruptedException,
			java.util.concurrent.ExecutionException, java.util.concurrent.TimeoutException {
		long timeoutMs = unit.toMillis(timeout);
		try {
			return get(timeoutMs);
		} catch (RequestTimeoutException e) {
			throw new TimeoutException(timeoutMs + "ms timeout");
		} catch (InterruptedException e) {
			throw e;
		} catch (Throwable e) {
			throw new ExecutionException(e);
		}
	}

	protected void processContext() {
		Thread currentThread = Thread.currentThread();
		if (currentThread == callerThread) {
			super.processContext();
		}
	}

	@Override
	public void dispose() {
		super.dispose();
		if (transaction != null) {
			try {
				transaction.complete();
			} catch (Throwable e) {
			}
		}
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return cancel();
	}
}
