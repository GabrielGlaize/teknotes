import XCTest

/// Real paths through the app, on a simulator. The account test needs the local Supabase
/// (supabase start); the screenshots go to the folder named by TEKNOTES_SHOTS, if set.
final class TeknotesUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    private func shot(_ name: String) {
        guard let folder = ProcessInfo.processInfo.environment["TEKNOTES_SHOTS"] else { return }
        let data = XCUIScreen.main.screenshot().pngRepresentation
        try? data.write(to: URL(fileURLWithPath: folder).appendingPathComponent("\(name).png"))
    }

    private func type(_ text: String, in field: XCUIElement) {
        XCTAssertTrue(field.waitForExistence(timeout: 10), "champ introuvable : \(field)")
        field.tap()
        field.typeText(text)
    }

    func testANightIsNotedInTwoTapsEvenWithATypo() {
        app.buttons["J'y suis"].tap()
        type("kaisson", in: app.textFields["Soundsystem ou artiste entendu"])
        let hit = app.staticTexts["Caisson Noir"]
        XCTAssertTrue(hit.waitForExistence(timeout: 5), "la recherche tolère la faute")
        shot("ios-search")
        hit.tap()
        XCTAssertTrue(app.staticTexts["Caisson Noir"].waitForExistence(timeout: 5), "l'entrée s'ajoute à la nuit")
        shot("ios-night")
    }

    func testADuplicateIsSuggestedBeforeCreating() {
        app.tabBars.buttons["Annuaire"].tap()
        app.buttons["Ajouter un soundsystem"].tap()
        type("Teknokrate", in: app.textFields["Nom"])
        XCTAssertTrue(app.staticTexts["Teknokrates"].waitForExistence(timeout: 5), "la fiche proche est proposée")
        shot("ios-create")
    }

    /// Signs up from the profile, unless this simulator is already signed in.
    private func signUpIfNeeded() {
        app.tabBars.buttons["Profil"].tap()
        let email = app.textFields["Email"]
        guard email.waitForExistence(timeout: 5) else { return }
        let id = Int.random(in: 1...1_000_000_000)
        type("ios\(id)@teknotes.test", in: email)
        type("mot-de-passe-solide", in: app.secureTextFields["Mot de passe"])
        shot("ios-profile")
        app.buttons["Créer un compte"].tap()

        type("iPhone\(id)\n", in: app.textFields["Pseudo"])
        let valider = app.buttons["Valider"]
        XCTAssertFalse(valider.isEnabled, "il faut accepter les conditions d'abord")
        let terms = app.switches["J'accepte les conditions d'utilisation"]
        terms.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
        shot("ios-terms")
        valider.tap()
    }

    func testAnAccountIsCreatedAndTheNotebookSynced() {
        signUpIfNeeded()
        let synced = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Synchronisé le'")).firstMatch
        XCTAssertTrue(synced.waitForExistence(timeout: 20), "le carnet part sur le serveur")
        shot("ios-signed-in")
    }

    func testAnEntryIsEditedAndItsHistoryShown() {
        signUpIfNeeded()
        XCTAssertTrue(app.buttons["Personnes bloquées"].waitForExistence(timeout: 20), "connecté")
        app.tabBars.buttons["Annuaire"].tap()
        app.staticTexts["Basse Marée"].tap()
        shot("ios-fiche")

        let edit = app.buttons["Modifier la fiche"]
        XCTAssertTrue(edit.waitForExistence(timeout: 5), "une fiche se modifie une fois connecté")
        edit.tap()
        let alias = "Alias \(Int.random(in: 1...1_000_000))"
        let aliases = app.textFields.matching(NSPredicate(format: "placeholderValue BEGINSWITH 'Aussi appelé'")).firstMatch
        XCTAssertTrue(aliases.waitForExistence(timeout: 5))
        aliases.tap()
        aliases.typeText(", \(alias)")
        shot("ios-edit")
        app.buttons["Enregistrer"].tap()
        let shown = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", alias)).firstMatch
        XCTAssertTrue(shown.waitForExistence(timeout: 5), "la fiche montre la modification")

        app.buttons["Voir l'historique"].tap()
        let change = app.staticTexts["Modifié : Aussi appelé"]
        XCTAssertTrue(change.waitForExistence(timeout: 20), "l'historique montre ce qui a changé")
        shot("ios-history")
        app.navigationBars.buttons.firstMatch.tap()

        app.buttons["Signaler un problème"].tap()
        XCTAssertTrue(app.buttons["Contenu offensant"].waitForExistence(timeout: 5), "les motifs de signalement s'affichent")
        shot("ios-report")
    }

    func testTheTermsAndContactAreReachable() {
        app.tabBars.buttons["Profil"].tap()
        let terms = app.buttons["Conditions d'utilisation"]
        for _ in 0..<5 where !terms.isHittable { app.swipeUp() }
        terms.tap()
        XCTAssertTrue(app.staticTexts["Modération"].waitForExistence(timeout: 5))
        shot("ios-legal")
    }
}
