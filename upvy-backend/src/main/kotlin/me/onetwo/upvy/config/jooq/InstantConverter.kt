package me.onetwo.upvy.config.jooq

import org.jooq.Converter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * jOOQ Converter for mapping database DATETIME/TIMESTAMP to java.time.Instant
 *
 * This converter treats all database timestamps as UTC and converts them to Instant.
 * Used for global app best practices to ensure consistent timezone handling.
 *
 * Database: LocalDateTime (timezone-agnostic)
 * Application: Instant (UTC moment in time)
 *
 * @see <a href="https://www.jooq.org/doc/latest/manual/sql-building/queryparts/custom-bindings/">jOOQ Custom Bindings</a>
 */
class InstantConverter : Converter<LocalDateTime, Instant> {

    override fun from(databaseObject: LocalDateTime?): Instant? {
        return databaseObject?.toInstant(ZoneOffset.UTC)
    }

    override fun to(userObject: Instant?): LocalDateTime? {
        return userObject?.atOffset(ZoneOffset.UTC)?.toLocalDateTime()
    }

    override fun fromType(): Class<LocalDateTime> = LocalDateTime::class.java

    override fun toType(): Class<Instant> = Instant::class.java
}
