package com.avito.instrumentation.reservation.client.local

import com.avito.instrumentation.reservation.adb.AndroidDebugBridge
import com.avito.instrumentation.reservation.adb.EmulatorsLogsReporter
import com.avito.runner.service.worker.device.Serial
import com.avito.instrumentation.reservation.client.ReservationClient
import com.avito.instrumentation.reservation.request.Device as RequestedDevice
import com.avito.runner.service.worker.device.Device as WorkerDevice
import com.avito.instrumentation.reservation.request.Reservation
import com.avito.instrumentation.util.forEachAsync
import com.avito.runner.service.worker.device.Device.DeviceStatus
import com.avito.runner.service.worker.device.adb.AdbDevicesManager
import com.avito.utils.logging.CILogger
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.distinctBy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Suppress("EXPERIMENTAL_API_USAGE")
internal class LocalReservationClient(
    private val androidDebugBridge: AndroidDebugBridge,
    private val devicesManager: AdbDevicesManager,
    private val emulatorsLogsReporter: EmulatorsLogsReporter,
    private val configurationName: String,
    private val logger: CILogger
) : ReservationClient {

    private var state: State = State.Idling

    override suspend fun claim(
        reservations: Collection<Reservation.Data>,
        serialsChannel: SendChannel<Serial>,
        reservationDeployments: SendChannel<String> // TODO: make this state internal
    ) {
        if (state !is State.Idling) {
            val error = RuntimeException("Unable to start reservation job. Already started")
            logger.critical(error.message.orEmpty())
            throw error
        }
        logger.debug("Starting reservations for the configuration: $configurationName...")

        val devicesChannel = Channel<WorkerDevice>(Channel.UNLIMITED)
        state = State.Reserving(devices = devicesChannel)

        reservations.forEach { reservation ->
            val fakeDeploymentName = "local-stub"
            reservationDeployments.send(fakeDeploymentName)

            logger.debug("Starting deployment: $fakeDeploymentName")
            check(reservation.device is RequestedDevice.LocalEmulator) {
                "Non-local emulator ${reservation.device} is unsupported in local reservation"
            }
            logger.debug("Deployment created: $fakeDeploymentName")

            listenEmulators(reservation, devicesChannel)
        }

        //todo use Flow
        @Suppress("DEPRECATION")
        devicesChannel
            .distinctBy { it.id }
            .forEachAsync { workerDevice ->
                logger.info("Found new emulator: ${workerDevice.id}")

                val serial = workerDevice.id
                val device = androidDebugBridge.getDevice(serial)
                val isReady = device.waitForBoot()
                if (isReady) {
                    emulatorsLogsReporter.redirectLogcat(
                        emulatorName = serial,
                        device = device
                    )
                    serialsChannel.send(serial)

                    logger.info("Device $serial is reserved for further usage")
                } else {
                    logger.info("Device $serial can't be used")
                }
            }
    }

    override suspend fun release(
        reservationDeployments: Collection<String>
    ) {
        if (state !is State.Reserving) {
            // TODO: check the state on client side beforehand
            val error = RuntimeException("Unable to stop reservation job. Hasn't started yet")
            logger.critical(error.message.orEmpty())
            throw error
        }
        (state as State.Reserving).devices.close()

        state = State.Idling

        logger.info("Devices released for configuration: $configurationName")
    }

    private fun listenEmulators(reservation: Reservation.Data, devices: SendChannel<WorkerDevice>) {
        // TODO: Don't use global scope. Unconfined coroutines lead to leaks
        GlobalScope.launch {
            logger.debug("Start listening devices for $reservation")
            // TODO: prevent reusing the same device in different reservations
            val reservedDevices = mutableSetOf<Serial>()
            var emulators = localEmulators(reservation)

            while (!devices.isClosedForSend) {
                findRemainingDevices(reservation, emulators, reservedDevices)
                    .forEach { emulator ->
                        devices.send(emulator)
                        reservedDevices.add(emulator.id)
                    }

                delay(TimeUnit.SECONDS.toMillis(5))

                emulators = localEmulators(reservation)
            }
            logger.debug("Finish listening devices for $reservation")
        }
    }

    private fun findRemainingDevices(
        reservation: Reservation.Data,
        devices: Set<WorkerDevice>,
        reserved: Set<Serial>
    ): List<WorkerDevice> {
        val remaining = reservation.count - reserved.size
        return devices.filterNot { device ->
            reserved.contains(device.id)
        }.take(remaining)
    }

    private fun localEmulators(reservation: Reservation.Data): Set<WorkerDevice> = try {
        logger.debug("Getting local emulators")
        val devices = devicesManager.connectedDevices()
            .filter { fitsReservation(it, reservation) }
            .toSet()
        logger.info(
            "Getting local emulators completed. " +
                "Received ${devices.size} emulators."
        )
        devices.toSet()
    } catch (t: Throwable) {
        logger.info("Failed to get local emulators", t)
        emptySet()
    }

    private fun fitsReservation(device: WorkerDevice, reservation: Reservation.Data): Boolean {
        return device.online
            && device.deviceStatus() == DeviceStatus.Alive
            && device.api == reservation.device.api
    }

    private sealed class State {

        class Reserving(
            val devices: Channel<WorkerDevice>
        ) : State()

        object Idling : State()
    }
}
