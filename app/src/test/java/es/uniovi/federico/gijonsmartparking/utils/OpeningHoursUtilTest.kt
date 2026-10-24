package es.uniovi.federico.gijonsmartparking.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class OpeningHoursUtilTest {

    private fun at(day: Int, hour: Int, minute: Int): Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, day)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }

    @Test
    fun always_open() {
        assertTrue(OpeningHoursUtil.isOpenAt("24/7", at(Calendar.SUNDAY, 3, 0)))
    }

    @Test
    fun weekday_inside_range_is_open() {
        assertTrue(OpeningHoursUtil.isOpenAt("Mo-Fr 08:00-20:00", at(Calendar.TUESDAY, 10, 0)))
    }

    @Test
    fun weekday_outside_range_is_closed() {
        assertFalse(OpeningHoursUtil.isOpenAt("Mo-Fr 08:00-20:00", at(Calendar.TUESDAY, 21, 0)))
    }

    @Test
    fun rule_for_other_days_means_closed_today() {
        assertFalse(OpeningHoursUtil.isOpenAt("Mo-Fr 08:00-20:00", at(Calendar.SATURDAY, 10, 0)))
        assertFalse(OpeningHoursUtil.isOpenAt("Mo-Fr 08:00-20:00; Sa 09:00-14:00", at(Calendar.SUNDAY, 10, 0)))
    }

    @Test
    fun explicit_closed_day_is_closed() {
        assertFalse(OpeningHoursUtil.isOpenAt("Mo-Sa 08:00-20:00; Su off", at(Calendar.SUNDAY, 12, 0)))
        assertTrue(OpeningHoursUtil.isOpenAt("Mo-Sa 08:00-20:00; Su off", at(Calendar.SATURDAY, 12, 0)))
    }

    @Test
    fun several_ranges_in_one_rule() {
        val rule = "Mo-Fr 08:00-14:00,16:00-20:00"
        assertFalse(OpeningHoursUtil.isOpenAt(rule, at(Calendar.MONDAY, 15, 0)))
        assertTrue(OpeningHoursUtil.isOpenAt(rule, at(Calendar.MONDAY, 17, 0)))
    }

    @Test
    fun range_crossing_midnight() {
        assertTrue(OpeningHoursUtil.isOpenAt("22:00-06:00", at(Calendar.MONDAY, 23, 0)))
        assertTrue(OpeningHoursUtil.isOpenAt("22:00-06:00", at(Calendar.TUESDAY, 3, 0)))
        assertFalse(OpeningHoursUtil.isOpenAt("22:00-06:00", at(Calendar.MONDAY, 12, 0)))
    }

    @Test
    fun missing_or_unreadable_hours_count_as_open() {
        assertTrue(OpeningHoursUtil.isOpenAt("unknown", at(Calendar.MONDAY, 12, 0)))
        assertTrue(OpeningHoursUtil.isOpenAt("", at(Calendar.MONDAY, 12, 0)))
        assertTrue(OpeningHoursUtil.isOpenAt("by appointment", at(Calendar.MONDAY, 12, 0)))
    }
}
