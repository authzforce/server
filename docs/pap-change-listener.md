# PAP change notifications

AuthzForce Server can notify an optional Java listener after successful PAP API
operations. No listener is configured by default. This extension does not require
additional runtime libraries in the standard server distribution.

## Contract

Implement `org.ow2.authzforce.rest.service.PapChangeListener`:

```java
package example;

import org.ow2.authzforce.rest.service.PapChangeEvent;
import org.ow2.authzforce.rest.service.PapChangeListener;

public final class MyPapListener implements PapChangeListener {
    public MyPapListener() { }

    @Override
    public void papChanged(PapChangeEvent event) {
        // Record the notification in your integration's persistent queue.
        // A separate worker can publish it to a broker.
    }
}
```

Compile the extension against the matching version of
`org.ow2.authzforce:authzforce-ce-server-rest-service` (normally with Maven
`provided` scope). Do not bundle a second copy of the server API classes.
The listener must have a public no-argument constructor. The server creates one
instance per web application, using its classloader; configuration of the
listener's own infrastructure is the extension's responsibility.

Callbacks run synchronously on request threads. The implementation must be
thread-safe and finish promptly; its duration adds to PAP request latency.
Concurrent operations may produce callbacks in a different order from storage
mutations. An event UUID and timestamp are not a domain revision.

Runtime exceptions from the callback are logged and do not turn an already
successful PAP operation into an HTTP failure. Fatal JVM errors are not caught.
If the listener implements `AutoCloseable`, Spring closes it when the web
application context is destroyed. Coordinate shutdown of any workers owned by
the extension.

## Install and configure

1. Put the extension JAR and its additional runtime dependencies on the web
   application's classpath, for example in
   `/opt/authzforce-ce-server/webapp/WEB-INF/lib`.
2. Set the following entry in the Tomcat context configuration (for example
   `/etc/tomcat11/Catalina/localhost/authzforce-ce.xml`):

   ```xml
   <Environment
       name="org.ow2.authzforce.rest.service.PapChangeListener.class"
       description="Fully qualified PAP change listener Java class"
       type="java.lang.String"
       value="example.MyPapListener"
       override="false" />
   ```

3. Redeploy/restart the web application.

An absent or empty entry disables notifications. A nonempty entry naming an
unavailable class, a class that does not implement the interface, or a listener
that cannot be constructed causes startup to fail. This avoids silently running
without an explicitly configured integration.

Changing the extension requires building its JAR and updating the deployment,
not recompiling AuthzForce source code. This is not hot loading. Review
compatibility with the listener API when upgrading the server.

## Events

All events contain an event UUID, creation timestamp, domain ID and operation.
Policy identifiers are supplied only where applicable:

| Operation | Policy ID | Version |
| --- | --- | --- |
| `POLICY_VERSION_ADDED` | Added policy | Added version |
| `POLICY_VERSION_DELETED` | Affected policy | Actual deleted version, including when requested through `latest` |
| `POLICY_DELETED` | Removed policy | Absent; operation covers all removed versions |
| `PDP_CONFIGURATION_CHANGED` | Absent | Absent |
| `ATTRIBUTE_PROVIDERS_CHANGED` | Absent | Absent |
| `DOMAIN_DELETED` | Absent | Absent |

Notifications describe successful operations, not a computed content diff.
A successful configuration call may therefore notify even when values are
unchanged. Adding a policy version does not necessarily activate it.
Provider events concern configuration, not changes in data queried from
external sources. Events do not contain policy XML or provider configuration,
which could include sensitive information.

No successful-change event is emitted for a rejected operation, conflicting
policy version, or not-found deletion. Domain creation, domain metadata and
repository-limit updates are outside this initial contract. Automatic version
rolling does not produce separate deletion notifications. Direct filesystem
changes and synchronization are not intercepted by the API hooks.

## Delivery and downstream recovery

The callback is best effort, with no retries or transactional outbox in the
server. A crash after a policy is persisted but before the event is recorded can
lose a notification. A callback failure does not roll back the policy.

External integrations may provide durable event queues, asynchronous publishing,
retry and duplicate handling. Such a queue is not atomic with policy storage.
Startup and periodic reconciliation against the PAP can recover the current
state after missed notifications, but not necessarily every intermediate change.
This entails a period of possible staleness, including delayed revocations.

Each embedded PDP instance must recover a coherent set of policies, root
selection and relevant provider configuration. Provider implementation JARs must
already be available to that instance. Building and safely replacing the embedded
PDP, instance fan-out and reconciliation are responsibilities of the downstream
integration, not features introduced by this extension point.
