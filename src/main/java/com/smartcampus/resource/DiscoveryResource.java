package com.smartcampus.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author chenuli
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class DiscoveryResource {

    @Context
    private UriInfo uriInfo;

    @GET
    public Map<String, Object> discover() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "Smart Campus API");
        info.put("version", "1.0");
        info.put("description", "Room and Sensor Management Service for the Smart Campus initiative");

        String baseUri = uriInfo.getBaseUri().toString();
        // Strip trailing slash for cleaner link formatting
        if (baseUri.endsWith("/")) {
            baseUri = baseUri.substring(0, baseUri.length() - 1);
        }

        Map<String, String> contact = new LinkedHashMap<>();
        contact.put("name", "admin");
        contact.put("email", "admin@smartcampus.ac.lk");
        info.put("contact", contact);

        Map<String, String> links = new LinkedHashMap<>();
        links.put("rooms", baseUri + "/rooms");
        links.put("sensors", baseUri + "/sensors");
        info.put("links", links);

        return info;
    }
}