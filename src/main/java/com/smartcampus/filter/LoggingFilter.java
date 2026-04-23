package com.smartcampus.filter;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.util.logging.Logger;

@Provider
public class LoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOGGER = Logger.getLogger(LoggingFilter.class.getName());

    @Override
    public void filter(ContainerRequestContext req) {
        String uri = req.getUriInfo().getRequestUri().toString();
        String method = req.getMethod();
        LOGGER.info("=> [REQUEST RECEIVED] " + method + " " + uri);
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        String uri = req.getUriInfo().getRequestUri().toString();
        String method = req.getMethod();

        String statusText = (res.getStatusInfo() != null)
                ? res.getStatusInfo().getReasonPhrase()
                : "Unknown";
        int status = res.getStatus();

        LOGGER.info("<= [RESPONSE SENT] " + method + " " + uri
                + " | Status: " + status + " " + statusText);
    }
}