package com.smartcampus.resource;

import com.smartcampus.exception.SensorUnavailableException;
import com.smartcampus.model.ErrorMessage;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;
import com.smartcampus.store.DataStore;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 *
 * @author chenuli
 */
@Produces(MediaType.APPLICATION_JSON)
public class SensorReadingResource {

    private static final Logger LOG = Logger.getLogger(SensorReadingResource.class.getName());
    private static int idCounter = 0;

    private DataStore store = DataStore.getInstance();
    private String sensorId;

    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId;
    }

    // Retrieve all readings recorded for this sensor
    @GET
    public Response getReadings() {
        LOG.info("Retrieving readings for sensor: " + sensorId);
        Sensor sensor = store.getSensor(sensorId);
        if (sensor == null) {
            LOG.severe("Cannot retrieve readings — sensor does not exist: " + sensorId);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorMessage("Not Found", 404, "Sensor not found: " + sensorId))
                    .build();
        }
        List<SensorReading> readings = store.getReadings(sensorId);
        LOG.info("Returned " + readings.size() + " reading(s) for sensor: " + sensorId);
        return Response.ok(readings).build();
    }

    // Record a new reading against this sensor
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addReading(SensorReading reading) {
        LOG.info("Recording new reading for sensor: " + sensorId);
        Sensor sensor = store.getSensor(sensorId);
        if (sensor == null) {
            LOG.severe("Cannot record reading — sensor does not exist: " + sensorId);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorMessage("Not Found", 404, "Sensor not found: " + sensorId))
                    .build();
        }

        // Reject readings for sensors under maintenance
        if ("MAINTENANCE".equalsIgnoreCase(sensor.getStatus())) {
            String errorMsg = "Sensor '" + sensorId + "' is under MAINTENANCE and cannot accept new readings.";
            LOG.severe(errorMsg);
            throw new SensorUnavailableException(errorMsg);
        }

        // Assign an ID and timestamp automatically if not provided
        if (reading.getId() == null || reading.getId().isBlank()) {
            reading.setId("RD-" + (++idCounter));
        }
        if (reading.getTimestamp() == 0) {
            reading.setTimestamp(System.currentTimeMillis());
        }

        store.addReading(sensorId, reading);

        // Reflect the latest value on the sensor itself
        sensor.setCurrentValue(reading.getValue());

        LOG.info("Reading recorded (value: " + reading.getValue() + ") for sensor: " + sensorId);
        return Response.status(Response.Status.CREATED).entity(reading).build();
    }
}