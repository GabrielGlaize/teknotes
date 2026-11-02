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

    func testAnAccountIsCreatedAndTheNotebookSynced() throws {
        app.tabBars.buttons["Profil"].tap()
        let email = app.textFields["Email"]
        guard email.waitForExistence(timeout: 10) else {
            throw XCTSkip("Déjà connecté sur ce simulateur")
        }
        let id = Int.random(in: 1...1_000_000_000)
        type("ios\(id)@teknotes.test", in: email)
        type("mot-de-passe-solide", in: app.secureTextFields["Mot de passe"])
        shot("ios-profile")
        app.buttons["Créer un compte"].tap()

        type("iPhone\(id)", in: app.textFields["Pseudo"])
        app.buttons["Valider"].tap()

        let synced = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Synchronisé le'")).firstMatch
        XCTAssertTrue(synced.waitForExistence(timeout: 20), "le carnet part sur le serveur")
        shot("ios-signed-in")
    }
}
