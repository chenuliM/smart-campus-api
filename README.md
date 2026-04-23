# Smart Campus - Sensor & Room Management API

**Module:** 5COSC022W Client-Server Architectures (2025/26)  
**Student:**  Chenuli Kodikara-w21531

A RESTful API supporting the university "Smart Campus" programme, developed with **JAX-RS (Jersey)** and deployed as a **WAR** on Apache Tomcat. The service manages **rooms**, **sensors** installed within them, and a **time-series log of sensor readings**. All data resides **in memory** no external database is used.

---

## How to build and run

# 1. Requirements
Java JDK 8 or higher
Apache Tomcat 9 (e.g., 9.0.100)
NetBeans IDE

# 2. Download Project
git clone https://github.com/chenuliM/smart-campus-api

# 3. Configure Tomcat in NetBeans
Open NetBeans
Go to Services → Servers
Right-click → Add Server
Select Apache Tomcat
Choose your Tomcat installation folder

# 4. Open Project
Go to File → Open Project
Select the project folder

# 5. Run the Project
Right-click the project → Run

# 6. Access API
Open in browser or Postman:

http://localhost:8080/api/v1


The entry point is declared with `@ApplicationPath("/api/v1")` on the `SmartCampusApplication` class, which extends `javax.ws.rs.core.Application`.

---

## API Overview

The API reflects the physical campus layout: **rooms** serve as the top-level resource, **sensors** are installed within rooms, and each sensor accumulates **readings** over time. A discovery endpoint at the API root exposes metadata and navigational links so that clients always have a well-defined starting point.

---

### Endpoints

| Method      | Path                                  | Description                                              |
| ----------- | ------------------------------------- | -------------------------------------------------------- |
| GET         | `/api/v1`                             | Discovery — metadata, version, HATEOAS links             |
| GET, POST   | `/api/v1/rooms`                       | List all rooms / create a new room                       |
| GET, DELETE | `/api/v1/rooms/{roomId}`              | Get room detail / delete (blocked if sensors are linked) |
| GET, POST   | `/api/v1/sensors`                     | List sensors (optional `?type=` filter) / register       |
| GET, POST   | `/api/v1/sensors/{sensorId}/readings` | Reading history / append a new reading                   |

**Models:** `Room`, `Sensor`, `SensorReading`, `ErrorMessage`.

---

## Sample `curl` commands

```bash
# Discovery
curl -i "http://localhost:8080/api/v1"

# List all rooms
curl -i "http://localhost:8080/api/v1/rooms"

# Create a room
curl -i -X POST -H "Content-Type: application/json" \
  -d '{"id":"SCI-401","name":"Science Wing Seminar","capacity":55}' \
  "http://localhost:8080/api/v1/rooms"

# Get a single room
curl -i "http://localhost:8080/api/v1/rooms/SCI-401"

# Delete a room
curl -i -X DELETE "http://localhost:8080/api/v1/rooms/SCI-401"

# List sensors (with optional type filter)
curl -i "http://localhost:8080/api/v1/sensors"
curl -i "http://localhost:8080/api/v1/sensors?type=Temperature"

# Register a sensor
curl -i -X POST -H "Content-Type: application/json" \
  -d '{"id":"TEMP-101","type":"Temperature","status":"ACTIVE","currentValue":20.9,"roomId":"SCI-401"}' \
  "http://localhost:8080/api/v1/sensors"

# Post a reading
curl -i -X POST -H "Content-Type: application/json" \
  -d '{"timestamp":1713868800000,"value":21.7}' \
  "http://localhost:8080/api/v1/sensors/TEMP-101/readings"

# Get readings history
curl -i "http://localhost:8080/api/v1/sensors/TEMP-101/readings"
```

---

## Written Report (Answers to the Brief)

### Part 1: Service Architecture & Setup

**Q: Explain the default lifecycle of a JAX-RS Resource class. Is a new instance instantiated for every incoming request, or does the runtime treat it as a
singleton? Elaborate on how this architectural decision impacts the way you manage and synchronize your in-memory data structures (maps/lists) to prevent data loss or race conditions.**

By default, JAX-RS employs a per-request lifecycle: the runtime creates a new instance of each resource class (e.g. `RoomResource`, `SensorResource`) for every HTTP request and disposes of it once the response has been dispatched. This ensures that one request cannot inadvertently interfere with another's state, but it also means that any data held in instance fields does not survive between requests.

To maintain state across requests, the application relies on a singleton `DataStore` with a private constructor and a static `getInstance()` accessor, guaranteeing that a single shared instance exists throughout the application's lifetime. Every resource class obtains a reference to this same object. Since Tomcat processes requests concurrently on multiple threads, the `DataStore` must handle concurrent access safely. It achieves this by backing its room, sensor, and reading collections with `ConcurrentHashMap`, which supports concurrent reads without locking and applies fine-grained segment-level locks during writes. Lists of sensor readings use `CopyOnWriteArrayList`, a structure well suited to workloads where reads vastly outnumber writes. The `computeIfAbsent` method is used when initialising a new reading list to prevent a race condition where two threads could simultaneously attempt to create the same entry. Together, these `java.util.concurrent` primitives remove any need for explicit `synchronized` blocks, yielding improved throughput under concurrent load.

**Q: Why is the provision of "Hypermedia" (links and navigation within responses) considered a hallmark of advanced RESTful design (HATEOAS)? How does this approach benefit client developers compared to static documentation?**

HATEOAS (Hypermedia as the Engine of Application State) stipulates that API responses should embed navigational links, enabling clients to discover available actions at runtime rather than relying on hardcoded URLs. The `DiscoveryResource` at `GET /api/v1` illustrates this by constructing links to `/rooms` and `/sensors` dynamically through `@Context UriInfo`, so the URLs automatically adjust to whichever host and port the server happens to be running on.

This confers two principal benefits for client developers. First, it decouples the client from the server's URL layout — should endpoints be renamed or versioned, clients that follow embedded links will continue to function without code changes. Second, it makes the API self-describing; a developer can begin at the root endpoint and navigate to every available resource simply by following links, thereby reducing dependence on external documentation.

---

### Part 2: Room Management

**Q: When returning a list of rooms, what are the implications of returning only IDs versus returning the full room objects? Consider network bandwidth and client side processing.**

The `GET /rooms` endpoint returns complete `Room` objects — including `id`, `name`, `capacity`, and `sensorIds` — rather than just a list of identifiers. Were only IDs returned, the client would need to issue a separate `GET /rooms/{roomId}` call for each room to obtain its details. This is commonly referred to as the N+1 problem, and for a campus with potentially hundreds of rooms it would generate excessive network round-trips and place unnecessary load on the server. Returning full objects enables the client to acquire all required information in a single request, which is substantially more efficient. The trade-off is a marginally larger response payload, but for structured data at this scale the bandwidth cost is negligible compared to the latency savings.

**Q: Is the DELETE operation idempotent in your implementation? Provide a detailed justification by describing what happens if a client mistakenly sends the exact same DELETE request for a room multiple times.**

Yes, the `DELETE /rooms/{roomId}` operation is idempotent. The first successful invocation removes the room from the `DataStore` and returns `204 No Content`. If the same request is issued again, the room is no longer present, so the method responds with `404 Not Found`. Although the HTTP status code differs between the two calls, the resulting server-side state is identical after both — the room is absent. According to the HTTP specification, idempotency requires that "the side-effects of N > 0 identical requests is the same as for a single request," which is precisely what this implementation delivers. Furthermore, if the room still contains assigned sensors, a `RoomNotEmptyException` is thrown, returning `409 Conflict` and safeguarding against orphaned data.

---

### Part 3: Sensor Operations & Linking

**Q: We explicitly use the @Consumes (MediaType.APPLICATION_JSON) annotation on the POST method. Explain the technical consequences if a client attempts to send data in a different format, such as text/plain or application/xml. How does JAX-RS handle this mismatch?**

The `@Consumes(MediaType.APPLICATION_JSON)` annotation on the `POST /sensors` method tells the JAX-RS runtime to accept only request bodies whose `Content-Type` is `application/json`. If a client submits data in a different format — for example `text/plain` or `application/xml` — Jersey automatically rejects the request with an HTTP `415 Unsupported Media Type` response before the method body is ever executed. This acts as an effective gatekeeper, ensuring that only correctly formatted JSON payloads reach the application logic and preventing parsing errors or potential security issues from unexpected input formats.

**Q: You implemented this filtering using @QueryParam. Contrast this with an alternative design where the type is part of the URL path (e.g., /api/vl/sensors/type/CO2). Why is the query parameter approach generally considered superior for filtering and searching collections?**

The `GET /sensors` endpoint supports an optional `@QueryParam("type")` filter. When provided (e.g. `?type=Temperature`), only matching sensors are returned; otherwise the full collection is returned. This design is preferable to a path-based alternative like `/sensors/type/CO2` for several reasons. First, the URI `/sensors` consistently identifies the sensor collection as a single resource, and the query parameter merely adjusts the view — this aligns with REST conventions where the path identifies the resource and query parameters refine the representation. Second, query parameters are composable: additional filters (e.g. `?type=CO2&status=ACTIVE`) can be introduced without requiring new `@Path` annotations. Third, this approach follows the established patterns used by prominent APIs like GitHub and Stripe, making it immediately recognisable to developers.

---

### Part 4: Deep Nesting with Sub-Resources

**Q: Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path (e.g., sensors/{id}/readings/{rid}) in one massive controller class?**

In `SensorResource`, the method annotated with `@Path("/{sensorId}/readings")` carries no HTTP method annotation. Instead, it functions as a sub-resource locator that constructs and returns a `SensorReadingResource` object, passing the `sensorId` as context. Jersey then dispatches the actual HTTP method (`GET` or `POST`) to the corresponding method within that sub-resource class.

This pattern provides several architectural advantages over defining all nested paths in a single controller. First, it enforces separation of concerns — sensor CRUD operations and reading history management are logically distinct responsibilities and belong in separate classes. Second, it promotes extensibility; should future requirements introduce additional nested resources (e.g. `/sensors/{id}/alerts`), each can be implemented as a standalone class without inflating the complexity of `SensorResource`. Third, the sub-resource class can be unit-tested independently by simply constructing it with a sensor ID, without needing to invoke the parent resource. This modular approach keeps individual classes focused and maintainable as the API evolves.

---

### Part 5: Error Handling, Exception Mapping & Logging

**Q: Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?**

When a client sends a `POST /sensors` request containing a `roomId` that does not correspond to any existing room, the `SensorResource` throws a `LinkedResourceNotFoundException`, which is mapped to `422 Unprocessable Entity`. Returning `404 Not Found` in this context would be misleading because the target endpoint `/sensors` does exist and is fully operational. The problem lies not with the URL itself but with the semantic validity of the request body. The JSON is syntactically correct, yet it references a room the server cannot locate. HTTP 422 conveys exactly this situation: "the request was well-formed but could not be processed due to semantic errors," giving the client a clear and precise understanding of the failure.

**Q: From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?**

The `GenericExceptionMapper` implements `ExceptionMapper<Throwable>` to catch any unhandled runtime exception and return a generic `500 Internal Server Error` with the message "An unexpected error occurred." The full exception details are logged server-side via `Logger.log(Level.SEVERE, ...)` but are never included in the client-facing response.

Exposing raw stack traces to external consumers constitutes an information-disclosure vulnerability. A stack trace reveals internal package and class names, thereby exposing the application's architecture. It can also contain framework version numbers, which enables attackers to search for known CVEs targeting those specific versions. File paths within the trace may disclose the server's directory structure and operating system. Method names and line numbers provide a detailed map of the codebase that can be used to identify potential injection points or logic flaws. This category of vulnerability is recognised by OWASP as part of their Top 10 security risks. Returning only a generic error message to clients is therefore an essential security practice.

**Q: Why is it advantageous to use JAX-RS filters for cross-cutting concerns like logging, rather than manually inserting Logger.info() statements inside every single resource method?**

The `LoggingFilter` class implements both `ContainerRequestFilter` and `ContainerResponseFilter` and is registered via `@Provider`. It intercepts every incoming request to log the HTTP method and URI, and every outgoing response to log the corresponding status code.

Employing a filter for this cross-cutting concern is advantageous because it eliminates code duplication — the logging logic is authored once rather than replicated across every resource method. It also ensures consistency, since every endpoint is logged in the same format regardless of which resource handles the request. If the log format needs to change, only the filter class requires modification. Finally, it keeps the resource classes focused solely on their primary responsibility — handling business logic — in accordance with the single-responsibility principle.

---

## Video demonstration

The full video walkthrough was recorded separately and submitted via **Blackboard**.

---

## References

- Course module: **5COSC022W** — University of Westminster
- No Spring Boot or database technology was used, as required by the coursework brief
