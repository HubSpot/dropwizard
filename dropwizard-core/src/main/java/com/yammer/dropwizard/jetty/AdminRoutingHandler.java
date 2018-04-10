package com.yammer.dropwizard.jetty;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * A Jetty router which routes /admin requests to the admin handler.
 */
public class AdminRoutingHandler extends AbstractHandler {
  private final Handler appHandler;
  private final Handler adminHandler;

  public AdminRoutingHandler(Handler appHandler, Handler adminHandler) {
    this.appHandler = appHandler;
    this.adminHandler = adminHandler;

    addBean(appHandler);
    addBean(adminHandler);
  }

  @Override
  public void handle(String target,
                     Request baseRequest,
                     HttpServletRequest request,
                     HttpServletResponse response) throws IOException, ServletException {
    final Handler handler;
    if (baseRequest.getRequestURI().startsWith("/admin")) {
      handler = adminHandler;
    } else {
      handler = appHandler;
    }

    handler.handle(target, baseRequest, request, response);
  }
}

