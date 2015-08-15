/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import com.dianping.pigeon.log.LoggerLoader;

public final class ContextUtils {

	private ContextUtils() {
	}

	private static final Logger logger = LoggerLoader
			.getLogger(ContextUtils.class);

	private static ThreadLocal<Map> localContext = new ThreadLocal<Map>();

	private static ThreadLocal<Map<String, Serializable>> globalContext = new ThreadLocal<Map<String, Serializable>>();

	private static ThreadLocal<Map<String, Serializable>> requestContext = new ThreadLocal<Map<String, Serializable>>();

	private static ThreadLocal<Map<String, Serializable>> responseContext = new ThreadLocal<Map<String, Serializable>>();

	public static void init() {
	}

	public static void putLocalContext(Object key, Object value) {
		Map<Object, Object> context = localContext.get();
		if (context == null) {
			context = new HashMap<Object, Object>();
			localContext.set(context);
		}
		context.put(key, value);
	}

	public static Map getLocalContext() {
		return localContext.get();
	}

	public static Object getLocalContext(Object key) {
		Map context = localContext.get();
		if (context == null) {
			return null;
		}
		return context.get(key);
	}

	public static void clearLocalContext() {
		Map context = localContext.get();
		if (context != null) {
			context.clear();
		}
		localContext.remove();
	}

	public static void putGlobalContext(String key, Serializable value) {
		Map<String, Serializable> context = globalContext.get();
		if (context == null) {
			context = new HashMap<String, Serializable>();
			globalContext.set(context);
		}
		context.put(key, value);
	}

	public static void setGlobalContext(Map<String, Serializable> context) {
		globalContext.set(context);
	}

	public static Map<String, Serializable> getGlobalContext() {
		return globalContext.get();
	}

	public static Serializable getGlobalContext(String key) {
		Map<String, Serializable> context = globalContext.get();
		if (context == null) {
			return null;
		}
		return context.get(key);
	}

	public static void clearGlobalContext() {
		Map<String, Serializable> context = globalContext.get();
		if (context != null) {
			context.clear();
		}
		globalContext.remove();
	}

	public static void putRequestContext(String key, Serializable value) {
		Map<String, Serializable> context = requestContext.get();
		if (context == null) {
			context = new HashMap<String, Serializable>();
			requestContext.set(context);
		}
		context.put(key, value);
	}

	public static Map<String, Serializable> getRequestContext() {
		return requestContext.get();
	}

	public static Serializable getRequestContext(String key) {
		Map<String, Serializable> context = requestContext.get();
		if (context == null) {
			return null;
		}
		return context.get(key);
	}

	public static void clearRequestContext() {
		Map<String, Serializable> context = requestContext.get();
		if (context != null) {
			context.clear();
		}
		requestContext.remove();
	}

	public static void putResponseContext(String key, Serializable value) {
		Map<String, Serializable> context = responseContext.get();
		if (context == null) {
			context = new HashMap<String, Serializable>();
			responseContext.set(context);
		}
		context.put(key, value);
	}

	public static Map<String, Serializable> getResponseContext() {
		return responseContext.get();
	}

	public static Serializable getResponseContext(String key) {
		Map<String, Serializable> context = responseContext.get();
		if (context == null) {
			return null;
		}
		return context.get(key);
	}

	public static void setResponseContext(Map<String, Serializable> context) {
		responseContext.set(context);
	}

	public static void clearResponseContext() {
		Map<String, Serializable> context = responseContext.get();
		if (context != null) {
			context.clear();
		}
		responseContext.remove();
	}
}
