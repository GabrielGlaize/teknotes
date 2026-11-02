import Observation
import Shared
import SwiftUI

@Observable
final class DirectoryModel {
    enum Scope: String, CaseIterable {
        case mine = "Mon classement"
        case open = "Public"
    }

    enum Filter: String, CaseIterable {
        case all = "Tout"
        case artists = "Artistes"
        case soundsystems = "Soundsystems"
    }

    let teknotes: Teknotes
    private(set) var allSoundsystems: [Soundsystem] = []
    private(set) var allArtists: [Artist] = []
    private(set) var links: DirectoryLinks
    private var mine: [MyDirectoryEntry] = []
    var scope = Scope.mine
    var filter = Filter.all { didSet { refreshResults() } }
    var style: Set<Style> = []
    var query = "" { didSet { refreshResults() } }
    private(set) var results: [DirectoryHit] = []
    @ObservationIgnored private var scopeChosen = false
    @ObservationIgnored private var watchers: [Watcher] = []

    init(teknotes: Teknotes) {
        self.teknotes = teknotes
        links = teknotes.directory.links()
        watchers.append(teknotes.directory.watchSoundsystems { [weak self] items in
            self?.allSoundsystems = items
            self?.refreshLinks()
        })
        watchers.append(teknotes.directory.watchArtists { [weak self] items in
            self?.allArtists = items
            self?.refreshLinks()
        })
        watchers.append(teknotes.notebook.watchMyDirectory { [weak self] items in self?.mine = items })
    }

    deinit { watchers.forEach { $0.cancel() } }

    private func refreshLinks() { links = teknotes.directory.links() }

    private var kind: DirectoryHit.Kind? {
        switch filter {
        case .all: return nil
        case .artists: return .artist
        case .soundsystems: return .soundsystem
        }
    }

    /// What a new entry is, for the + button.
    var newKind: DirectoryHit.Kind { filter == .artists ? .artist : .soundsystem }

    var hasNotes: Bool { !mine.isEmpty }

    var ranking: [MyDirectoryEntry] { teknotes.notebook.ranked(items: mine, kind: kind, query: query) }

    var soundsystems: [Soundsystem] {
        guard filter != .artists else { return [] }
        guard let style = style.first else { return allSoundsystems }
        return allSoundsystems.filter { $0.styles.contains(style) }
    }

    var artists: [Artist] {
        guard filter != .soundsystems else { return [] }
        guard let style = style.first else { return allArtists }
        return allArtists.filter { $0.styles.contains(style) }
    }

    private func refreshResults() {
        results = teknotes.directory.search(query: query).filter { kind == nil || $0.kind == kind }
    }
}

struct DirectoryView: View {
    @Bindable var model: DirectoryModel
    let onFirstNote: () -> Void
    @State private var creating = false
    @State private var filtering = false

    var body: some View {
        NavigationStack {
            Page {
                HStack(alignment: .center) {
                    BigTitle(text: "Annuaire")
                    Spacer()
                    Button {
                        creating = true
                    } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 20, weight: .bold))
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(model.newKind == .soundsystem ? "Ajouter un soundsystem" : "Ajouter un artiste")
                }
                .padding(.top, 8)
                .padding(.bottom, 8)

                Segments(items: DirectoryModel.Scope.allCases, selection: $model.scope, label: { $0.rawValue })
                SearchLine(prompt: "Chercher, même avec une faute", text: $model.query)
                    .clipShape(RoundedRectangle(cornerRadius: 9))
                    .padding(.top, 10)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(DirectoryModel.Filter.allCases, id: \.self) { filter in
                            Chip(text: filter.rawValue, on: model.filter == filter) { model.filter = filter }
                        }
                        if model.scope == .open {
                            Chip(text: model.style.first.map { "\($0.label) ×" } ?? "Style ▾", on: !model.style.isEmpty) { filtering = true }
                        }
                    }
                }
                .padding(.vertical, 4)

                switch model.scope {
                case .mine: ranking
                case .open: publicList
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .sheet(isPresented: $creating) {
                CreateView(teknotes: model.teknotes, kind: model.newKind, name: model.query, mode: .create) { _ in }
            }
            .sheet(isPresented: $filtering) {
                StylePicker(directory: model.teknotes.directory, filtering: true, selected: $model.style)
            }
        }
    }

    @ViewBuilder
    private var ranking: some View {
        let entries = model.ranking
        if !model.hasNotes {
            TipCard(text: "Ton classement se remplit avec tes notes. Commence par un soundsystem ou un artiste entendu.", action: onFirstNote)
                .padding(.top, 6)
        } else if entries.isEmpty {
            Note(text: "Rien de tel dans ton classement. Regarde dans Public.").padding(.top, 16)
        }
        ForEach(Array(entries.enumerated()), id: \.element.id) { index, entry in
            NavigationLink {
                FicheView(teknotes: model.teknotes, kind: entry.kind, id: entry.id, personal: true)
            } label: {
                RankRow(rank: index + 1, entry: entry)
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private var publicList: some View {
        if !model.query.isEmpty {
            if model.results.isEmpty {
                Note(text: "Pas de fiche à ce nom. Le + en haut permet de la créer.").padding(.top, 16)
            }
            ForEach(model.results, id: \.id) { hit in
                NavigationLink { FicheView(teknotes: model.teknotes, kind: hit.kind, id: hit.id, personal: false) } label: { HitRow(hit: hit) }
                    .buttonStyle(.plain)
            }
        } else {
            ForEach(model.soundsystems, id: \.id) { soundsystem in
                NavigationLink {
                    FicheView(teknotes: model.teknotes, kind: .soundsystem, id: soundsystem.id, personal: false)
                } label: {
                    PublicRow(
                        artist: nil,
                        soundsystem: soundsystem.name,
                        details: [
                            model.links.artistsOfSoundsystem[soundsystem.id].map { "avec " + $0.joined(separator: ", ") },
                            soundsystem.styles.isEmpty ? nil : soundsystem.styles.map(\.label).joined(separator: ", "),
                        ].compactMap { $0 }.joined(separator: " · ")
                    )
                }
                .buttonStyle(.plain)
            }
            ForEach(model.artists, id: \.id) { artist in
                NavigationLink {
                    FicheView(teknotes: model.teknotes, kind: .artist, id: artist.id, personal: false)
                } label: {
                    PublicRow(
                        artist: artist.name,
                        soundsystem: model.links.soundsystemsOfArtist[artist.id]?.joined(separator: ", "),
                        details: artist.styles.map(\.label).joined(separator: ", ")
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private struct PublicRow: View {
    let artist: String?
    let soundsystem: String?
    let details: String

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    SetName(artist: artist, soundsystem: soundsystem).multilineTextAlignment(.leading)
                    if !details.isEmpty {
                        Text(details).font(Theme.text(13)).foregroundStyle(Theme.mute).lineLimit(2).multilineTextAlignment(.leading)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.vertical, 12)
            .contentShape(Rectangle())
            Hairline()
        }
    }
}

/// A line of the ranking: the rank and the score are the design.
private struct RankRow: View {
    let rank: Int
    let entry: MyDirectoryEntry

    private var details: String {
        let times = entry.nights == 1 ? "1 nuit" : "\(entry.nights) nuits"
        return "\(entry.kind == .artist ? "Artiste" : "Soundsystem") · \(times)"
    }

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Theme.ink).frame(height: 1)
            HStack(spacing: 12) {
                Text("\(rank)")
                    .font(Theme.number(rank > 99 ? 28 : 42))
                    .tracking(-2)
                    .frame(width: 52, alignment: .leading)
                VStack(alignment: .leading, spacing: 2) {
                    Text(entry.name).font(Theme.text(16, weight: 700)).lineLimit(1)
                    Text(details).font(Theme.text(12)).foregroundStyle(Theme.mute)
                }
                Spacer(minLength: 0)
                ScoreText(value: entry.average?.doubleValue)
            }
            .padding(.vertical, 7)
            .contentShape(Rectangle())
        }
    }
}
