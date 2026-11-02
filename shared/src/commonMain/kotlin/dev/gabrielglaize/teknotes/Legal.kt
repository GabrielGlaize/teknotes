package dev.gabrielglaize.teknotes

data class LegalSection(val title: String, val text: String)

/** Terms and privacy shown in the app (store requirement for apps with user content). */
object Legal {
    /** Contact shown in the app. To create before the app is distributed. */
    const val CONTACT_EMAIL = "contact@gabrielglaize.dev"

    val terms: List<LegalSection> = listOf(
        LegalSection(
            "L'app",
            "Teknotes est un carnet personnel et un annuaire de soundsystems et d'artistes, rempli par ceux qui l'utilisent. Elle est éditée par Gabriel Glaize et réservée aux personnes de 16 ans et plus.",
        ),
        LegalSection(
            "Ce que tu ajoutes",
            "Tu es responsable de ce que tu écris dans l'annuaire. Interdits : les contenus offensants, haineux ou illégaux, les données personnelles d'autres personnes (adresse, téléphone, vrai nom d'un artiste qui ne l'utilise pas publiquement), et tout lieu ou date permettant de trouver une soirée à venir.",
        ),
        LegalSection(
            "Modération",
            "Tout le monde peut signaler une fiche : trois signalements de personnes différentes la masquent en attendant une vérification. Tu peux bloquer une personne pour ne plus voir ses contributions dans l'historique ni ses nuits partagées. Un compte qui abuse de ces règles peut être supprimé.",
        ),
        LegalSection(
            "Ce que tu partages",
            "Tes nuits sont privées par défaut. Une nuit que tu choisis de partager est visible des autres à partir du lendemain, avec sa date, son département et ton pseudo.",
        ),
        LegalSection(
            "Sans garantie",
            "L'annuaire est écrit par ses utilisateurs : ses informations peuvent être fausses. L'app est fournie telle quelle, sans garantie de disponibilité. Elle n'encourage ni la participation à un événement ni la consommation de quoi que ce soit.",
        ),
        LegalSection("Contact", "Pour signaler un problème ou demander le retrait d'un contenu : $CONTACT_EMAIL."),
    )

    val privacy: List<LegalSection> = listOf(
        LegalSection(
            "Sans compte",
            "Rien ne quitte ton téléphone, à part la lecture de l'annuaire partagé. Ton carnet y reste.",
        ),
        LegalSection(
            "Avec un compte",
            "Sont envoyés sur le serveur : ton email (pour te connecter), ton pseudo, ton carnet (nuits, sons notés, notes et commentaires) et tes contributions à l'annuaire. Ton pseudo est la seule information sur toi visible des autres, avec les nuits que tu choisis de partager.",
        ),
        LegalSection(
            "Ce que l'app ne fait pas",
            "Pas de position GPS : le lieu d'une nuit s'arrête au département que tu choisis. Pas de photos, pas de publicité, pas de mesure d'audience, aucune donnée revendue.",
        ),
        LegalSection(
            "Où et combien de temps",
            "Les données sont hébergées chez Supabase et conservées tant que ton compte existe. En cas de suppression du compte, ton email, ton profil et ton carnet sont effacés du serveur ; tes ajouts à l'annuaire restent, sans ton nom.",
        ),
        LegalSection(
            "Tes droits",
            "Tu peux exporter ton carnet et supprimer ton compte depuis le profil. Pour toute autre demande (accès, rectification, opposition) : $CONTACT_EMAIL. Tu peux aussi saisir la CNIL.",
        ),
    )
}
