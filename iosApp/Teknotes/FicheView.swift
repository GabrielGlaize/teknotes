import Observation
import Shared
import SwiftUI

/// A soundsystem or an artist: its content, and what the community can do with it.
@Observable
final class FicheModel {
    let teknotes: Teknotes
    let kind: DirectoryHit.Kind
    let id: String
    private(set) var soundsystem: Soundsystem?
    private(set) var artist: Artist?
    private(set) var related: [(id: String, name: String)] = []
    private(set) var signedIn = false
    private(set) var sightings: [Sighting] = []
    private(set) var proposals: [MergeProposal] = []
    private(set) var busy = false
    private(set) var message: String?
    var mergeQuery = "" {
        didSet { mergeResults = teknotes.directory.search(query: mergeQuery).filter { $0.kind == kind && $0.id != id }.prefix(6).map { $0 } }
    }
    private(set) var mergeResults: [DirectoryHit] = []
    @ObservationIgnored private var watchers: [Watcher] = []

    init(teknotes: Teknotes, kind: DirectoryHit.Kind, id: String) {
        self.teknotes = teknotes
        self.kind = kind
        self.id = id
        read()
        watchers.append(teknotes.directory.watchSoundsystems { [weak self] _ in self?.read() })
        watchers.append(teknotes.directory.watchArtists { [weak self] _ in self?.read() })
        watchers.append(teknotes.account.watchState { [weak self] state in
            self?.signedIn = state is AccountStateSignedIn
            self?.refresh()
        })
    }

    deinit { watchers.forEach { $0.cancel() } }

    var isSoundsystem: Bool { kind == .soundsystem }
    var name: String? { isSoundsystem ? soundsystem?.name : artist?.name }

    private func read() {
        if isSoundsystem {
            soundsystem = teknotes.directory.soundsystem(id: id)
            related = teknotes.directory.membersOf(soundsystemId: id).map { ($0.id, $0.name) }
        } else {
            artist = teknotes.directory.artist(id: id)
            related = teknotes.directory.soundsystemsOf(artistId: id).map { ($0.id, $0.name) }
        }
    }

    func refresh() {
        Task { @MainActor in
            // Offline: the entry still shows, without what needs the server.
            sightings = (try? await teknotes.community.sightings(kind: kind, id: id)) ?? sightings
            proposals = (try? await teknotes.community.mergeProposals(kind: kind, id: id)) ?? proposals
        }
    }

    private func run(done: String? = nil, _ action: @escaping () async throws -> Void) {
        busy = true
        message = nil
        Task { @MainActor in
            do {
                try await action()
                message = done
                refresh()
            } catch {
                message = error.localizedDescription
            }
            busy = false
        }
    }

    func proposeMerge(absorbed: DirectoryHit) {
        run(done: "Fusion proposée. Il faut deux autres approbations.") { [self] in
            // What's created offline must reach the server before it can be merged.
            try await teknotes.sync.syncNow()
            try await teknotes.community.proposeMerge(kind: kind, keptId: id, absorbedId: absorbed.id)
            mergeQuery = ""
        }
    }

    func approve(_ proposal: MergeProposal) {
        run { [self] in
            let applied = try await teknotes.community.approveMerge(proposalId: proposal.id)
            if applied.boolValue { try await teknotes.sync.syncNow() }
        }
    }

    func report(_ reason: ReportReason) {
        run(done: "Merci, c'est noté.") { [self] in
            try await teknotes.community.report(kind: kind, id: id, reason: reason)
            teknotes.sync.schedule()
        }
    }

    func block(_ sighting: Sighting) {
        run(done: "\(sighting.pseudo) est bloqué.") { [self] in
            try await teknotes.community.block(userId: sighting.userId)
        }
    }
}

private struct Fact: View {
    let label: String
    let value: String

    var body: some View {
        if !value.isEmpty {
            VStack(alignment: .leading, spacing: 2) {
                FlyerLabel(text: label)
                Text(value).font(Theme.body(16)).textSelection(.enabled)
            }
        }
    }
}

struct FicheView: View {
    @State private var model: FicheModel
    @State private var editing = false
    @State private var merging = false
    @State private var reporting = false
    @Environment(\.dismiss) private var dismiss

    init(teknotes: Teknotes, kind: DirectoryHit.Kind, id: String) {
        _model = State(initialValue: FicheModel(teknotes: teknotes, kind: kind, id: id))
    }

    var body: some View {
        Group {
            if let name = model.name {
                list(name: name)
            } else {
                // Gone from the directory (hidden after reports, merged).
                Text("Cette fiche n'est plus dans l'annuaire.").foregroundStyle(Theme.mute)
            }
        }
        .sheet(isPresented: $editing) {
            CreateView(teknotes: model.teknotes, kind: model.kind, name: "", mode: .edit(id: model.id)) { _ in }
        }
        .sheet(isPresented: $merging) { MergeSheet(model: model) }
        .confirmationDialog("Quel est le problème ?", isPresented: $reporting, titleVisibility: .visible) {
            ForEach(ReportReason.entries, id: \.self) { reason in
                Button(reason.label) { model.report(reason) }
            }
        } message: {
            Text("Trois signalements de personnes différentes masquent la fiche en attendant une vérification.")
        }
    }

    private func list(name: String) -> some View {
        List {
            Section {
                ScreenHeader(label: model.isSoundsystem ? "Soundsystem" : "Artiste", title: name)
                if let soundsystem = model.soundsystem {
                    Fact(label: "Origine", value: Departments.shared.byCode(code: soundsystem.department)?.label ?? "Inconnue")
                    Fact(label: "Styles", value: soundsystem.styles.map(\.label).joined(separator: " · "))
                    Fact(label: "Aussi appelé", value: soundsystem.aliases.joined(separator: ", "))
                    Fact(label: "Liens", value: soundsystem.links.joined(separator: "\n"))
                } else if let artist = model.artist {
                    Fact(label: "Styles", value: artist.styles.map(\.label).joined(separator: " · "))
                    Fact(label: "Aussi appelé", value: artist.aliases.joined(separator: ", "))
                    Fact(label: "Liens", value: artist.links.joined(separator: "\n"))
                }
            }

            if !model.related.isEmpty {
                Section {
                    ForEach(model.related, id: \.id) { item in
                        NavigationLink(item.name) {
                            FicheView(teknotes: model.teknotes, kind: model.isSoundsystem ? .artist : .soundsystem, id: item.id)
                        }
                    }
                } header: {
                    FlyerLabel(text: model.isSoundsystem ? "Membres" : "Soundsystems")
                }
            }

            if !model.proposals.isEmpty {
                Section {
                    ForEach(model.proposals, id: \.id) { proposal in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(proposal.keepsThisEntry ? "« \(proposal.otherName) » fondu ici" : "Fondu dans « \(proposal.otherName) »")
                                    .font(Theme.body(15, weight: 600))
                                Text("\(proposal.approvals) accord\(proposal.approvals > 1 ? "s" : "") sur \(proposal.needed)")
                                    .font(Theme.body(13))
                                    .foregroundStyle(Theme.mute)
                            }
                            Spacer()
                            if !proposal.approvedByMe {
                                Button("Approuver") { model.approve(proposal) }.disabled(model.busy).buttonStyle(.borderless)
                            }
                        }
                    }
                } header: {
                    FlyerLabel(text: "Fusions proposées")
                }
            }

            if !model.sightings.isEmpty {
                Section {
                    ForEach(model.sightings, id: \.self) { sighting in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(NightDate.format(sighting.date)).font(Theme.body(15, weight: 600))
                                Text([Departments.shared.byCode(code: sighting.department)?.label, sighting.pseudo].compactMap { $0 }.joined(separator: " · "))
                                    .font(Theme.body(13))
                                    .foregroundStyle(Theme.mute)
                            }
                            Spacer()
                            Button("Bloquer") { model.block(sighting) }.disabled(model.busy).buttonStyle(.borderless)
                        }
                    }
                } header: {
                    FlyerLabel(text: "Nuits partagées")
                }
            }

            if let message = model.message {
                Text(message).foregroundStyle(Theme.pink)
            }

            Section {
                if model.signedIn {
                    Button("Modifier la fiche") { editing = true }
                    NavigationLink("Voir l'historique") {
                        HistoryView(model: HistoryModel(teknotes: model.teknotes, kind: model.kind, id: model.id))
                    }
                    Button("Signaler un doublon") { merging = true }
                    Button("Signaler un problème", role: .destructive) { reporting = true }
                } else {
                    Text("Connecte-toi depuis le profil pour modifier cette fiche, voir son historique ou la signaler.")
                        .font(Theme.body(15))
                        .foregroundStyle(Theme.mute)
                }
            } header: {
                FlyerLabel(text: "L'annuaire est à tout le monde")
            }
        }
        .flyerBackground()
    }
}

private struct MergeSheet: View {
    @Bindable var model: FicheModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Chercher la fiche", text: $model.mergeQuery).autocorrectionDisabled()
                    ForEach(model.mergeResults, id: \.id) { hit in
                        Button {
                            model.proposeMerge(absorbed: hit)
                            dismiss()
                        } label: {
                            HitRow(hit: hit)
                        }
                    }
                } footer: {
                    Text("La fiche choisie sera fondue dans « \(model.name ?? "") » quand trois personnes seront d'accord.")
                }
            }
            .flyerBackground()
            .navigationTitle("C'est le doublon de quelle fiche ?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
            }
        }
    }
}

/// Every version of an entry; any of them can be put back.
@Observable
final class HistoryModel {
    let teknotes: Teknotes
    let kind: DirectoryHit.Kind
    let id: String
    private(set) var revisions: [Revision] = []
    private(set) var loading = true
    private(set) var message: String?

    init(teknotes: Teknotes, kind: DirectoryHit.Kind, id: String) {
        self.teknotes = teknotes
        self.kind = kind
        self.id = id
        load()
    }

    func load() {
        Task { @MainActor in
            do {
                // A local edit not sent yet must be part of the history shown.
                try await teknotes.sync.syncNow()
                revisions = try await teknotes.community.history(kind: kind, id: id)
            } catch {
                message = error.localizedDescription
            }
            loading = false
        }
    }

    func restore(_ revision: Revision) {
        Task { @MainActor in
            do {
                try await teknotes.community.restore(kind: kind, id: id, revision: revision)
                message = "Version rétablie."
                load()
            } catch {
                message = error.localizedDescription
            }
        }
    }

    func block(_ authorId: String) {
        Task { @MainActor in
            do {
                try await teknotes.community.block(userId: authorId)
                load()
            } catch {
                message = error.localizedDescription
            }
        }
    }

    var myPseudo: String? { (teknotes.account.state.value as? AccountStateSignedIn)?.pseudo }
}

struct HistoryView: View {
    @State var model: HistoryModel

    var body: some View {
        List {
            if let message = model.message {
                Text(message).foregroundStyle(Theme.pink)
            }
            if model.loading {
                Text("Un instant…").foregroundStyle(Theme.mute)
            } else if model.revisions.isEmpty && model.message == nil {
                Text("Pas d'historique à afficher.").foregroundStyle(Theme.mute)
            }
            ForEach(Array(model.revisions.enumerated()), id: \.element.id) { index, revision in
                VStack(alignment: .leading, spacing: 4) {
                    FlyerLabel(text: NightDate.format(revision.date))
                    Text(revision.name).font(Theme.body(17, weight: 700))
                    Text(revision.changes.isEmpty ? "Création de la fiche" : "Modifié : " + revision.changes.joined(separator: ", "))
                        .font(Theme.body(15))
                    Text(revision.authorPseudo.map { "par \($0)" } ?? "auteur inconnu")
                        .font(Theme.body(13))
                        .foregroundStyle(Theme.mute)
                    HStack(spacing: 16) {
                        if index > 0 {
                            Button("Rétablir cette version") { model.restore(revision) }.buttonStyle(.borderless)
                        }
                        if let author = revision.authorId, revision.authorPseudo != model.myPseudo {
                            Button("Bloquer") { model.block(author) }.buttonStyle(.borderless)
                        }
                    }
                }
            }
        }
        .flyerBackground()
        .navigationTitle("Historique")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct BlockedView: View {
    let teknotes: Teknotes
    @State private var users: [BlockedUser] = []
    @State private var message: String?

    var body: some View {
        List {
            if let message {
                Text(message).foregroundStyle(Theme.pink)
            }
            if users.isEmpty {
                Text("Personne pour l'instant.").foregroundStyle(Theme.mute)
            }
            ForEach(users, id: \.id) { user in
                HStack {
                    Text(user.pseudo).font(Theme.body(17, weight: 600))
                    Spacer()
                    Button("Débloquer") {
                        Task { @MainActor in
                            do {
                                try await teknotes.community.unblock(userId: user.id)
                                await load()
                            } catch {
                                message = error.localizedDescription
                            }
                        }
                    }
                    .buttonStyle(.borderless)
                }
            }
        }
        .flyerBackground()
        .navigationTitle("Personnes bloquées")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    @MainActor
    private func load() async {
        do {
            users = try await teknotes.community.blockedUsers()
        } catch {
            message = error.localizedDescription
        }
    }
}

struct LegalView: View {
    let privacy: Bool

    var body: some View {
        List {
            ForEach(privacy ? Legal.shared.privacy : Legal.shared.terms, id: \.title) { section in
                VStack(alignment: .leading, spacing: 4) {
                    Text(section.title).font(Theme.body(17, weight: 700))
                    Text(section.text).font(Theme.body(15))
                }
            }
        }
        .flyerBackground()
        .navigationTitle(privacy ? "Confidentialité" : "Conditions d'utilisation")
        .navigationBarTitleDisplayMode(.inline)
    }
}
