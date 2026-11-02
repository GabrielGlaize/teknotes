package dev.gabrielglaize.teknotes

/** Closed list of styles, managed in the app so the directory doesn't fill with near-duplicates. */
enum class Style(val id: String, val label: String) {
    TEKNO("tekno", "Tekno"),
    TRIBE("tribe", "Tribe"),
    HARDTEK("hardtek", "Hardtek"),
    ACID("acid", "Acid"),
    MENTAL("mental", "Mental"),
    FRENCHCORE("frenchcore", "Frenchcore"),
    HARDCORE("hardcore", "Hardcore"),
    SPEEDCORE("speedcore", "Speedcore"),
    RAGGATEK("raggatek", "Raggatek"),
    TECHNO("techno", "Techno"),
    MINIMAL("minimal", "Minimal"),
    PSYTRANCE("psytrance", "Psytrance"),
    DRUM_AND_BASS("dnb", "Drum & bass"),
    DUB("dub", "Dub");

    companion object {
        fun fromId(id: String): Style? = entries.firstOrNull { it.id == id }
    }
}
