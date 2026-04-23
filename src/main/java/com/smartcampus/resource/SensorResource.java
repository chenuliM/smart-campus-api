package com.smartcampus.resource;

import com.smartcampus.exception.LinkedResourceNotFoundException;
import com.smartcampus.model.ErrorMessage;
import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
import com.smartcampus.store.DataStore;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 *
 * @author chenuli
 */
@Path("/sensors")
@Produces(MediaType.APPLICATION_JSON)
public class SensorResource {

    private static final Logger LOG = Logger.getLogger(SensorResource.class.getName());

    private DataStore store = DataStore.getInstance();

    @Context
    private UriInfo uriInfo;

    // List all sensors, optionally narrowed by type
    @GET
    public List<Sensor> getAllSensors(@QueryParam("type") String type) {
        LOG.info("Listing all registered sensors...");
        List<Sensor> all = new ArrayList<>(store.getSensors().values());
        if (type != null && !type.isBlank()) {
            LOG.info("Applying type filter: " + type);
            return all.stream()
                    .filter(s -> type.equalsIgnoreCase(s.getType()))
                    .collect(Collectors.toList());
        }
        return all;
    }

    // Register a new sensor (the target room must already exist)
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createSensor(Sensor sensor) {
        LOG.info("Registering sensor: " + sensor.getId());
        Room room = store.getRoom(sensor.getRoomId());
        if (room == null) {
            String errorMsg = "Room '" + sensor.getRoomId()
                    + "' does not exist. A sensor must be assigned to a valid room.";
            LOG.severe(errorMsg);
            throw new LinkedResourceNotFoundException(errorMsg);
        }
        store.addSensor(sensor);
        room.getSensorIds().add(sensor.getId());
        URI location = uriInfo.getAbsolutePathBuilder().path(sensor.getId()).build();
        LOG.info("Sensor registered successfully: " + sensor.getId());
        return Response.created(location).entity(sensor).build();
    }

    // Fetch a single sensor by its ID
    @GET
    @Path("/{sensorId}")
    public Response getSensor(@PathParam("sensorId") String sensorId) {
        LOG.info("Looking up sensor: " + sensorId);
        Sensor sensor = store.getSensor(sensorId);
        if (sensor == null) {
            LOG.severe("No sensor exists with ID: " + sensorId);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorMessage("Not Found", 404, "Sensor not found: " + sensorId))
                    .build();
        }
        return Response.ok(sensor).build();
    }

    // Remove a sensor and detach it from the parent room
    @DELETE
    @Path("/{sensorId}")
    public Response deleteSensor(@PathParam("sensorId") String sensorId) {
        LOG.info("Processing delete request for sensor: " + sensorId);
        Sensor sensor = store.getSensor(sensorId);
        if (sensor == null) {
            LOG.info("Delete target not found, sensor ID: " + sensorId);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorMessage("Not Found", 404, "Sensor not found: " + sensorId))
                    .build();
        }
        // Detach sensor from its parent room
        Room room = store.getRoom(sensor.getRoomId());
        if (room != null) {
            room.getSensorIds().remove(sensorId);
            LOG.info("Detached sensor " + sensorId + " from room " + sensor.getRoomId());
        }
        store.removeSensor(sensorId);
        LOG.info("Sensor removed successfully: " + sensorId);
        return Response.noContent().build();
    }

    // Sub-resource locator — delegates reading operations to SensorReadingResource
    @Path("/{sensorId}/readings")
    public SensorReadingResource getReadingsSubResource(@PathParam("sensorId") String sensorId) {
        return new SensorReadingResource(sensorId);
    }
}