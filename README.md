# Smart Campus - Sensor & Room Management API

**Module:** 5COSC022W Client-Server Architectures (2025/26)  
**Student:**  Chenuli Kodikara-w2153120

A RESTful API supporting the university "Smart Campus" programme, developed with **JAX-RS (Jersey)** and deployed as a **WAR** on Apache Tomcat. The service manages **rooms**, **sensors** installed within them, and a **time-series log of sensor readings**. All data resides **in memory** no external database is used.

---

## How to build and run

### 1. Requirements
Java JDK 8 or higher
Apache Tomcat 9 (e.g., 9.0.100)
NetBeans IDE

### 2. Download Project
git clone https://github.com/chenuliM/smart-campus-api

### 3. Configure Tomcat in NetBeans
Open NetBeans
Go to Services → Servers
Right-click → Add Server
Select Apache Tomcat
Choose your Tomcat installation folder

### 4. Open Project
Go to File → Open Project
Select the project folder

### 5. Run the Project
Right-click the project → Run

### 6. Access API
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


###Part 1: Service Architecture & Setup

**Q: Explain the default lifecycle of a JAX-RS Resource class. Is a new instance instantiated for every incoming request, or does the runtime treat it as a singleton? Elaborate on how this architectural decision impacts the way you manage and synchronize your in-memory data structures.**

By default, JAX-RS uses a per-request lifecycle a new instance of each resource class (RoomResource, SensorResource, etc.) is created for every HTTP request and discarded once the response is sent. This means instance fields cannot hold state between requests.

To maintain state across requests, the application uses a singleton DataStore with a private constructor and a static getInstance() accessor, ensuring a single shared instance exists for the application's lifetime. Every resource class calls this to obtain a reference to the same object.
Since Tomcat handles requests concurrently across multiple threads, DataStore uses ConcurrentHashMap for its rooms, sensors, and readings maps supporting concurrent reads without locking and applying fine-grained locks on writes. 

Sensor reading lists use CopyOnWriteArrayList, suited to read-heavy workloads. The computeIfAbsent call in addReading atomically initialises a new list only if one doesn't already exist, preventing a race condition where two threads could simultaneously create duplicate entries for the same sensor. Together these java.util.concurrent primitives eliminate the need for explicit synchronized blocks.

**Q: Why is the provision of "Hypermedia" (HATEOAS) considered a hallmark of advanced RESTful design? How does this approach benefit client developers compared to static documentation?**

HATEOAS (Hypermedia as the Engine of Application State) requires that API responses embed navigational links, enabling clients to discover available actions at runtime rather than relying on hardcoded URLs. The DiscoveryResource at GET /api/v1 illustrates this by constructing links to /rooms and /sensors dynamically via @Context UriInfo, so the URLs automatically reflect whichever host and port the server runs on.

This benefits client developers in two principal ways. First, it decouples the client from the server's URL structure if endpoints are renamed or versioned, clients following embedded links continue working without code changes. Second, it makes the API self-describing; a developer can start at the root and navigate to every resource by following links, reducing dependence on external documentation that can go stale.

---

## Part 2: Room Management

**Q: When returning a list of rooms, what are the implications of returning only IDs versus returning the full room objects? Consider network bandwidth and client side processing.**

The GET /rooms endpoint returns complete Room objects id, name, capacity, and sensorIds rather than just identifiers. If only IDs were returned, the client would need to issue a separate GET /rooms/{roomId} request for each room to obtain its details. This is the N+1 problem, and for a campus with hundreds of rooms it would generate excessive network round-trips and unnecessary server load. 

Returning full objects lets the client acquire all required information in a single request, which is substantially more efficient. The trade-off is a marginally larger payload, but for structured data at this scale the bandwidth cost is negligible compared to the latency savings.

**Q: Is the DELETE operation idempotent in your implementation? Provide a detailed justification by describing what happens if a client mistakenly sends the exact same DELETE request for a room multiple times.**

Yes, DELETE /rooms/{roomId} is idempotent. The first successful call removes the room from the DataStore and returns 204 No Content. If the same request is sent again, the room is no longer present, so the method returns 404 Not Found. Although the HTTP status code differs between the two calls, the resulting server-side state is identical after both the room is absent.

The HTTP specification defines idempotency as requiring that the side-effects of N identical requests equal those of a single request, which this implementation satisfies. Additionally, if the room still has sensors linked at the time of the first call, RoomNotEmptyException is thrown and mapped to 409 Conflict, preventing orphaned sensor data.

---

## Part 3: Sensor Operations & Linking

**Q: Explain the technical consequences if a client attempts to send data in a different format such as text/plain or application/xml. How does JAX-RS handle this mismatch?**

The @Consumes (MediaType.APPLICATION_JSON) annotation on POST /sensors tells the Jersey runtime to only accept requests whose Content-Type header is application/json. If a client submits data as text/plain or application/xml, Jersey automatically rejects the request with HTTP 415 Unsupported Media Type before the method body ever executes.

This acts as a gatekeeper, ensuring only correctly formatted JSON payloads reach application logic and preventing parsing errors or security issues from unexpected input formats.

**Q: Contrast @QueryParam filtering with path-based filtering (e.g. /api/v1/sensors/type/CO2). Why is the query parameter approach generally considered superior?**

GET /sensors supports an optional @QueryParam("type") filter when provided (e.g. ?type=Temperature), only matching sensors are returned; otherwise the full collection is returned. This is preferable to a path-based alternative like /sensors/type/CO2 for three reasons.

First, the URI /sensors consistently identifies the sensor collection as a resource; the query parameter refines the view this aligns with REST conventions where the path identifies the resource and query parameters adjust the representation. 

Second, query parameters are composable: additional filters (e.g. ?type=CO2&status=ACTIVE) can be added without new @Path annotations. Third, this follows established patterns used by major APIs like GitHub and Stripe, making it immediately recognisable to developers.

---

## Part 4: Deep Nesting with Sub- Resources

**Q: Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity compared to defining every nested path in one massive controller class?**

In SensorResource, the method annotated with @Path("/{sensorId}/readings") carries no HTTP method annotation. It functions as a sub-resource locator it constructs and returns a SensorReadingResource object, passing the sensorId as context. Jersey then dispatches the actual HTTP method (GET or POST) to the corresponding method in that sub-resource class.

This pattern provides several architectural advantages. First, it enforces separation of concerns sensor CRUD operations and reading history management are distinct responsibilities and belong in separate classes. 

Second, it promotes extensibility; should future requirements introduce additional nested resources (e.g. /sensors/{id}/alerts), each can be implemented as a standalone class without inflating SensorResource. Third, SensorReadingResource can be unit-tested independently by simply constructing it with a sensor ID, without invoking the parent resource. This keeps individual classes focused and maintainable as the API grows.

---

## Part 5: Advanced Error Handling, Exception Mapping & Logging

**Q: Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?**

When POST /sensors receives a payload with a roomId that doesn't correspond to any existing room, SensorResource throws LinkedResourceNotFoundException, which LinkedResourceNotFoundExceptionMapper maps to 422 Unprocessable Entity. Returning 404 Not Found here would be misleading because the endpoint /sensors does exist and is operational the problem is not the URL.

The JSON is syntactically correct, but it contains a semantic error: a reference to a room the server cannot locate. HTTP 422 conveys this precisely the request was well-formed and understood, but could not be processed due to a logical error in the body giving the client a clear and accurate understanding of the failure.

**Q: From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather?**

GenericExceptionMapper implements ExceptionMapper<Throwable> to catch any unhandled runtime exception and return a generic 500 Internal Server Error with the message "An unexpected error occurred." Full exception details are logged server-side via Logger but never included in the client-facing response.

Exposing raw stack traces constitutes an information disclosure vulnerability. A stack trace reveals internal package and class names, exposing the application's architecture. It can contain framework version numbers, enabling attackers to search for known CVEs targeting those versions. File paths within the trace may disclose the server's directory structure and operating system. Method names and line numbers provide a detailed map of the codebase that can be used to identify potential injection points or logic flaws. This category of vulnerability is recognised in the OWASP Top 10. Returning only a generic error message to clients is therefore an essential security practice.

**Q: Why is it advantageous to use JAX-RS filters for cross-cutting concerns like logging, rather than manually inserting Logger.info() statements inside every single resource method?**

LoggingFilter implements both ContainerRequestFilter and ContainerResponseFilter and is registered via @Provider. It intercepts every incoming request to log the HTTP method and URI, and every outgoing response to log the corresponding status code automatically, across the entire API.

Using a filter for this cross-cutting concern is advantageous because it eliminates code duplication the logging logic is written once rather than repeated across every resource method. It ensures consistency, since every endpoint is logged in the same format regardless of which resource handles the request. If the log format needs to change, only LoggingFilter.java requires modification. Finally, it keeps resource classes focused solely on business logic, in accordance with the single-responsibility principle.

## Video demonstration

The full video walkthrough was recorded separately and submitted via **Blackboard**.

---

## References

- Course module: **5COSC022W** — University of Westminster
- No Spring Boot or database technology was used, as required by the coursework brief
