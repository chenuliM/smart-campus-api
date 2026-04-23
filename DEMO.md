## Demo

A RESTful Smart Campus API implemented with JAX-RS and running on Tomcat. It handles rooms, sensors, and real-time as well as historical sensor data, all kept in memory.

---

### Step 1: API Discovery

Verify the API is reachable. The discovery endpoint provides metadata (name, version, contact info) along with HATEOAS-style links to the primary resource collections. These links are **constructed dynamically** via `UriInfo`, ensuring they remain valid across different deployment environments.

**Expected:** `200 OK` with JSON containing `name`, `version`, `contact`, and `links`.

```bash
curl -i "http://localhost:8080/api/v1"
```

---

### Step 2: List Pre-loaded Rooms

Retrieve all rooms. The API is pre-populated with initial data (`R201`, `R202`).

**Expected:** `200 OK` with a JSON array of room objects.

```bash
curl -i "http://localhost:8080/api/v1/rooms"
```

---

### Step 3: Create a New Room

Add room `SCI-401`. POST responds with `201 Created` and includes a `Location` header generated using `UriInfo`.

**Expected:** `201 Created`, `Location: .../api/v1/rooms/SCI-401`, body contains the newly created room JSON.

```bash
curl -i -X POST \
  -H "Content-Type: application/json" \
  -d '{"id":"SCI-401","name":"Science Wing Seminar","capacity":55}' \
  "http://localhost:8080/api/v1/rooms"
```

---

### Step 4: Verify the New Room

Fetch `SCI-401` by ID to confirm it was stored in the in-memory `DataStore`.

**Expected:** `200 OK` with the `SCI-401` room JSON.

```bash
curl -i "http://localhost:8080/api/v1/rooms/SCI-401"
```

---

### Step 5: Error — Sensor in a Non-Existent Room (422)

Attempt to register a sensor into room `FAKE-ROOM`. This triggers `LinkedResourceNotFoundException` → mapped to **422** by `LinkedResourceNotFoundExceptionMapper`. The response uses the `ErrorMessage` POJO.

**Expected:** `422 Unprocessable Entity` with structured JSON error.

```bash
curl -i -X POST \
  -H "Content-Type: application/json" \
  -d '{"id":"X-1","type":"CO2","status":"ACTIVE","currentValue":0,"roomId":"FAKE-ROOM"}' \
  "http://localhost:8080/api/v1/sensors"
```

---

### Step 6: Register a Sensor Successfully

Register temperature sensor `TEMP-101` into `SCI-401`. If the room does not exist, a 422 is returned (demonstrated above).

**Expected:** `201 Created`, `Location` header, sensor JSON in body, `SCI-401.sensorIds` now includes `TEMP-101`.

```bash
curl -i -X POST \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-101","type":"Temperature","status":"ACTIVE","currentValue":20.9,"roomId":"SCI-401"}' \
  "http://localhost:8080/api/v1/sensors"
```

---

### Step 7: Filter Sensors by Type

Leverage `@QueryParam("type")` to narrow results. Only sensors whose type matches `Temperature` are returned.

**Expected:** `200 OK` with a filtered JSON array (includes seed sensor `S101` and the newly created `TEMP-101`).

```bash
curl -i "http://localhost:8080/api/v1/sensors?type=Temperature"
```

---

### Step 8: Add a Sensor Reading

POST a reading to the sub-resource `/sensors/TEMP-101/readings`. The server auto-generates the `id` when omitted and updates `Sensor.currentValue` to `21.7`. This uses the **sub-resource locator** pattern — `SensorResource` delegates to `SensorReadingResource`.

**Expected:** `201 Created` with reading JSON (auto-generated `id`).

```bash
curl -i -X POST \
  -H "Content-Type: application/json" \
  -d '{"timestamp":1713868800000,"value":21.7}' \
  "http://localhost:8080/api/v1/sensors/TEMP-101/readings"
```

---

### Step 9: Get Sensor Readings History

Retrieve all readings for `TEMP-101`. Returns the complete history list.

**Expected:** `200 OK` with JSON array of readings.

```bash
curl -i "http://localhost:8080/api/v1/sensors/TEMP-101/readings"
```

---

### Step 10: Error — Reading on a MAINTENANCE Sensor (403)

Attempt to add a reading to seed sensor `S103` which is in `MAINTENANCE` mode. This triggers `SensorUnavailableException` → mapped to **403 Forbidden** by `SensorUnavailableExceptionMapper`.

**Expected:** `403 Forbidden` with `ErrorMessage` JSON.

```bash
curl -i -X POST \
  -H "Content-Type: application/json" \
  -d '{"timestamp":1713868800000,"value":20.2}' \
  "http://localhost:8080/api/v1/sensors/S103/readings"
```

---

### Step 11: Error — Deleting a Room in Use (409)

Attempt to delete `SCI-401` while `TEMP-101` is still assigned to it. This triggers `RoomNotEmptyException` → mapped to **409 Conflict** by `RoomNotEmptyExceptionMapper`. Data integrity is enforced.

**Expected:** `409 Conflict` with `ErrorMessage` JSON.

```bash
curl -i -X DELETE "http://localhost:8080/api/v1/rooms/SCI-401"
```

---

### Step 12: Error — Unsupported Media Type (415)

Send `Content-Type: text/plain` rather than `application/json`. Jersey's `@Consumes(APPLICATION_JSON)` rejects the request before the handler method executes.

**Expected:** `415 Unsupported Media Type`.

```bash
curl -i -X POST \
  -H "Content-Type: text/plain" \
  -d 'This is not valid JSON' \
  "http://localhost:8080/api/v1/sensors"
```

---

### Step 13: Error — Internal Server Error with No Stack Trace (500)

Send syntactically malformed JSON. The invalid body triggers a parsing exception that no specific `ExceptionMapper` handles, so the `GenericExceptionMapper` catches it. It returns a clean `ErrorMessage` with a generic description — **no stack trace or internal details are exposed** to the client. Full exception details are only logged on the server side.

**Expected:** `500 Internal Server Error` with a clean JSON body: `{"error":"Internal Server Error","code":500,"message":"An unexpected error occurred."}` — no Java class names or stack frames visible.

```bash
curl -i -X POST \
  -H "Content-Type: application/json" \
  -d '{"id": INVALID_JSON!!}' \
  "http://localhost:8080/api/v1/rooms"
```

---

### Step 14: Delete the Sensor

Delete sensor `TEMP-101`. The `DELETE` handler detaches the sensor from room `SCI-401` (removes it from `sensorIds`) and removes it from the `DataStore`. The room's `sensorIds` list is now empty, which is required before the room itself can be deleted.

**Expected:** `204 No Content`.

```bash
curl -i -X DELETE "http://localhost:8080/api/v1/sensors/TEMP-101"
```

---

### Step 15: Delete the Room (Success)

With no sensors assigned, the DELETE on `SCI-401` succeeds. The room is removed from the `DataStore`.

**Expected:** `204 No Content`.

```bash
curl -i -X DELETE "http://localhost:8080/api/v1/rooms/SCI-401"
```

---

### Step 16: DELETE Idempotency Demo

Delete `SCI-401` again. The room no longer exists, so the server responds with `404 Not Found`. The server state (room absent) is identical after both calls, demonstrating that the operation is **idempotent in terms of state**.

**Expected:** `404 Not Found` with `ErrorMessage` JSON. Server state is unchanged from Step 15.

```bash
curl -i -X DELETE "http://localhost:8080/api/v1/rooms/SCI-401"
```

---

**Tools used:** Postman and/or `curl` in the terminal.

The full video was recorded separately and submitted via **Blackboard** as required by the module brief.
