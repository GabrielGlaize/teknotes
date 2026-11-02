package dev.gabrielglaize.teknotes

/**
 * Clearly fictional directory used for tests and the test build, so testers don't
 * start with an empty directory. The same names are in supabase/seed.sql.
 */
internal object DemoData {
    fun seed(directory: DirectoryRepository) {
        val soundsystems = listOf(
            Soundsystem("5d000000-0000-4000-8000-000000000001", "Caisson Noir", listOf("Caisson Noir Sound System", "CNS"), "59", listOf(Style.HARDTEK, Style.TRIBE), emptyList()),
            Soundsystem("5d000000-0000-4000-8000-000000000002", "Les Ondes Sauvages", listOf("Ondes Sauvages"), "29", listOf(Style.TRIBE, Style.ACID), emptyList()),
            Soundsystem("5d000000-0000-4000-8000-000000000003", "Kollectif Brume", listOf("Brume"), "35", listOf(Style.MENTAL, Style.ACID), emptyList()),
            Soundsystem("5d000000-0000-4000-8000-000000000004", "Mur du Nord", listOf("MDN"), "62", listOf(Style.FRENCHCORE, Style.HARDCORE), emptyList()),
            Soundsystem("5d000000-0000-4000-8000-000000000005", "Tribu des Talus", listOf("Talus Tribe"), "46", listOf(Style.TRIBE, Style.RAGGATEK), emptyList()),
            Soundsystem("5d000000-0000-4000-8000-000000000006", "Teknokrates", listOf("Tekno Krates", "TKS"), "31", listOf(Style.TEKNO, Style.HARDTEK), emptyList()),
            Soundsystem("5d000000-0000-4000-8000-000000000007", "Basse Marée", emptyList(), "56", listOf(Style.DUB, Style.TEKNO), emptyList()),
            Soundsystem("5d000000-0000-4000-8000-000000000008", "Onde de Choc", listOf("ODC"), Department.ABROAD, listOf(Style.SPEEDCORE, Style.HARDCORE), emptyList()),
        )
        val artists = listOf(
            Artist("5a000000-0000-4000-8000-000000000001", "Rouille", emptyList(), listOf(Style.HARDTEK), emptyList()),
            Artist("5a000000-0000-4000-8000-000000000002", "Lunatek", listOf("Luna Tek"), listOf(Style.TRIBE, Style.ACID), emptyList()),
            Artist("5a000000-0000-4000-8000-000000000003", "Moktar Acid", listOf("Moktar"), listOf(Style.ACID), emptyList()),
            Artist("5a000000-0000-4000-8000-000000000004", "Kasba", emptyList(), listOf(Style.MENTAL), emptyList()),
            Artist("5a000000-0000-4000-8000-000000000005", "Djette Brindille", listOf("Brindille"), listOf(Style.TRIBE), emptyList()),
            Artist("5a000000-0000-4000-8000-000000000006", "Sub Zéro", listOf("Subzero"), listOf(Style.FRENCHCORE), emptyList()),
            Artist("5a000000-0000-4000-8000-000000000007", "Grésil", emptyList(), listOf(Style.HARDCORE, Style.SPEEDCORE), emptyList()),
            Artist("5a000000-0000-4000-8000-000000000008", "Talkie", emptyList(), listOf(Style.RAGGATEK), emptyList()),
            Artist("5a000000-0000-4000-8000-000000000009", "Pyl", listOf("PYL"), listOf(Style.TEKNO), emptyList()),
            Artist("5a000000-0000-4000-8000-000000000010", "Écho Bleu", listOf("Echo Bleu"), listOf(Style.DUB), emptyList()),
        )
        val members = listOf(
            "5d000000-0000-4000-8000-000000000001" to "5a000000-0000-4000-8000-000000000001", "5d000000-0000-4000-8000-000000000001" to "5a000000-0000-4000-8000-000000000009",
            "5d000000-0000-4000-8000-000000000002" to "5a000000-0000-4000-8000-000000000002", "5d000000-0000-4000-8000-000000000002" to "5a000000-0000-4000-8000-000000000005",
            "5d000000-0000-4000-8000-000000000003" to "5a000000-0000-4000-8000-000000000003", "5d000000-0000-4000-8000-000000000003" to "5a000000-0000-4000-8000-000000000004",
            "5d000000-0000-4000-8000-000000000004" to "5a000000-0000-4000-8000-000000000006", "5d000000-0000-4000-8000-000000000004" to "5a000000-0000-4000-8000-000000000007",
            "5d000000-0000-4000-8000-000000000005" to "5a000000-0000-4000-8000-000000000008", "5d000000-0000-4000-8000-000000000006" to "5a000000-0000-4000-8000-000000000009",
            "5d000000-0000-4000-8000-000000000007" to "5a000000-0000-4000-8000-000000000010", "5d000000-0000-4000-8000-000000000008" to "5a000000-0000-4000-8000-000000000007",
        )
        soundsystems.forEach(directory::save)
        artists.forEach(directory::save)
        members.forEach { (soundsystem, artist) -> directory.addMember(soundsystem, artist) }
    }
}
