import XCTest

/// Real paths through the app, on a simulator. The account test needs the local Supabase
/// (supabase start); the screenshots go to the folder named by TEKNOTES_SHOTS, if set.
final class TeknotesUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        // The first-launch test starts from a fresh install, without skipping anything.
        guard !name.contains("testTheTutorial") else { return }
        app.launchArguments = ["-skipIntro"]
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
        type("kaisson", in: app.textFields["Artiste ou soundsystem"])
        let hit = app.buttons["Rouille (Caisson Noir)"]
        XCTAssertTrue(hit.waitForExistence(timeout: 5), "la recherche tolère la faute et propose le set")
        shot("ios-search")
        hit.tap()
        XCTAssertTrue(app.staticTexts["Rouille (Caisson Noir)"].waitForExistence(timeout: 5), "le set s'ajoute à la nuit")
        shot("ios-night")
    }

    func testTheKeyboardClosesOnATapOutside() {
        app.buttons["J'y suis"].tap()
        let field = app.textFields["Artiste ou soundsystem"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
        app.staticTexts["Souvenirs"].tap()
        let gone = expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: app.keyboards.firstMatch)
        wait(for: [gone], timeout: 5)
    }

    func testSharingANightAsksForAnAccount() {
        app.buttons["J'y suis"].tap()
        let share = app.staticTexts["Il faut un compte pour partager une nuit."]
        for _ in 0..<3 where !share.exists { app.swipeUp() }
        if app.switches.matching(NSPredicate(format: "label BEGINSWITH 'Partager'")).firstMatch.exists {
            return // this simulator is signed in
        }
        XCTAssertTrue(share.waitForExistence(timeout: 5), "pas d'interrupteur sans compte")
        shot("ios-share")
    }

    func testTheTutorialThenTheCodeProtectTheApp() {
        let fresh = XCUIApplication()
        fresh.launch()
        // A simulator that already went through it opens directly on the notebook.
        guard fresh.buttons["Continuer"].waitForExistence(timeout: 5) else { return }
        shot("ios-tuto")
        fresh.buttons["Continuer"].tap()
        fresh.buttons["Continuer"].tap()
        fresh.buttons["Choisir mon code"].tap()
        for _ in 0..<2 {
            XCTAssertTrue(fresh.buttons["4"].waitForExistence(timeout: 5))
            for digit in ["4", "8", "2", "1"] { fresh.buttons[digit].tap() }
            sleep(1)
        }
        XCTAssertTrue(fresh.staticTexts["Tek'Notes !"].waitForExistence(timeout: 5))
        fresh.terminate()
        fresh.launch()
        XCTAssertTrue(fresh.staticTexts["Entre ton code"].waitForExistence(timeout: 5), "le code est demandé à l'ouverture")
        shot("ios-lock")
        for digit in ["4", "8", "2", "1"] { fresh.buttons[digit].tap() }
        XCTAssertTrue(fresh.buttons["J'y suis"].waitForExistence(timeout: 5))
        shot("ios-carnet-vide")
    }

    func testSwipingMovesFromOneTabToTheNext() {
        XCTAssertTrue(app.staticTexts["Tek'Notes !"].waitForExistence(timeout: 5))
        app.swipeLeft()
        XCTAssertTrue(app.staticTexts["Annuaire"].waitForExistence(timeout: 5), "l'écran suit le doigt")
        XCTAssertTrue(app.buttons["Annuaire"].isSelected, "la barre suit aussi")
    }

    func testADuplicateIsSuggestedBeforeCreating() {
        app.buttons["Annuaire"].tap()
        app.buttons["Ajouter un soundsystem"].tap()
        type("Teknokrate", in: app.textFields["Nom"])
        XCTAssertTrue(app.staticTexts["Teknokrates"].waitForExistence(timeout: 5), "la fiche proche est proposée")
        shot("ios-create")
    }

    /// Signs up from the profile, unless this simulator is already signed in.
    private func signUpIfNeeded() {
        app.buttons["Profil"].tap()
        let create = app.buttons["Créer un compte"]
        guard create.waitForExistence(timeout: 5) else { return }
        shot("ios-profile")
        create.tap()
        let id = Int.random(in: 1...1_000_000_000)
        type("ios\(id)@teknotes.test", in: app.textFields["Email"])
        type("mot-de-passe-solide", in: app.secureTextFields["Mot de passe (6 caractères minimum)"])
        shot("ios-signup")
        app.buttons["Créer mon compte"].tap()

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
        app.buttons["Annuaire"].tap()
        app.buttons["Public"].tap()
        let basse = app.staticTexts["Basse Marée"]
        XCTAssertTrue(basse.waitForExistence(timeout: 5))
        basse.tap()
        shot("ios-fiche")

        let edit = app.buttons["Modifier la fiche"]
        XCTAssertTrue(edit.waitForExistence(timeout: 5), "une fiche se modifie une fois connecté")
        edit.tap()
        let alias = "Alias \(Int.random(in: 1...1_000_000))"
        let aliases = app.textFields["Aussi appelé (séparés par des virgules)"]
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
        app.buttons["Profil"].tap()
        let terms = app.buttons["Conditions d'utilisation"]
        for _ in 0..<5 where !terms.isHittable { app.swipeUp() }
        terms.tap()
        XCTAssertTrue(app.staticTexts["Modération"].waitForExistence(timeout: 5))
        shot("ios-legal")
    }

    /// With the demo server (scripts/iphone.sh): signing in brings the seeded notebook, the
    /// shared nights, the history and a merge to approve. Run with TEST_RUNNER_TEKNOTES_DEMO=1.
    func testTheDemoAccountBringsTheSeededNotebook() throws {
        guard ProcessInfo.processInfo.environment["TEKNOTES_DEMO"] == "1" else {
            throw XCTSkip("Demande le serveur de démo : scripts/iphone.sh")
        }
        app.buttons["Profil"].tap()
        shot("demo-profil-sans-compte")
        app.buttons["Se connecter"].tap()
        type("demo@teknotes.test", in: app.textFields["Email"])
        type("teknotes-demo", in: app.secureTextFields["Mot de passe"])
        shot("demo-connexion")
        app.buttons["Me connecter"].tap()
        XCTAssertTrue(app.staticTexts["Gabriel"].waitForExistence(timeout: 15), "connecté")
        shot("demo-profil")
        let synced = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Synchronisé le'")).firstMatch
        XCTAssertTrue(synced.waitForExistence(timeout: 20))

        app.buttons["Carnet"].tap()
        XCTAssertTrue(app.staticTexts["Teknival du Nord"].waitForExistence(timeout: 10), "le carnet de démo est arrivé")
        XCTAssertTrue(app.staticTexts["Week-end au bord de l'eau"].exists)
        shot("demo-carnet")
        app.staticTexts["Teknival du Nord"].tap()
        XCTAssertTrue(app.staticTexts["Pyl (Caisson Noir)"].waitForExistence(timeout: 5), "un set dit qui joue sur quel soundsystem")
        shot("demo-nuit")
        app.navigationBars.buttons.firstMatch.tap()

        app.buttons["Annuaire"].tap()
        XCTAssertTrue(app.staticTexts["Caisson Noir"].waitForExistence(timeout: 5), "mon annuaire reprend ce que j'ai noté")
        shot("demo-annuaire-mien")
        let rouille = app.staticTexts["Rouille"]
        XCTAssertTrue(rouille.waitForExistence(timeout: 5))
        rouille.tap()
        let note = app.staticTexts["Set hardtek très propre"]
        XCTAssertTrue(note.waitForExistence(timeout: 5), "la fiche d'un artiste reprend toutes mes notes sur lui")
        shot("demo-fiche-artiste")
        app.navigationBars.buttons.firstMatch.tap()
        app.buttons["Public"].tap()
        shot("demo-annuaire-public")
        app.buttons["Style ▾"].tap()
        XCTAssertTrue(app.staticTexts["Hardtek"].waitForExistence(timeout: 5))
        shot("demo-style-filtre")
        app.buttons["OK"].tap()

        app.staticTexts["Caisson Noir"].firstMatch.tap()
        let sighting = app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'Moktar'")).firstMatch
        XCTAssertTrue(sighting.waitForExistence(timeout: 10), "nuit partagée par un autre")
        let approve = app.buttons["Approuver"]
        XCTAssertTrue(approve.waitForExistence(timeout: 10), "une fusion attend un accord")
        shot("demo-fiche")
        approve.tap()
        // The merge was applied: the proposal leaves the screen, and the copy's name is now an alias.
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: approve)
        waitForExpectations(timeout: 15)
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'Caisson Noir Sound'")).firstMatch.exists, "le nom du doublon est devenu un alias")
        shot("demo-fusion")
    }
}
