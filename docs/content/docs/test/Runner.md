---
title: Test Runner
type: docs
---

# Avito test runner

This is the Gradle plugin to run Android instrumentation tests.

It can do the following:

- Filter tests by annotations, by packages, by previous runs.
- Run tests in parallel. It orchestrates emulators in Kubernetes or uses local emulators.
- Rerun failed tests to deal with flakiness
- Save tests result in a report.\
It uses an internal TMS (test management system). We are working on support other formats.

## Getting started

1. Apply the instrumentation-tests Gradle plugin \

{{< tabs "applyPlugin" >}}
{{< tab "Kotlin" >}}
Add to your `build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("com.avito.android.instrumentation-tests")
}
```

{{< /tab >}}
{{< tab "Groovy" >}}
Add to your `build.gradle`

```groovy
plugins {
    id("com.android.application")
    id("com.avito.android.instrumentation-tests")
}
```

{{< /tab >}}
{{< /tabs >}}

2. Add common plugin configuration
{{< tabs "pluginConfiguration" >}}
{{< tab "Kotlin" >}}

```kotlin
import com.avito.instrumentation.reservation.request.Device.LocalEmulator

extensions.getByType<GradleInstrumentationPluginConfiguration>().apply {
    // they are required for Avito app. We will make them optional in future.
    reportApiUrl = "http://stub"
    reportApiFallbackUrl = "http://stub"
    reportViewerUrl = "http://stub"
    registry = "registry"
    sentryDsn = "http://stub-project@stub-host/0"
    slackToken = "stub"
    fileStorageUrl = "http://stub"

    configurationsContainer.register("local") {

        targetsContainer.register("api28") {
            deviceName = "API28"

            scheduling = SchedulingConfiguration().apply {
                quota = QuotaConfiguration().apply {
                    retryCount = 1
                    minimumSuccessCount = 1
                }

                reservation = TestsBasedDevicesReservationConfiguration().apply {
                    device = LocalEmulator.device(28, "Android_SDK_built_for_x86_64")
                    maximum = 1
                    minimum = 1
                    testsPerEmulator = 1
                }
            }
        }
    }
}
```

{{< /tab >}}
{{< tab "Groovy" >}}

```groovy
import com.avito.instrumentation.reservation.request.Device.LocalEmulator

instrumentation {
    // they are required for Avito app. We will make them optional in future.
    reportApiUrl = "http://stub"
    reportApiFallbackUrl = "http://stub"
    reportViewerUrl = "http://stub"
    registry = "registry"
    sentryDsn = "http://stub-project@stub-host/0"
    slackToken = "stub"
    fileStorageUrl = "http://stub"
    
    configurations {
        local {
            targets {
                api28 {
                    deviceName = "API28"
                    scheduling {
                        quota {
                            retryCount = 1
                            minimumSuccessCount = 1
                        }

                        testsCountBasedReservation {
                            device = new LocalEmulator("28", 28, "Android_SDK_built_for_x86_64")
                            maximum = 1
                            testsPerEmulator = 1
                        }
                    }
                }   
            }       
        }   
    }
}
```

{{< /tab >}}
{{< /tabs >}}

3. Run tests via Gradle task

```shell script
    ./gradlew :<projectPath>:instrumentation<ConfigurationName>         
```

In our case:

```shell script
    ./gradlew :<projectPath>:instrumentationLocal 
```

## Examples

Check out a configuration to run in `GradleInstrumentationPluginConfiguration` in the [test app](https://github.com/avito-tech/avito-android/blob/develop/samples/test-app/build.gradle.kts#L114)

## Filtering tests for execution

0. [You must apply a plugin]({{< relref "#getting-started">}})
1. Create filter

{{< tabs "createFilter" >}}
{{< tab "Kotlin" >}}

```kotlin
extensions.getByType<GradleInstrumentationPluginConfiguration>().apply {
    filters.register("filterName") {
        fromSource.includeByAnnotations(annotations)
        fromSource.excludeByAnnotations(annotations)
        fromSource.includeByPrefixes(prefixes)
        fromSource.excludeByPrefixes(prefixes)
        
        // it is internal for Avito. It uses run history from our test-report system.
        fromRunHistory.excludePreviousStatuses(statuses)
        fromRunHistory.excludePreviousStatuses(statuses)
        fromRunHistory.report("reportId") { reportStatuses ->
            reportStatuses.include(statuses)
            reportStatuses.exclude(statuses)
        }
    }
}
```

{{< /tab >}}
{{< tab "Groovy" >}}

```groovy
import com.avito.instrumentation.reservation.request.Device.LocalEmulator

instrumentation {
    filters {
        filterName {
            fromSource.includeByAnnotations(annotations)
            fromSource.excludeByAnnotations(annotations)
            fromSource.includeByPrefixes(prefixes)
            fromSource.excludeByPrefixes(prefixes)
            
            // it is internal for Avito. It uses run history from our test-report system.
            fromRunHistory.excludePreviousStatuses(statuses)
            fromRunHistory.excludePreviousStatuses(statuses)
            fromRunHistory.report("reportId") { reportStatuses ->
                reportStatuses.include(statuses)
                reportStatuses.exclude(statuses)
            }
        }
    }   
}
```

{{< /tab >}}
{{< /tabs >}}

2. Add filter to configuration

{{< tabs "addFilterToConfiguration" >}}
{{< tab "Kotlin" >}}

```kotlin
extensions.getByType<GradleInstrumentationPluginConfiguration>().apply {
    configurationsContainer.register("local") {
        filter = "filterName"
        // else...   
    }    
}
```

{{< /tab >}}
{{< tab "Groovy" >}}

```groovy
instrumentation {
    configurations {
        local {
            filter = filterName
            // else...       
        }   
    }
}
```

{{< /tab >}}
{{< /tabs >}}

### Filter tests by annotations

{{< tabs "filterTestsByAnnotations" >}}
{{< tab "Kotlin" >}}

```kotlin
extensions.getByType<GradleInstrumentationPluginConfiguration>().apply {
    filters.register("filterName") {
        val yourFullyQualifiedAnnotationName = "package.AnnotationClassName"
        
        val annotations = setOf(youFullyQualifiedAnnotationName)
        // will include only tests with at least one annotation
        fromSource.includeByAnnotations(annotations)
        // will exclude all tests with at least one annotation
        fromSource.excludeByAnnotations(annotations)
    }
}
```

{{< /tab >}}
{{< tab "Groovy" >}}

```groovy
instrumentation {
    filters {
        filterName {
            def yourFullyQualifiedAnnotationName = "package.AnnotationClassName"
                    
            def annotations = [youFullyQualifiedAnnotationName] as Set
            // will include only tests with at least one annotation
            fromSource.includeByAnnotations(annotations)
            // will exclude all tests with at least one annotation
            fromSource.excludeByAnnotations(annotations)       
        }
    }
}
```

{{< /tab >}}
{{< /tabs >}}

### Filter flaky tests

{{< tabs "filterFlakyTests" >}}
{{< tab "Kotlin" >}}

```kotlin
extensions.getByType<GradleInstrumentationPluginConfiguration>().apply {
    filters.register("filterName") {
        fromSource.excludeFlaky = true
    }
}
```

{{< /tab >}}
{{< tab "Groovy" >}}

```groovy
instrumentation {
    filters {
        filterName {
            fromSource.excludeFlaky = true       
        }
    }
}
```

{{< /tab >}}
{{< /tabs >}}

### Filter tests by prefix or name

{{< tabs "filterTestsByPrefix" >}}
{{< tab "Kotlin" >}}

```kotlin
extensions.getByType<GradleInstrumentationPluginConfiguration>().apply {
    filters.register("filterName") {
        val packageTestFilter = "testPackage"
        val classTestFilter = "testPackage.testClass"
        val fullyQualifiedTestFilter = "testPackage.testClass.testMetod"
        val prefixes = setOf(packageTestFilter, classTestFilter, fullyQualifiedTestFilter)
        // will include only tests from package, class and concrete test
        fromSource.includeByPrefixes(prefixes)
        
        // will exclude all tests from package, class and concrete test
        fromSource.excludeByPrefixes(prefixes)
    }
}
```

{{< /tab >}}
{{< tab "Groovy" >}}

```groovy
instrumentation {
    filters {
        filterName {
            def packageTestFilter = "testPackage"
            def classTestFilter = "testPackage.testClass"
            def fullyQualifiedTestFilter = "testPackage.testClass.testMetod"
            def prefixes = [packageTestFilter, classTestFilter, fullyQualifiedTestFilter] as Set
            // will include only tests from package, class and concrete test
            fromSource.includeByPrefixes(prefixes)
            
            // will exclude all tests from package, class and concrete test
            fromSource.excludeByPrefixes(prefixes)
        }
    }   
}
```

{{< /tab >}}
{{< /tabs >}}

### Filter tests by statuses from previous run on the same commit

{{< tabs "filterTestsByPreviousRun" >}}
{{< tab "Kotlin" >}}

```kotlin
import com.avito.instrumentation.configuration.InstrumentationFilter.FromRunHistory.RunStatus

extensions.getByType<GradleInstrumentationPluginConfiguration>().apply {
    filters.register("filterName") {
        val statuses = setOf(RunStatus.Success)        
        // will run only Success previously Succeed tests
        fromRunHistory.includePreviousStatuses(statuses)
        // will run all tests except previously Succeed
        fromRunHistory.excludePreviousStatuses(statuses)
    }
}
```

{{< /tab >}}
{{< tab "Groovy" >}}

```groovy
import com.avito.instrumentation.configuration.InstrumentationFilter.FromRunHistory.RunStatus

instrumentation {
    filters {
        filterName {
            def statuses = [RunStatus.Success] as Set   
            // will run only Success previously Succeed tests
            fromRunHistory.includePreviousStatuses(statuses)
            // will run all tests except previously Succeed
            fromRunHistory.excludePreviousStatuses(statuses)
        }
    }   
}
```

{{< /tab >}}
{{< /tabs >}}

### Filter tests by statuses from report by id

{{< tabs "filterTestsByReport" >}}
{{< tab "Kotlin" >}}

```kotlin
import com.avito.instrumentation.configuration.InstrumentationFilter.FromRunHistory.RunStatus

extensions.getByType<GradleInstrumentationPluginConfiguration>().apply {
    filters.register("filterName") {
        // report-viewer report id
        val reportId = "id"
        val statuses = setOf(RunStatus.Failed)
        
        fromRunHistory.report(reportId) { reportStatuses ->
            // will run only Failed tests from report 
            reportStatuses.include(statuses)
            // will run all tests except Failed tests from report
            reportStatuses.exclude(statuses)
        }
    }
}
```

{{< /tab >}}
{{< tab "Groovy" >}}

```groovy
import com.avito.instrumentation.configuration.InstrumentationFilter.FromRunHistory.RunStatus

instrumentation {
    filters {
        filterName {
            // report-viewer report id
            def reportId = "id"
            def statuses = [RunStatus.Failed] as Set  
            fromRunHistory.report(reportId) { reportStatuses ->
                // will run only Failed tests from report 
                reportStatuses.include(statuses)
                // will run all tests except Failed tests from report
                reportStatuses.exclude(statuses)
            }
        }
    }   
}
```

{{< /tab >}}
{{< /tabs >}}

### Apply a filter without changing `build.gradle.kts` or `build.gradle`

1. Add custom Gradle property for filter name to gradle.properties file

```properties
filterName="default"
```

2. Use the property to configure plugin

{{< tabs "applyFilterWithoutChanging" >}}
{{< tab "Kotlin" >}}

```kotlin
import com.avito.kotlin.dsl.getOptionalStringProperty

// read property
val filterName: String? by project
// or
val filterName = project.getOptionalStringProperty("filterName", "default")

extensions.getByType<GradleInstrumentationPluginConfiguration>().apply {
    configurationsContainer.register("local") {
        filter = filterName
        // else...   
    }    
}
```

{{< /tab >}}
{{< tab "Groovy" >}}

```groovy
import static com.avito.kotlin.dsl.ProjectExtensionsKt.getOptionalStringProperty

// read property
def filterName = project.hasProperty("filterName") ? project["filterName"] : "default"
// or
def filterName = getOptionalStringProperty(project, "filterName", "default")

instrumentation {
    configurations {
        local {
            filter = filterName
        }
    }
}
```

{{< /tab >}}
{{< /tabs >}}

3. Add `property` to CLI command if you want to override `filterName`

```shell script
./gradlew instrumentationLocal -PfilterName=<any name of defined filter>
```

### Customize a filter without changing `build.gradle.kts` or `build.gradle`

You can customize everything by adding custom properties to CLI command e.g.

1. Define [filter including tests by annotation]({{< relref "#filter-tests-by-annotations">}})
2. Add logic to check presence of Gradle property.

{{< tabs "customizeFilterWithoutChanging" >}}
{{< tab "Kotlin" >}}

```kotlin
val includedAnnotation: String? by project
// or
val includedAnnotation = project.getOptionalStringProperty("includedAnnotation")
 
extensions.getByType<GradleInstrumentationPluginConfiguration>().apply {
    filters.register("filterName") {
        val annotation = if(includedAnnotation != null){
            includedAnnotation
        } else {
            "package.AnnotationClassName"
        }
        
        val annotations = setOf(annotation)
        fromSource.includeByAnnotations(annotations)
    }
}
```

{{< /tab >}}
{{< tab "Groovy" >}}

```groovy
import static com.avito.kotlin.dsl.ProjectExtensionsKt.getOptionalStringProperty

// read property
def includedAnnotation = project.hasProperty("includedAnnotation") ? project["includedAnnotation"] : null
// or
def includedAnnotation = getOptionalStringProperty(project, "includedAnnotation")

instrumentation {
    filters {
        filterName {
            def annotation = includedAnnotation ?: "package.AnnotationClassName"
            def annotations = [annotation] as Set
            fromSource.includeByAnnotations(annotations)
        }
    }   
}
```

{{< /tab >}}
{{< /tabs >}}

3. Run Gradle task with property `includedAnnotation` to override filter

```shell script
./gradlew instrumentationLocal -PincludedAnnotation="package.AnotherAnnotationClassName" 
```

## Find out how filters were applied

If build finishes successfully It will produce files with debug information
Files will be located at: \
 `<Project root folder>/outputs/<subproject name>/instrumentation/<instrumentation task name>/filter`

### Find out what filter config was

Look at file `filter-config.json`

### Find out what filters applied

Look at file `filters-applied.json`

### Find out what tests were filtered

Look at file `filters-excludes.json` \
- You may find filtered tests grouped by filter names declared in `filters-applied.json`
- You may find a filtered test by name

## Choosing target for tests execution

### Run tests on kubernetes target from a local machine

{{< avito >}}

1. Get access to kubernetes cloud: [internal doc](http://links.k.avito.ru/Kubectl)
2. [Request](http://links.k.avito.ru/androidEmulatorServiceDesk) `exec` access to `android-emulator` namespace in `beta` cluster
3. Setup a context on `beta`, `android-emulator` with your user access.\
More about kubernetes context: [Official docs](https://kubernetes.io/docs/tasks/access-application-cluster/configure-access-multiple-clusters/#define-clusters-users-and-contexts)
4. Add a configuration with target on kubernetes

{{< tabs "addK8SInstrumentation" >}}
{{< tab "Kotlin" >}}

```kotlin
import com.avito.instrumentation.reservation.request.Device.Emulator.Emulator28

configurationsContainer.register("k8s") {                    

            targetsContainer.register("api28") {
                deviceName = "API28"

                scheduling = SchedulingConfiguration().apply {
                    quota = QuotaConfiguration().apply {
                        retryCount = 1
                        minimumSuccessCount = 1
                    }

                    reservation = TestsBasedDevicesReservationConfiguration().apply {
                        device = Emulator28
                        maximum = 1
                        minimum = 1
                        testsPerEmulator = 1
                    }
                }
            }
        }
```

{{< /tab >}}
{{< tab "Groovy" >}}

```groovy
import static com.avito.instrumentation.reservation.request.Device.Emulator.Emulator28

instrumentation {
    configurations {
        k8s {
            targets {
                api28 {
                    deviceName = "API28"
                    scheduling {
                        quota {
                            retryCount = 1
                            minimumSuccessCount = 1
                        }

                        testsCountBasedReservation {
                            device = Emulator28.INSTANCE
                            maximum = 1
                            minimum = 1
                            testsPerEmulator = 1
                        }
                    }
                }   
            }       
        }   
    }
}
```

{{< /tab >}}
{{< /tabs >}}

5. Run tests with extra parameters specified.

```shell script
./gradlew :samples:test-app:instrumentation<configuration name> 
    -PkubernetesContext=<your context> // for Avito probably 'beta'
```

We will looking for `.kube/config` in your $HOME

### Run tests on local emulator target

0. Add a configuration with target on local emulator

{{< tabs "addLocalEmulator" >}}
{{< tab "Kotlin" >}}

```kotlin
import com.avito.instrumentation.reservation.request.Device.LocalEmulator

extensions.getByType<GradleInstrumentationPluginConfiguration>().apply {
configurationsContainer.register("local") {
        targetsContainer.register("api28") {
            deviceName = "API28"

            scheduling = SchedulingConfiguration().apply {
                quota = QuotaConfiguration().apply {
                    retryCount = 1
                    minimumSuccessCount = 1
                }

                reservation = TestsBasedDevicesReservationConfiguration().apply {
                    device = LocalEmulator.device(28, "Android_SDK_built_for_x86_64")
                    maximum = 1
                    testsPerEmulator = 1
                }
            }
        }
    }
}
```

{{< /tab >}}
{{< tab "Groovy" >}}

```groovy
import com.avito.instrumentation.reservation.request.Device.LocalEmulator

instrumentation {
    configurations {
        local {
            targets {
                api28 {
                    deviceName = "API28"
                    scheduling {
                        quota {
                            retryCount = 1
                            minimumSuccessCount = 1
                        }

                        testsCountBasedReservation {
                            device = new LocalEmulator("28", 28, "Android_SDK_built_for_x86_64")
                            maximum = 1
                            testsPerEmulator = 1
                        }
                    }
                }   
            }       
        }   
    }
}
```

{{< /tab >}}
{{< /tabs >}}

1. Run an emulator with 28 API
2. Run Gradle CLI command

```shell script
`./gradlew :<project gradle path>:instrumentation<configuration name>`, e.g.
`./gradlew :samples:test-app:instrumentationLocal` 
```

## Run test on APK was built before

Plugin builds APKs on his own by default.\
If for any reason you have to build APK externally, you can pass files manually:

```kotlin
// optional
applicationApk = "/path/to/app.apk"
// optional
testApplicationApk = "/path/to/test-app-debug-androidTest.apk"
```

## Run tests on Google Cloud Platform

Work in progress

0. Create and configure Kubernetes cluster TODO
    0. Create a node pool. Node must contain CPU that supports KVM https://cloud.google.com/compute/docs/instances/enable-nested-virtualization-vm-instances#gcloud
0. Add configuration for your cluster to ./kube/config
0. Providing credentials to the application https://cloud.google.com/docs/authentication/production#providing_credentials_to_your_application TODO
    0. Run on local machine TODO
        0. Run locally `gcloud auth application-default login` one time will be enough 
        0. Run `./gradlew <instrumentationTaskName> -PkubernetesContext=<context from ./kube/config>`
    0. Run on CI
0. Customize deployment TODO
    0. Custom POD image
    0. Customize POD cpu and ram
    0. Custom namespace

