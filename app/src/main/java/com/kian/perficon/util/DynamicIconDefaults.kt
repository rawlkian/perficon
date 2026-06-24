package com.kian.perficon.util

import com.kian.perficon.model.IconMapping

/** CandyBar's stock calendar component set, used by the project-level calendar sequence. */
object DynamicIconDefaults {
    const val DEFAULT_CALENDAR_MAPPING_PACKAGE = "com.kian.perficon.dynamic.calendar"
    const val DEFAULT_CLOCK_MAPPING_PACKAGE = "com.kian.perficon.dynamic.clock"

    private val calendarComponents = listOf(
        "ComponentInfo{com.underwood.calendar_beta/com.android.calendar.AllInOneActivity}",
        "ComponentInfo{com.underwood.calendar/com.android.calendar.AllInOneActivity}",
        "ComponentInfo{com.google.android.calendar/com.google.android.calendar.LaunchActivity}",
        "ComponentInfo{com.google.android.calendar/com.google.android.calendar.AllInOneActivity}",
        "ComponentInfo{com.android.calendar/com.android.calendar.LaunchActivity}",
        "ComponentInfo{com.htc.calendar/com.htc.calendar.LaunchActivity}",
        "ComponentInfo{com.htc.calendar/com.htc.calendar.CalendarActivityMain}",
        "ComponentInfo{com.htc.calendar/com.htc.calendar.CalendarCarouselActivity}",
        "ComponentInfo{com.android.calendar/com.android.calendar.AllInOneActivity}",
        "ComponentInfo{com.android.calendar/com.android.calendar.CalendarTabActivity}",
        "ComponentInfo{com.asus.calendar.watchcalendar/com.asus.calendar.watchcalendar.WatchCalendarActivity}",
        "ComponentInfo{com.asus.calendar.watchcalendar/com.asus.calendar.watchcalendar.LaunchActivity}",
        "ComponentInfo{com.sonyericsson.androidapp.lunarcalendar/com.sonyericsson.androidapp.lunarcalendar.CalendarActivity}",
        "ComponentInfo{com.sonyericsson.calendar/com.sonyericsson.calendar.monthview.MonthViewActivity}",
        "ComponentInfo{com.motorola.calendar/com.motorola.calendar.LaunchActivity}",
        "ComponentInfo{com.oppo.calendar/com.oppo.calendar.activity.CalendarActivity}",
        "ComponentInfo{com.lge.calendar/com.lge.calendar.LaunchActivity}",
        "ComponentInfo{com.lenovo.app.Calendar/com.lenovo.app.Calendar.MonthUI}",
        "ComponentInfo{com.sec.android.app.latin.launcher.calendar/com.sec.android.app.latin.launcher.calendar.Launcher}",
        "ComponentInfo{com.lenovo.app.Calendar/com.lenovo.app.Calendar.MonthActivityNew}",
        "ComponentInfo{com.android.calendar/com.android.calendar.MonthActivity}",
        "ComponentInfo{com.samsung.android.calendar/com.samsung.android.app.calendar.activity.MainActivity}"
    )

    private val clockComponents = listOf(
        "ComponentInfo{com.android.deskclock/com.android.deskclock.AlarmClock}",
        "ComponentInfo{com.zui.deskclock/com.zui.deskclock.DeskClock}",
        "ComponentInfo{com.android.deskclock/com.android.deskclock.AlarmsMainActivity}",
        "ComponentInfo{com.android.alarmclock/com.android.alarmclock.AlarmClock}",
        "ComponentInfo{com.android.deskclock/com.jrdcom.timetool.TimeToolActivity}",
        "ComponentInfo{com.htc.android.worldclock/com.htc.android.worldclock.WorldClockTabControl}",
        "ComponentInfo{com.sec.android.app.clockpackage/com.sec.android.app.clockpackage.ClockPackage}",
        "ComponentInfo{com.google.android.deskclock/com.android.deskclock.DeskClock}",
        "ComponentInfo{com.android.deskclock/com.android.deskclock.DeskClock}",
        "ComponentInfo{com.motorola.blur/com.motorola.blur.alarmclock.AlarmClock}",
        "ComponentInfo{com.sec.android.app.clockpackage/com.sec.android.app.clockpackage.AlarmActivity}",
        "ComponentInfo{com.android.alarmclock/com.meizu.flyme.alarmclock.AlarmClock}",
        "ComponentInfo{com.android.deskclock/com.android.deskclock.DeskClockTabActivity}",
        "ComponentInfo{com.sec.android.app.clockpackage/com.sec.android.app.clockpackage.TabletClockPackage}",
        "ComponentInfo{com.moblynx.clockl/com.android.deskclock.DeskClock}",
        "ComponentInfo{com.yulong.android.xtime/yulong.xtime.ui.main.XTimeActivity}",
        "ComponentInfo{com.lge.clock/com.lge.clock.AlarmClockActivity}",
        "ComponentInfo{com.asus.deskclock/com.asus.deskclock.DeskClock}",
        "ComponentInfo{com.android.alarmclock/com.meizu.flyme.alarmclock.DeskClock}",
        "ComponentInfo{net.oneplus.deskclock/net.oneplus.deskclock.DeskClock}",
        "ComponentInfo{com.oppo.alarmclock/com.oppo.alarmclock.AlarmClock}",
        "ComponentInfo{com.sonyericsson.organizer/com.sonyericsson.organizer.Organizer}",
        "ComponentInfo{com.oneplus.deskclock/com.oneplus.deskclock.DeskClock}",
        "ComponentInfo{com.sec.android.app.clockpackage/com.sec.android.app.clockpackage.alarm.Alarm}",
        "ComponentInfo{com.lenovo.deskclock/com.lenovo.deskclock.DeskClock}",
        "ComponentInfo{com.tct.timetool/com.tct.timetool.DeskClock}",
        "ComponentInfo{com.android.BBKClock/com.android.BBKClock.Timer}",
        "ComponentInfo{zte.com.cn.alarmclock/zte.com.cn.alarmclock.AlarmClock}",
        "ComponentInfo{com.jrdcom.timetool/com.jrdcom.timetool.TimeToolActivity}",
        "ComponentInfo{com.coloros.alarmclock/com.coloros.alarmclock.AlarmClock}"
    )

    fun expandCalendarMapping(mapping: IconMapping): List<IconMapping> {
        return expand(mapping, calendarComponents, "calendar")
    }

    fun expandClockMapping(mapping: IconMapping): List<IconMapping> {
        return expand(mapping, clockComponents, "clock")
    }

    private fun expand(mapping: IconMapping, components: List<String>, type: String): List<IconMapping> =
        components.mapIndexed { index, component ->
            val match = COMPONENT_PATTERN.matchEntire(component) ?: error("Invalid $type component: $component")
            mapping.copy(
                id = -(index + 1L),
                targetPackageName = match.groupValues[1],
                targetActivityName = match.groupValues[2]
            )
        }

    private val COMPONENT_PATTERN = Regex("ComponentInfo\\{([^/]+)/([^}]+)\\}")
}
