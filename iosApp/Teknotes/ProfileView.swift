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
    var email = ""
    var password = ""
    var code = ""
    var codeSent = false
    var pseudo = ""
    var termsAccepted = false
    private(set) var busy = false
    private(set) var message: String?
    @ObservationIgnored private var watchers: [Watcher] = []

    init(teknotes: Teknotes) {
        self.teknotes = teknotes
        appearance = teknotes.settings.current().appearance
        watchers.append(teknotes.account.watchState { [weak self] state in self?.state = state })
        watchers.append(teknotes.sync.watchState { [weak self] state in self?.sync = state })
        watchers.append(teknotes.settings.watchPreferences { [weak self] preferences in
            self?.appearance = preferences.appearance
        })
    }

    deinit { watchers.forEach { $0.cancel() } }

    var isSignedIn: Bool { state is AccountStateSignedIn }
    var needsPseudo: Bool { state is AccountStateNeedsPseudo }
    var isUnavailable: Bool { state is AccountStateUnavailable }

    func clearMessage() { message = nil }

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

/// Opens the account screens on the chosen path.
private struct AuthRequest: Identifiable {
    let mode: AuthSheet.Mode
    var id: String { "\(mode)" }
}

struct ProfileView: View {
    @Bindable var model: AccountModel
    @State private var exported: ExportedFile?
    @State private var confirmDelete = false
    @State private var auth: AuthRequest?
    @State private var replayingTutorial = false

    private var title: String {
        (model.state as? AccountStateSignedIn)?.pseudo ?? "Profil"
    }

    private var appearanceName: (Appearance) -> String {
        { $0 == .dark ? "Sombre" : ($0 == .light ? "Clair" : "Système") }
    }

    var body: some View {
        NavigationStack {
            Page {
                BigTitle(text: title).padding(.top, 12)
                accountSection

                SectionHead(text: "Affichage")
                WordTabs(
                    items: [Appearance.dark, Appearance.light, Appearance.system],
                    selection: Binding(get: { model.appearance }, set: { model.setAppearance($0) }),
                    label: appearanceName,
                    size: 15
                )

                SectionHead(text: "Sécurité")
                SecuritySection(teknotes: model.teknotes)

                SectionHead(text: "Mon carnet")
                RowButton(title: "Exporter en JSON") { exported = model.exportFile(json: true).map(ExportedFile.init) }
                RowButton(title: "Exporter en CSV") { exported = model.exportFile(json: false).map(ExportedFile.init) }

                SectionHead(text: "À propos")
                RowButton(title: "Revoir le tuto") { replayingTutorial = true }
                NavigationLink { LegalView(privacy: false) } label: { RowLabel(title: "Conditions d'utilisation") }
                    .buttonStyle(.plain)
                NavigationLink { LegalView(privacy: true) } label: { RowLabel(title: "Confidentialité") }
                    .buttonStyle(.plain)
                Text("Contact : \(Legal.shared.CONTACT_EMAIL)")
                    .font(Theme.text(14))
                    .foregroundStyle(Theme.mute)
                    .textSelection(.enabled)
                    .padding(.top, 14)

                if model.isSignedIn {
                    SectionHead(text: "Compte")
                    RowButton(title: "Se déconnecter") { model.signOut() }.disabled(model.busy)
                    RowButton(title: "Supprimer mon compte", color: Theme.accent) { confirmDelete = true }.disabled(model.busy)
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .sheet(item: $exported) { file in ShareSheet(url: file.url) }
            .sheet(item: $auth) { request in AuthSheet(mode: request.mode) }
            .fullScreenCover(isPresented: $replayingTutorial) {
                IntroView(teknotes: model.teknotes, replay: true) { replayingTutorial = false }
            }
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
            Note(text: signedIn.email).padding(.top, 4)
            SectionHead(text: "Synchronisation")
            HStack {
                Text(syncText).font(Theme.text(15))
                Spacer(minLength: 8)
                TextAction(title: "Synchroniser") { model.syncNow() }
                    .disabled(model.busy || model.sync?.running == true)
            }
            Hairline()
            NavigationLink { BlockedView(teknotes: model.teknotes) } label: { RowLabel(title: "Personnes bloquées") }
                .buttonStyle(.plain)
            messageLine
        } else if model.needsPseudo {
            Note(text: "Il ne te reste qu'à choisir ton pseudo.").padding(.top, 8)
            ActionButton(title: "Choisir mon pseudo") { open(.signUp) }.padding(.top, 16)
        } else if model.state is AccountStateSignedOut {
            Note(text: "Ton carnet marche sans compte et reste sur ce téléphone. Un compte sert à contribuer à l'annuaire, à partager tes nuits et à retrouver ton carnet ailleurs.")
                .padding(.top, 8)
            ActionButton(title: "Créer un compte") { open(.signUp) }.padding(.top, 20)
            ActionButton(title: "Se connecter", kind: .outline) { open(.signIn) }.padding(.top, 10)
            messageLine
        } else if model.isUnavailable {
            Note(text: "Ton carnet marche sans compte et reste sur ce téléphone. Le serveur de l'annuaire n'est pas encore branché sur cette version.")
                .padding(.top, 8)
        } else {
            Note(text: "Un instant…").padding(.top, 8)
        }
    }

    @ViewBuilder
    private var messageLine: some View {
        if let message = model.message {
            Text(message).font(Theme.text(14, weight: 600)).foregroundStyle(Theme.accent).padding(.top, 8)
        }
    }

    private func open(_ mode: AuthSheet.Mode) {
        model.clearMessage()
        auth = AuthRequest(mode: mode)
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
