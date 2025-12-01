package me.onetwo.growsnap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.util.TimeZone

@SpringBootApplication
class GrowSnapBackendApplication

fun main(args: Array<String>) {
    // Set default timezone to UTC for consistent backend behavior
    // Frontend will convert to local timezone for display
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    runApplication<GrowSnapBackendApplication>(*args)
}
