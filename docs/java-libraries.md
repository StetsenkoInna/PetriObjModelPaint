# Reuse the Java simulation libraries

Three modules can be used without the Swing editor or the Spring Boot server:

| Artifact | Use |
|---|---|
| `petri-math` | Petri net and Petri-object simulation engine |
| `petri-api` | Simulation interfaces, events, DTOs and statistic interfaces |
| `petri-model` | Graph model, PNML readers and writers |

These modules carry their own MIT license. `petri-model` includes drawing geometry
and AWT types; it does not require the Swing editor. Java 23 or newer is required.
The engine reports invalid negative delay samples through logging without opening
a GUI dialog; this does not change its sampling policy.

## Maven

After the library release workflow has completed for `v2.7.0`, add the public
repository and whichever module your application uses:

```xml
<repositories>
    <repository>
        <id>petri-libraries</id>
        <url>https://raw.githubusercontent.com/StetsenkoInna/PetriObjModelPaint/maven-repository/</url>
        <releases><checksumPolicy>fail</checksumPolicy></releases>
        <snapshots><enabled>false</enabled></snapshots>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>ua.stetsenkoinna</groupId>
        <artifactId>petri-math</artifactId>
        <version>2.6.0</version>
    </dependency>
</dependencies>
```

Use `petri-model` for PNML or `petri-api` for service contracts; their core
dependencies are transitive. No GitHub account, token, submodule or editor install
is required to download the libraries. The parent POM is published alongside them.

If your Maven settings redirect every repository through a mirror, exclude
`petri-libraries` from that mirror, for example with
`<mirrorOf>*,!petri-libraries</mirrorOf>`.

## Release files

Each release includes the three binary JARs, matching source JARs and POMs,
SHA-256 sums, and a ZIP containing the Maven repository. Existing versioned
artifacts cannot be replaced with different bytes. The publication workflow
preserves previously published versions on the `maven-repository` branch.

The Swing editor remains an application ZIP. `petri-server` is distributed as an
executable JAR for HTTP/SSE/WebSocket integration; it is not a dependency of these
libraries. Publishing the libraries does not replace the Python simulator in
Petri Net Sim. That application can use the server adapter when a shared Java
simulation backend is desired.

Running the Build Java library distribution workflow manually with an empty
`release_tag` builds review artifacts only. To repair a failed publication, set
`release_tag` to an existing published tag, for example `v2.7.0`: the workflow
builds that tag and publishes its packages. Normal publication runs when a
GitHub Release is published. The workflow does not create releases or tags.
