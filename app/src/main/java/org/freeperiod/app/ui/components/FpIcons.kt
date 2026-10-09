package org.freeperiod.app.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.freeperiod.app.R

/** Stable keys for original, bundled line icons. Never persist drawable IDs; never rename or drop a key. */
object FpIcons {
    class Icon(val key: String, @DrawableRes val drawable: Int, @StringRes val label: Int)
    class Group(@StringRes val title: Int, val icons: List<Icon>)

    /** The icon picker for entry items and categories, in display order. */
    val groups = listOf(
        Group(R.string.icon_group_cycle, listOf(
            Icon("drop", R.drawable.ic_item_drop, R.string.icon_drop),
            Icon("droplets", R.drawable.ic_item_droplets, R.string.icon_droplets),
            Icon("pad", R.drawable.ic_item_pad, R.string.icon_pad),
            Icon("tampon", R.drawable.ic_item_tampon, R.string.icon_tampon),
            Icon("menstrual_cup", R.drawable.ic_item_menstrual_cup, R.string.icon_menstrual_cup),
            Icon("discharge", R.drawable.ic_fp_discharge, R.string.icon_discharge),
            Icon("cramps", R.drawable.ic_symptom_cramps, R.string.symptom_cramps),
            Icon("backache", R.drawable.ic_symptom_backache, R.string.symptom_backache),
            Icon("bloating", R.drawable.ic_symptom_bloating, R.string.symptom_bloating),
            Icon("breast", R.drawable.ic_symptom_breast_tenderness, R.string.symptom_breast_tenderness),
            Icon("headache", R.drawable.ic_symptom_headache, R.string.symptom_headache),
            Icon("joint", R.drawable.ic_symptom_joint_pain, R.string.symptom_joint_pain),
            Icon("acne", R.drawable.ic_symptom_acne, R.string.symptom_acne),
            Icon("digestion", R.drawable.ic_symptom_digestion, R.string.symptom_digestion),
            Icon("nausea", R.drawable.ic_symptom_nausea, R.string.symptom_nausea),
            Icon("warmth", R.drawable.ic_symptom_hot_flushes, R.string.symptom_hot_flushes),
            Icon("night", R.drawable.ic_symptom_night_sweats, R.string.symptom_night_sweats))),
        Group(R.string.icon_group_health, listOf(
            Icon("thermometer", R.drawable.ic_item_thermometer, R.string.icon_thermometer),
            Icon("pill", R.drawable.ic_item_pill, R.string.icon_pill),
            Icon("stethoscope", R.drawable.ic_item_stethoscope, R.string.icon_stethoscope),
            Icon("pulse", R.drawable.ic_item_pulse, R.string.icon_pulse),
            Icon("scale", R.drawable.ic_item_scale, R.string.icon_scale),
            Icon("tooth", R.drawable.ic_item_tooth, R.string.icon_tooth),
            Icon("eye", R.drawable.ic_item_eye, R.string.icon_eye),
            Icon("hot_water_bottle", R.drawable.ic_item_hot_water_bottle, R.string.icon_hot_water_bottle),
            Icon("bathtub", R.drawable.ic_item_bathtub, R.string.icon_bathtub),
            Icon("candle", R.drawable.ic_item_candle, R.string.icon_candle),
            Icon("lotion", R.drawable.ic_item_lotion, R.string.icon_lotion))),
        Group(R.string.icon_group_mood, listOf(
            Icon("smile", R.drawable.ic_item_smile, R.string.icon_smile),
            Icon("meh", R.drawable.ic_item_meh, R.string.icon_meh),
            Icon("calm", R.drawable.ic_item_calm, R.string.icon_calm),
            Icon("sad", R.drawable.ic_symptom_sad, R.string.symptom_sad),
            Icon("anxious", R.drawable.ic_symptom_anxious, R.string.symptom_anxious),
            Icon("irritable", R.drawable.ic_symptom_irritable, R.string.symptom_irritable),
            Icon("spiral", R.drawable.ic_item_spiral, R.string.icon_spiral),
            Icon("sparkles", R.drawable.ic_item_sparkles, R.string.icon_sparkles),
            Icon("focus", R.drawable.ic_symptom_brain_fog, R.string.symptom_brain_fog))),
        Group(R.string.icon_group_sleep, listOf(
            Icon("sleep", R.drawable.ic_symptom_insomnia, R.string.symptom_insomnia),
            Icon("bed", R.drawable.ic_item_bed, R.string.icon_bed),
            Icon("nap", R.drawable.ic_item_nap, R.string.icon_nap),
            Icon("alarm", R.drawable.ic_item_alarm, R.string.icon_alarm),
            Icon("fatigue", R.drawable.ic_symptom_fatigue, R.string.symptom_fatigue),
            Icon("battery_full", R.drawable.ic_item_battery_full, R.string.icon_battery_full),
            Icon("energetic", R.drawable.ic_symptom_energetic, R.string.symptom_energetic))),
        Group(R.string.icon_group_activity, listOf(
            Icon("sneaker", R.drawable.ic_item_sneaker, R.string.icon_sneaker),
            Icon("footprints", R.drawable.ic_item_footprints, R.string.icon_footprints),
            Icon("stretch", R.drawable.ic_item_stretch, R.string.icon_stretch),
            Icon("dumbbell", R.drawable.ic_item_dumbbell, R.string.icon_dumbbell),
            Icon("bike", R.drawable.ic_item_bike, R.string.icon_bike),
            Icon("ball", R.drawable.ic_item_ball, R.string.icon_ball),
            Icon("mountain", R.drawable.ic_item_mountain, R.string.icon_mountain),
            Icon("waves", R.drawable.ic_item_waves, R.string.icon_waves))),
        Group(R.string.icon_group_food, listOf(
            Icon("glass", R.drawable.ic_item_glass, R.string.icon_glass),
            Icon("teacup", R.drawable.ic_item_teacup, R.string.icon_teacup),
            Icon("wine", R.drawable.ic_item_wine, R.string.icon_wine),
            Icon("apple", R.drawable.ic_item_apple, R.string.icon_apple),
            Icon("carrot", R.drawable.ic_item_carrot, R.string.icon_carrot),
            Icon("chocolate", R.drawable.ic_item_chocolate, R.string.icon_chocolate),
            Icon("pizza", R.drawable.ic_item_pizza, R.string.icon_pizza),
            Icon("cutlery", R.drawable.ic_item_cutlery, R.string.icon_cutlery),
            Icon("cravings", R.drawable.ic_symptom_cravings, R.string.symptom_cravings))),
        Group(R.string.icon_group_people, listOf(
            Icon("sex", R.drawable.ic_fp_sex, R.string.icon_sex),
            Icon("person", R.drawable.ic_item_person, R.string.icon_person),
            Icon("people", R.drawable.ic_item_people, R.string.icon_people),
            Icon("chat", R.drawable.ic_item_chat, R.string.icon_chat),
            Icon("phone", R.drawable.ic_item_phone, R.string.icon_phone),
            Icon("gift", R.drawable.ic_item_gift, R.string.icon_gift),
            Icon("balloon", R.drawable.ic_item_balloon, R.string.icon_balloon),
            Icon("home", R.drawable.ic_item_home, R.string.icon_home))),
        Group(R.string.icon_group_everyday, listOf(
            Icon("briefcase", R.drawable.ic_item_briefcase, R.string.icon_briefcase),
            Icon("book", R.drawable.ic_item_book, R.string.icon_book),
            Icon("laptop", R.drawable.ic_item_laptop, R.string.icon_laptop),
            Icon("pencil", R.drawable.ic_item_pencil, R.string.icon_pencil),
            Icon("clock", R.drawable.ic_item_clock, R.string.icon_clock),
            Icon("today", R.drawable.ic_fp_today, R.string.icon_today),
            Icon("note", R.drawable.ic_fp_note, R.string.icon_note),
            Icon("plane", R.drawable.ic_item_plane, R.string.icon_plane),
            Icon("car", R.drawable.ic_item_car, R.string.icon_car),
            Icon("bag", R.drawable.ic_item_bag, R.string.icon_bag),
            Icon("music", R.drawable.ic_item_music, R.string.icon_music),
            Icon("lightbulb", R.drawable.ic_item_lightbulb, R.string.icon_lightbulb))),
        Group(R.string.icon_group_nature, listOf(
            Icon("sun", R.drawable.ic_item_sun, R.string.icon_sun),
            Icon("moon", R.drawable.ic_item_moon, R.string.icon_moon),
            Icon("cloud", R.drawable.ic_item_cloud, R.string.icon_cloud),
            Icon("rain", R.drawable.ic_item_rain, R.string.icon_rain),
            Icon("snowflake", R.drawable.ic_item_snowflake, R.string.icon_snowflake),
            Icon("leaf", R.drawable.ic_item_leaf, R.string.icon_leaf),
            Icon("flower", R.drawable.ic_item_flower, R.string.icon_flower),
            Icon("sprout", R.drawable.ic_item_sprout, R.string.icon_sprout),
            Icon("paw", R.drawable.ic_item_paw, R.string.icon_paw))),
        Group(R.string.icon_group_shapes, listOf(
            Icon("tag", R.drawable.ic_fp_tags, R.string.icon_tag),
            Icon("circle", R.drawable.ic_item_circle, R.string.icon_circle),
            Icon("square", R.drawable.ic_item_square, R.string.icon_square),
            Icon("triangle", R.drawable.ic_item_triangle, R.string.icon_triangle),
            Icon("diamond", R.drawable.ic_item_diamond, R.string.icon_diamond),
            Icon("star", R.drawable.ic_item_star, R.string.icon_star),
            Icon("flag", R.drawable.ic_item_flag, R.string.icon_flag),
            Icon("bell", R.drawable.ic_item_bell, R.string.icon_bell),
            Icon("check", R.drawable.ic_item_check, R.string.icon_check),
            Icon("plus", R.drawable.ic_item_plus, R.string.icon_plus),
            Icon("bookmark", R.drawable.ic_item_bookmark, R.string.icon_bookmark),
            Icon("pin", R.drawable.ic_item_pin, R.string.icon_pin)))
    )
    val itemKeys = groups.flatMap { group -> group.icons.map { it.key } }
    val byKey: Map<String, Int> = mapOf("tags" to R.drawable.ic_fp_tags, "history" to R.drawable.ic_fp_history,
        "settings" to R.drawable.ic_fp_settings) + groups.flatMap { it.icons }.associate { it.key to it.drawable }
}
