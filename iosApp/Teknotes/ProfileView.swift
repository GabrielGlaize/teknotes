import Observation
import Shared
import SwiftUI
import UIKit

@Observable
final class AccountModel {
    let teknotes: Teknotes
    private(set) var state: AccountState = AccountStateLoading.shared
    private(set) var sync: SyncState?
    private(set) var appearance: Appearance
    private(set) var fieldMode: Bool
    var email = ""
    var password = ""
    var code = ""
    var codeSent = false
    var pseudo = ""
    private(set) var busy = false
    private(set) var message: String?
    @ObservationIgnored private var watchers: [Watcher] = []

    init(teknotes: Teknotes) {
        self.teknotes = teknotes
        let current = teknotes.settings.current()
        appearance = current.appearance
        fieldMode = current.fieldMode
        watchers.append(teknotes.account.watchState { [weak self] state in self?.state = state })
        watchers.append(teknotes.sync.watchState { [weak self] state in self?.sync = state })
        watchers.append(teknotes.settings.watchPreferences { [weak self] preferences in
            self?.appearance = preferences.appearance
            self?.fieldMode = preferences.fieldMode
        })
    }

    deinit { watchers.forEach { $0.cancel() } }

    private func run(_ action: @escaping () async throws -> Void) {
        busy = true
        message = nil
        Task { @MainActor in
            do {
                try await action()
            } catch {
                message = error.localizedDescription
            }
            busy = false
        }
    }

    func signIn() { run { [self] in try await teknotes.account.signIn(email: email, password: password); password = "" } }
    func signUp() { run { [self] in try await teknotes.account.signUp(email: email, password: password); password = "" } }
    func sendCode() { run { [self] in try await teknotes.account.sendCode(email: email); codeSent = true } }
    func verifyCode() { run { [self] in try await teknotes.account.verifyCode(email: email, code: code); code = ""; codeSent = false } }
    func choosePseudo() { run { [self] in try await teknotes.account.choosePseudo(pseudo: pseudo) } }
    func syncNow() { run { [self] in try await teknotes.sync.syncNow() } }
    func signOut() { run { [self] in try await teknotes.account.signOut() } }
    func deleteAccount() { run { [self] in try await teknotes.account.deleteAccount() } }

    func setAppearance(_ value: Appearance) { teknotes.settings.setAppearance(appearance: value) }
    func setFieldMode(_ value: Bool) { teknotes.settings.setFieldMode(enabled: value) }

    /// Writes the export to a temporary file, ready to share.
    func exportFile(json: Bool) -> URL? {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(json ? "teknotes.json" : "teknotes.csv")
        let content = json ? teknotes.notebook.exportJson() : teknotes.notebook.exportCsv()
        do {
            try content.write(to: url, atomically: true, encoding: .utf8)
            return url
        } catch {
            message = "L'export n'a pas pu être écrit."
            return nil
        }
    }
}

private struct ExportedFile: Identifiable {
    let url: URL
    var id: URL { url }
}

struct ProfileView: View {
    @Bindable var model: AccountModel
    @State private var exported: ExportedFile?
    @State private var confirmDelete = false

    private var title: String {
        if let signedIn = model.state as? AccountStateSignedIn { return signedIn.pseudo }
        if model.state is AccountStateNeedsPseudo { return "Ton pseudo" }
        return "Sans compte"
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ScreenHeader(label: "Profil", title: title)
                    accountSection
                    if let message = model.message {
                        Text(message).foregroundStyle(Theme.pink)
                    }
                }

                Section {
                    Picker("Affichage", selection: Binding(get: { model.appearance }, set: { model.setAppearance($0) })) {
                        Text("Système").tag(Appearance.system)
                        Text("Sombre").tag(Appearance.dark)
                        Text("Clair").tag(Appearance.light)
                    }
                    .pickerStyle(.segmented)
                    Toggle(isOn: Binding(get: { model.fieldMode }, set: { model.setFieldMode($0) })) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Mode champ").font(Theme.body(16, weight: 600))
                            Text("Écran sombre, couleurs atténuées et texte plus grand, pour noter en pleine nuit.")
                                .font(Theme.body(13))
                                .foregroundStyle(Theme.mute)
                        }
                    }
                } header: {
                    FlyerLabel(text: "Apparence")
                }

                Section {
                    Button("Exporter en JSON") { exported = model.exportFile(json: true).map(ExportedFile.init) }
                    Button("Exporter en CSV") { exported = model.exportFile(json: false).map(ExportedFile.init) }
                } header: {
                    FlyerLabel(text: "Mon carnet")
                }
            }
            .flyerBackground()
            .sheet(item: $exported) { file in ShareSheet(url: file.url) }
            .confirmationDialog("Supprimer ton compte ?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Supprimer", role: .destructive) { model.deleteAccount() }
            } message: {
                Text("Ton compte et les nuits envoyées sur le serveur sont effacés. Ton carnet reste sur ce téléphone, et les fiches que tu as ajoutées à l'annuaire restent, sans ton nom.")
            }
        }
    }

    @ViewBuilder
    private var accountSection: some View {
        if let signedIn = model.state as? AccountStateSignedIn {
            Text(signedIn.email).foregroundStyle(Theme.mute)
            Text(syncText)
            Button("Synchroniser") { model.syncNow() }.disabled(model.busy || model.sync?.running == true)
            Button("Se déconnecter") { model.signOut() }.disabled(model.busy)
            Button("Supprimer mon compte", role: .destructive) { confirmDelete = true }.disabled(model.busy)
        } else if model.state is AccountStateNeedsPseudo {
            Text("Ton pseudo est la seule chose que les autres verront de toi.").foregroundStyle(Theme.mute)
            TextField("Pseudo", text: $model.pseudo).autocorrectionDisabled().textInputAutocapitalization(.never)
            Button("Valider") { model.choosePseudo() }.disabled(model.busy || model.pseudo.isEmpty)
        } else if model.state is AccountStateSignedOut {
            Text("Ton carnet marche sans compte et reste sur ce téléphone. Un compte sert à contribuer à l'annuaire partagé et à retrouver ton carnet sur un autre téléphone.")
                .foregroundStyle(Theme.mute)
            TextField("Email", text: $model.email)
                .keyboardType(.emailAddress)
                .textContentType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            if model.codeSent {
                Text("Un code à 6 chiffres vient de partir par mail.").foregroundStyle(Theme.mute)
                TextField("Code reçu", text: $model.code).keyboardType(.numberPad).textContentType(.oneTimeCode)
                Button("Valider le code") { model.verifyCode() }.disabled(model.busy || model.code.isEmpty)
                Button("Revenir") { model.codeSent = false }
            } else {
                SecureField("Mot de passe", text: $model.password).textContentType(.password)
                Button("Se connecter") { model.signIn() }.disabled(model.busy || model.email.isEmpty || model.password.isEmpty)
                Button("Créer un compte") { model.signUp() }.disabled(model.busy || model.email.isEmpty || model.password.isEmpty)
                Button("Recevoir plutôt un code par mail") { model.sendCode() }.disabled(model.busy || model.email.isEmpty)
            }
        } else if model.state is AccountStateUnavailable {
            Text("Ton carnet marche sans compte et reste sur ce téléphone. Le serveur de l'annuaire n'est pas encore branché sur cette version.")
                .foregroundStyle(Theme.mute)
        } else {
            Text("Un instant…").foregroundStyle(Theme.mute)
        }
    }

    private var syncText: String {
        guard let sync = model.sync else { return "Pas encore synchronisé" }
        if sync.running { return "Synchronisation…" }
        if let error = sync.error { return error }
        if sync.pending > 0 { return sync.pending == 1 ? "1 modification en attente" : "\(sync.pending) modifications en attente" }
        if let last = sync.lastSyncedAt {
            let date = Date(timeIntervalSince1970: TimeInterval(last.int64Value) / 1000)
            return "Synchronisé le " + date.formatted(.dateTime.day().month().hour().minute().locale(Locale(identifier: "fr_FR")))
        }
        return "Pas encore synchronisé"
    }
}

private struct ShareSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
