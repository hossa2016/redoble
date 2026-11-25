package com.hossa.redoble

/** Piezas del widget Completo por tema (generado por tools/split_widget_layouts.py). */
internal object WidgetPieces {
    fun of(family: String, name: String): Int = when (family) {
        "rock" -> when (name) {
            "header" -> R.layout.widget_rock_p_header
            "middle" -> R.layout.widget_rock_p_middle
            "middle_top" -> R.layout.widget_rock_p_middle_top
            "setlist" -> R.layout.widget_rock_p_setlist
            "dia" -> R.layout.widget_rock_p_dia
            "hora" -> R.layout.widget_rock_p_hora
            "fecha" -> R.layout.widget_rock_p_fecha
            "clima" -> R.layout.widget_rock_p_clima
            "bateria" -> R.layout.widget_rock_p_bateria
            else -> 0
        }
        "claro" -> when (name) {
            "header" -> R.layout.widget_claro_p_header
            "middle" -> R.layout.widget_claro_p_middle
            "middle_top" -> R.layout.widget_claro_p_middle_top
            "setlist" -> R.layout.widget_claro_p_setlist
            "dia" -> R.layout.widget_claro_p_dia
            "hora" -> R.layout.widget_claro_p_hora
            "clima" -> R.layout.widget_claro_p_clima
            "bateria" -> R.layout.widget_claro_p_bateria
            else -> 0
        }
        "cristal" -> when (name) {
            "header" -> R.layout.widget_cristal_p_header
            "middle" -> R.layout.widget_cristal_p_middle
            "middle_top" -> R.layout.widget_cristal_p_middle_top
            "setlist" -> R.layout.widget_cristal_p_setlist
            "dia" -> R.layout.widget_cristal_p_dia
            "hora" -> R.layout.widget_cristal_p_hora
            "fecha" -> R.layout.widget_cristal_p_fecha
            "clima" -> R.layout.widget_cristal_p_clima
            "bateria" -> R.layout.widget_cristal_p_bateria
            else -> 0
        }
        "neon" -> when (name) {
            "header" -> R.layout.widget_neon_p_header
            "middle" -> R.layout.widget_neon_p_middle
            "middle_top" -> R.layout.widget_neon_p_middle_top
            "setlist" -> R.layout.widget_neon_p_setlist
            "dia" -> R.layout.widget_neon_p_dia
            "hora" -> R.layout.widget_neon_p_hora
            "fecha" -> R.layout.widget_neon_p_fecha
            "clima" -> R.layout.widget_neon_p_clima
            "bateria" -> R.layout.widget_neon_p_bateria
            else -> 0
        }
        "lujo" -> when (name) {
            "header" -> R.layout.widget_lujo_p_header
            "middle" -> R.layout.widget_lujo_p_middle
            "middle_top" -> R.layout.widget_lujo_p_middle_top
            "setlist" -> R.layout.widget_lujo_p_setlist
            "dia" -> R.layout.widget_lujo_p_dia
            "hora" -> R.layout.widget_lujo_p_hora
            "fecha" -> R.layout.widget_lujo_p_fecha
            "clima" -> R.layout.widget_lujo_p_clima
            "bateria" -> R.layout.widget_lujo_p_bateria
            else -> 0
        }
        else -> of("rock", name)
    }
}
