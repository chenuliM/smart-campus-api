package com.smartcampus.resource;

import com.smartcampus.exception.RoomNotEmptyException;
import com.smartcampus.model.ErrorMessage;
import com.smartcampus.model.Room;
import com.smartcampus.store.DataStore;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 *
 * @author chenuli
 */
@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
public class RoomResource {

    private static final Logger LOG = Logger.getLogger(RoomResource.class.getName());

    private DataStore store = DataStore.getInstance();

    @Context
    private UriInfo uriInfo;

    // Retrieve every room in the data store
    @GET
    public Collection<Room> getAllRooms() {
        LOG.info("Retrieving complete list of rooms.");
        return new ArrayList<>(store.getRooms().values());
    }

    // Register a new room via POST
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createRoom(Room room) {
        LOG.info("Creating new room with ID: " + room.getId());
        store.addRoom(room);
        URI location = uriInfo.getAbsolutePathBuilder().path(room.getId()).build();
        LOG.info("Room created successfully: " + room.getId());
        return Response.created(location).entity(room).build();
    }

    // Look up a specific room by its ID
    @GET
    @Path("/{roomId}")
    public Response getRoom(@PathParam("roomId") String roomId) {
        LOG.info("Looking up room: " + roomId);
        Room room = store.getRoom(roomId);
        if (room == null) {
            LOG.severe("No room exists with ID: " + roomId);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorMessage("Not Found", 404, "Room not found: " + roomId))
                    .build();
        }
        return Response.ok(room).build();
    }

    // Remove a room (fails if sensors are still linked)
    @DELETE
    @Path("/{roomId}")
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        LOG.info("Processing delete request for room: " + roomId);
        Room room = store.getRoom(roomId);
        if (room == null) {
            LOG.info("Delete target not found, room ID: " + roomId);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorMessage("Not Found", 404, "Room not found: " + roomId))
                    .build();
        }
        if (!room.getSensorIds().isEmpty()) {
            String errorMsg = "Room " + roomId + " still has " + room.getSensorIds().size()
                    + " sensor(s) linked. Detach all sensors before removing the room.";
            LOG.severe(errorMsg);
            throw new RoomNotEmptyException(errorMsg);
        }
        store.removeRoom(roomId);
        LOG.info("Room removed successfully: " + roomId);
        return Response.noContent().build();
    }
}