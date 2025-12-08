package me.onetwo.upvy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.util.TimeZone

@SpringBootApplication
class UpvyBackendApplication

fun main(args: Array<String>) {
    // Set default timezone to UTC for consistent backend behavior
    // Frontend will convert to local timezone for display
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    runApplication<UpvyBackendApplication>(*args)
}
