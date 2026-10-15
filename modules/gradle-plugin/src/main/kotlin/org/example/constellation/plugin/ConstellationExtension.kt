package org.example.constellation.plugin

import org.gradle.api.provider.Property

/** Plugin configuration: `constellation { port = 8080 }`. */
abstract class ConstellationExtension {

    /** Server port; 8080 by default. */
    abstract val port: Property<Int>

    /** Open the browser after the server starts. */
    abstract val openBrowser: Property<Boolean>

    /** Also analyze `test` source set classes (they get the Test category). */
    abstract val includeTests: Property<Boolean>

    /**
     * When applied to the root project: also analyze every java subproject's main
     * classes, so cross-module links connect analyzed classes of the whole build.
     */
    abstract val includeSubprojects: Property<Boolean>

    /** Stop the server after N seconds (0 = serve until interrupted). Test convenience. */
    abstract val autoStopSeconds: Property<Int>
}
