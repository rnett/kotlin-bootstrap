package com.rnett.future.testing.github

import com.rnett.future.testing.KotlinFutureTestingExtension
import org.slf4j.LoggerFactory
import java.time.DayOfWeek

/**
 * CRON scheduling.
 */
public sealed class Scheduling(public val cron: String) {
    protected companion object {
        internal val logger = LoggerFactory.getLogger(KotlinFutureTestingExtension::class.java)
    }

    protected fun checkMH(minute: Int, hour: Int) {
        require(minute >= 0) { "Can't have minute < 0" }
        require(minute < 60) { "Can't have minute >= 60" }
        require(hour >= 0) { "Can't have minute < 0" }
        require(hour < 23) { "Can't have minute >= 23" }
    }

    /**
     * Run daily at a set time
     */
    public data class Daily(val minute: Int = 0, val hour: Int = 0) : Scheduling("$minute $hour * * *") {
        init {
            checkMH(minute, hour)
        }
    }

    /**
     * Run weekly at a set time
     */
    public data class Weekly(val minute: Int = 0, val hour: Int = 0, val dayOfWeek: DayOfWeek = DayOfWeek.SATURDAY) :
        Scheduling("$minute $hour * * ${dayOfWeek.value}") {
        init {
            checkMH(minute, hour)
        }
    }

    /**
     * Run monthly at a set time
     */
    public data class Monthly(val minute: Int = 0, val hour: Int = 0, val dayOfMonth: Int = 1) :
        Scheduling("$minute $hour $dayOfMonth * *") {
        init {
            checkMH(minute, hour)
            require(dayOfMonth >= 1) { "Can't have day of month < 1" }
            require(dayOfMonth <= 31) { "Can't have day of month > 31" }
            if (dayOfMonth > 28)
                logger.warn("Day of month is > 28, may not run on all months")
        }
    }
}