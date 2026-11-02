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
    private(set) var notes: [MyNote] = []
    private var mine: [MyDirectoryEntry] = []
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
        watchers.append(teknotes.notebook.watchNotesOn(kind: kind, id: id) { [weak self] notes in self?.notes = notes })
        watchers.append(teknotes.notebook.watchMyDirectory { [weak self] items in self?.mine = items })
        watchers.append(teknotes.account.watchState { [weak self] state in
            self?.signedIn = state is AccountStateSignedIn
            self?.refresh()
        })
    }

    deinit { watchers.forEach { $0.cancel() } }

    var isSoundsystem: Bool { kind == .soundsystem }
    var name: String? { isSoundsystem ? soundsystem?.name : artist?.name }
    var styles: [Style] { isSoundsystem ? soundsystem?.styles ?? [] : artist?.styles ?? [] }
    var aliases: [String] { isSoundsystem ? soundsystem?.aliases ?? [] : artist?.aliases ?? [] }
    var links: [String] { isSoundsystem ? soundsystem?.links ?? [] : artist?.links ?? [] }

    /// Its place in the user's ranking, from 1.
    var rank: Int? {
        teknotes.notebook.ranked(items: mine, kind: nil, query: "").firstIndex { $0.id == id }.map { $0 + 1 }
    }

    /// Average of what the user gave it, and how many notes that's based on.
    var average: (value: Double, count: Int)? {
        let ratings = notes.compactMap { $0.entry.rating?.doubleValue }
        guard !ratings.isEmpty else { return nil }
        return (ratings.reduce(0, +) / Double(ratings.count), ratings.count)
    }

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

/// Two faces of an entry. Personal: what the user noted, from their ranking. Public: what
/// everyone shares, with no rating at all.
struct FicheView: View {
    @State private var model: FicheModel
    let personal: Bool
    @State private var editing = false
    @State private var merging = false
    @State private var reporting = false
    @State private var authenticating = false
    @Environment(AccountModel.self) private var account

    init(teknotes: Teknotes, kind: DirectoryHit.Kind, id: String, personal: Bool = false) {
        _model = State(initialValue: FicheModel(teknotes: teknotes, kind: kind, id: id))
        self.personal = personal
    }

    var body: some View {
        Group {
            if let name = model.name {
                personal ? AnyView(personalPage(name: name)) : AnyView(publicPage(name: name))
            } else {
                // Gone from the directory (hidden after reports, merged).
                Page { Note(text: "Cette fiche n'est plus dans l'annuaire.").padding(.top, 16) }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $editing) {
            CreateView(teknotes: model.teknotes, kind: model.kind, name: "", mode: .edit(id: model.id)) { _ in }
        }
        .sheet(isPresented: $merging) { MergeSheet(model: model) }
        .sheet(isPresented: $authenticating) { AuthSheet(mode: .signUp) }
        .confirmationDialog("Quel est le problème ?", isPresented: $reporting, titleVisibility: .visible) {
            ForEach(ReportReason.entries, id: \.self) { reason in
                Button(reason.label) { model.report(reason) }
            }
        } message: {
            Text("Trois signalements de personnes différentes masquent la fiche en attendant une vérification.")
        }
    }

    @ViewBuilder
    private func header(_ name: String) -> some View {
        Text(model.isSoundsystem ? "Soundsystem" : "Artiste").font(Theme.text(14, weight: 600)).foregroundStyle(Theme.mute)
        BigTitle(text: name).padding(.top, 2)
    }

    private func personalPage(name: String) -> some View {
        Page {
            header(name)
            if let average = model.average {
                ScoreText(value: average.value, size: 72).tracking(-3).padding(.top, 16)
            } else {
                ScoreText(value: nil, size: 72).padding(.top, 16)
            }
            Note(text: rankLine)

            SectionHead(text: "Mes notes")
            if model.notes.isEmpty {
                Note(text: "Pas encore dans ton carnet.").padding(.top, 4)
            }
            ForEach(model.notes, id: \.entry.id) { note in
                NavigationLink {
                    NightView(model: NightModel(teknotes: model.teknotes, nightId: note.entry.nightId))
                } label: {
                    NoteRow(note: note)
                }
                .buttonStyle(.plain)
            }

            NavigationLink {
                FicheView(teknotes: model.teknotes, kind: model.kind, id: model.id, personal: false)
            } label: {
                RowLabel(title: "Voir la fiche publique")
            }
            .buttonStyle(.plain)
            .padding(.top, 20)
        }
    }

    private var rankLine: String {
        var parts: [String] = []
        if let rank = model.rank { parts.append(rank == 1 ? "1er de ton classement" : "\(rank)e de ton classement") }
        if let count = model.average?.count { parts.append(count == 1 ? "1 note" : "\(count) notes") }
        return parts.joined(separator: " · ")
    }

    private func publicPage(name: String) -> some View {
        Page {
            header(name)
            if !model.aliases.isEmpty {
                Note(text: "aussi appelé " + model.aliases.joined(separator: ", ")).padding(.top, 4)
            }
            if !model.styles.isEmpty {
                TagFlow {
                    ForEach(model.styles, id: \.id) { Tag(text: $0.label) }
                }
                .padding(.top, 12)
            }

            if !model.notes.isEmpty {
                NavigationLink {
                    FicheView(teknotes: model.teknotes, kind: model.kind, id: model.id, personal: true)
                } label: {
                    RowLabel(title: "Voir mes notes", detail: model.notes.count == 1 ? "1 note" : "\(model.notes.count) notes")
                }
                .buttonStyle(.plain)
                .padding(.top, 12)
            }

            if !model.related.isEmpty {
                SectionHead(text: model.isSoundsystem ? "Artistes" : "Joue sur")
                ForEach(model.related, id: \.id) { item in
                    NavigationLink {
                        FicheView(teknotes: model.teknotes, kind: model.isSoundsystem ? .artist : .soundsystem, id: item.id)
                    } label: {
                        RowLabel(title: item.name)
                    }
                    .buttonStyle(.plain)
                }
            }

            if !model.links.isEmpty {
                SectionHead(text: "Liens")
                ForEach(model.links, id: \.self) { link in
                    if let url = URL(string: link), url.scheme != nil {
                        Link(destination: url) { RowLabel(title: link, chevron: false) }
                    } else {
                        RowLabel(title: link, chevron: false)
                    }
                }
            }

            if !model.proposals.isEmpty {
                SectionHead(text: "Fusions proposées")
                ForEach(model.proposals, id: \.id) { proposal in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(proposal.keepsThisEntry ? "« \(proposal.otherName) » fondu ici" : "Fondu dans « \(proposal.otherName) »")
                                .font(Theme.text(15, weight: 600))
                            Note(text: "\(proposal.approvals) accord\(proposal.approvals > 1 ? "s" : "") sur \(proposal.needed)")
                        }
                        Spacer(minLength: 8)
                        if !proposal.approvedByMe {
                            TextAction(title: "Approuver") { model.approve(proposal) }.disabled(model.busy)
                        }
                    }
                    .padding(.vertical, 8)
                    Hairline()
                }
            }

            if !model.sightings.isEmpty {
                SectionHead(text: "Nuits partagées par d'autres")
                ForEach(model.sightings, id: \.self) { sighting in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(NightDate.format(sighting.date)).font(Theme.text(15, weight: 600))
                            Note(text: [sighting.pseudo, Departments.shared.byCode(code: sighting.department)?.name].compactMap { $0 }.joined(separator: " · "))
                        }
                        Spacer(minLength: 8)
                        TextAction(title: "Bloquer", color: Theme.mute) { model.block(sighting) }.disabled(model.busy)
                    }
                    .padding(.vertical, 8)
                    Hairline()
                }
            }

            if let message = model.message {
                Text(message).font(Theme.text(14, weight: 600)).padding(.top, 16)
            }

            SectionHead(text: "Contribuer")
            if model.signedIn {
                RowButton(title: "Modifier la fiche") { editing = true }
                NavigationLink {
                    HistoryView(model: HistoryModel(teknotes: model.teknotes, kind: model.kind, id: model.id))
                } label: {
                    RowLabel(title: "Voir l'historique")
                }
                .buttonStyle(.plain)
                RowButton(title: "Signaler un doublon") { merging = true }
                RowButton(title: "Signaler un problème") { reporting = true }
            } else {
                Note(text: "Tout le monde peut compléter cette fiche, avec un compte.").padding(.top, 4)
                if !account.isUnavailable {
                    TextAction(title: "Créer un compte ou se connecter") {
                        account.clearMessage()
                        authenticating = true
                    }
                }
            }

            if let department = Departments.shared.byCode(code: model.soundsystem?.department)?.name {
                Note(text: "Origine : \(department)").padding(.top, 20)
            }
        }
    }
}

private struct NoteRow: View {
    let note: MyNote

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(NightDate.format(note.nightDate) + (note.nightTitle.isEmpty ? "" : " · \(note.nightTitle)"))
                        .font(Theme.text(13))
                        .foregroundStyle(Theme.mute)
                    SetName(artist: note.entry.artistName, soundsystem: note.entry.soundsystemName, fallback: note.entry.label, size: 16)
                        .multilineTextAlignment(.leading)
                    if !note.entry.comment.isEmpty {
                        Text(note.entry.comment).font(Theme.text(15)).multilineTextAlignment(.leading)
                    }
                }
                Spacer(minLength: 0)
                ScoreText(value: note.entry.rating?.doubleValue)
            }
            .padding(.vertical, 12)
            .contentShape(Rectangle())
            Hairline()
        }
    }
}

private struct MergeSheet: View {
    @Bindable var model: FicheModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Page {
                SearchLine(prompt: "Chercher la fiche", text: $model.mergeQuery).padding(.top, 8).padding(.bottom, 8)
                ForEach(model.mergeResults, id: \.id) { hit in
                    Button {
                        model.proposeMerge(absorbed: hit)
                        dismiss()
                    } label: {
                        HitRow(hit: hit)
                    }
                    .buttonStyle(.plain)
                }
                Note(text: "La fiche choisie sera fondue dans « \(model.name ?? "") » quand trois personnes seront d'accord.").padding(.top, 12)
            }
            .navigationTitle("Doublon de quelle fiche ?")
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
        Page {
            if let message = model.message {
                Text(message).font(Theme.text(14, weight: 600)).foregroundStyle(Theme.accent).padding(.top, 8)
            }
            if model.loading {
                Note(text: "Un instant…").padding(.top, 8)
            } else if model.revisions.isEmpty && model.message == nil {
                Note(text: "Pas d'historique à afficher.").padding(.top, 8)
            }
            ForEach(Array(model.revisions.enumerated()), id: \.element.id) { index, revision in
                VStack(alignment: .leading, spacing: 4) {
                    Text(NightDate.format(revision.date) + " · " + (revision.authorPseudo ?? "auteur inconnu"))
                        .font(Theme.text(13))
                        .foregroundStyle(Theme.mute)
                    Text(revision.name).font(Theme.text(17, weight: 700))
                    Text(revision.changes.isEmpty ? "Création de la fiche" : "Modifié : " + revision.changes.joined(separator: ", "))
                        .font(Theme.text(15))
                    HStack(spacing: 16) {
                        if index > 0 {
                            TextAction(title: "Rétablir cette version") { model.restore(revision) }
                        }
                        if let author = revision.authorId, revision.authorPseudo != model.myPseudo {
                            TextAction(title: "Bloquer", color: Theme.mute) { model.block(author) }
                        }
                    }
                }
                .padding(.vertical, 12)
                Hairline()
            }
        }
        .navigationTitle("Historique")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct BlockedView: View {
    let teknotes: Teknotes
    @State private var users: [BlockedUser] = []
    @State private var message: String?

    var body: some View {
        Page {
            if let message {
                Text(message).font(Theme.text(14, weight: 600)).foregroundStyle(Theme.accent).padding(.top, 8)
            }
            if users.isEmpty {
                Note(text: "Personne pour l'instant.").padding(.top, 8)
            }
            ForEach(users, id: \.id) { user in
                HStack {
                    Text(user.pseudo).font(Theme.text(16, weight: 600))
                    Spacer()
                    TextAction(title: "Débloquer") {
                        Task { @MainActor in
                            do {
                                try await teknotes.community.unblock(userId: user.id)
                                await load()
                            } catch {
                                message = error.localizedDescription
                            }
                        }
                    }
                }
                Hairline()
            }
        }
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
        Page {
            ForEach(privacy ? Legal.shared.privacy : Legal.shared.terms, id: \.title) { section in
                VStack(alignment: .leading, spacing: 4) {
                    Text(section.title).font(Theme.text(16, weight: 700))
                    Text(section.text).font(Theme.text(15)).fixedSize(horizontal: false, vertical: true)
                }
                .padding(.vertical, 12)
                Hairline()
            }
        }
        .navigationTitle(privacy ? "Confidentialité" : "Conditions d'utilisation")
        .navigationBarTitleDisplayMode(.inline)
    }
}
