/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.demo.typical;

import java.util.concurrent.Future;

import com.dianping.pigeon.container.SpringContainer;
import com.dianping.pigeon.demo.EchoService;
import com.dianping.pigeon.remoting.invoker.callback.ServiceFutureFactory;
import com.dianping.pigeon.util.ContextUtils;

public class Client {

	private static SpringContainer CLIENT_CONTAINER = new SpringContainer(
			"classpath*:META-INF/spring/typical/invoker.xml");

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		CLIENT_CONTAINER.start();

		EchoService echoService = (EchoService) CLIENT_CONTAINER.getBean("echoService");
		EchoService echoServiceWithCallback = (EchoService) CLIENT_CONTAINER.getBean("echoServiceWithCallback");
		EchoService echoServiceWithFuture = (EchoService) CLIENT_CONTAINER.getBean("echoServiceWithFuture");

		int i = 0;
		while (true) {
			try {
				ContextUtils.putRequestContext("key1", "1");
				System.out.println(echoService.echo("hi," + i++));

				echoServiceWithFuture.echo("hi with future," + i++);
				Future<String> future = ServiceFutureFactory.getFuture(String.class);
				System.out.println(future.get());

				// System.out.println("response:" +
				// ContextUtils.getResponseContext("key1"));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
