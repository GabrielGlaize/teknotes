import Observation
import Shared
import SwiftUI

@Observable
final class DirectoryModel {
    enum Tab: String, CaseIterable, Identifiable {
        case soundsystems = "Soundsystems"
        case artists = "Artistes"
        var id: String { rawValue }
    }

    let teknotes: Teknotes
    private(set) var allSoundsystems: [Soundsystem] = []
    private(set) var allArtists: [Artist] = []
    var tab = Tab.soundsystems { didSet { refreshResults() } }
    var style: Style?
    var query = "" { didSet { refreshResults() } }
    private(set) var results: [DirectoryHit] = []
    @ObservationIgnored private var watchers: [Watcher] = []

    init(teknotes: Teknotes) {
        self.teknotes = teknotes
        watchers.append(teknotes.directory.watchSoundsystems { [weak self] items in self?.allSoundsystems = items })
        watchers.append(teknotes.directory.watchArtists { [weak self] items in self?.allArtists = items })
    }

    deinit { watchers.forEach { $0.cancel() } }

    var kind: DirectoryHit.Kind { tab == .soundsystems ? .soundsystem : .artist }

    var soundsystems: [Soundsystem] { allSoundsystems.filter { style == nil || $0.styles.contains(style!) } }

    var artists: [Artist] { allArtists.filter { style == nil || $0.styles.contains(style!) } }

    private func refreshResults() {
        results = teknotes.directory.search(query: query).filter { $0.kind == kind }
    }
}

struct DirectoryView: View {
    @Bindable var model: DirectoryModel
    @State private var creating = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ScreenHeader(label: "Annuaire", title: model.tab.rawValue)
                    Picker("Afficher", selection: $model.tab) {
                        ForEach(DirectoryModel.Tab.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Style.entries, id: \.id) { style in
                                StyleChip(style: style, selected: model.style == style) {
                                    model.style = model.style == style ? nil : style
                                }
                            }
                        }
                    }
                    Button {
                        creating = true
                    } label: {
                        Label(model.tab == .soundsystems ? "Ajouter un soundsystem" : "Ajouter un artiste", systemImage: "plus")
                    }
                }
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)

                if !model.query.isEmpty {
                    ForEach(model.results, id: \.id) { hit in
                        NavigationLink { DirectoryDetail(teknotes: model.teknotes, hit: hit) } label: { HitRow(hit: hit) }
                            .listRowBackground(Color.clear)
                    }
                } else if model.tab == .soundsystems {
                    ForEach(model.soundsystems, id: \.id) { soundsystem in
                        NavigationLink {
                            SoundsystemDetail(teknotes: model.teknotes, id: soundsystem.id)
                        } label: {
                            DirectoryRow(
                                label: Departments.shared.byCode(code: soundsystem.department)?.label ?? "Origine inconnue",
                                name: soundsystem.name,
                                styles: soundsystem.styles
                            )
                        }
                        .listRowBackground(Color.clear)
                    }
                } else {
                    ForEach(model.artists, id: \.id) { artist in
                        NavigationLink {
                            ArtistDetail(teknotes: model.teknotes, id: artist.id)
                        } label: {
                            DirectoryRow(label: "Artiste", name: artist.name, styles: artist.styles)
                        }
                        .listRowBackground(Color.clear)
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .searchable(text: $model.query, prompt: "Chercher, même avec une faute")
            .sheet(isPresented: $creating) {
                CreateView(teknotes: model.teknotes, kind: model.kind, name: model.query, forNight: false) { _ in }
            }
        }
    }
}

private struct DirectoryRow: View {
    let label: String
    let name: String
    let styles: [Style]

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            FlyerLabel(text: label)
            Text(name).font(Theme.body(17, weight: 700))
            Text(styles.map(\.label).joined(separator: " · "))
                .font(Theme.body(14))
                .foregroundStyle(Theme.mute)
        }
    }
}

struct StyleChip: View {
    let style: Style
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(style.label)
                .font(Theme.body(14, weight: 600))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .foregroundStyle(selected ? Color.white : Color.primary)
                .background(selected ? Theme.pink : Color.clear, in: Capsule())
                .overlay(Capsule().stroke(Theme.mute.opacity(0.4)))
        }
        .buttonStyle(.plain)
    }
}

struct DirectoryDetail: View {
    let teknotes: Teknotes
    let hit: DirectoryHit

    var body: some View {
        if hit.kind == .soundsystem {
            SoundsystemDetail(teknotes: teknotes, id: hit.id)
        } else {
            ArtistDetail(teknotes: teknotes, id: hit.id)
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

struct SoundsystemDetail: View {
    let teknotes: Teknotes
    let id: String

    var body: some View {
        if let soundsystem = teknotes.directory.soundsystem(id: id) {
            List {
                Section {
                    ScreenHeader(label: "Soundsystem", title: soundsystem.name)
                    Fact(label: "Origine", value: Departments.shared.byCode(code: soundsystem.department)?.label ?? "Inconnue")
                    Fact(label: "Styles", value: soundsystem.styles.map(\.label).joined(separator: " · "))
                    Fact(label: "Aussi appelé", value: soundsystem.aliases.joined(separator: ", "))
                    Fact(label: "Liens", value: soundsystem.links.joined(separator: "\n"))
                }
                let members = teknotes.directory.membersOf(soundsystemId: id)
                if !members.isEmpty {
                    Section {
                        ForEach(members, id: \.id) { artist in
                            NavigationLink(artist.name) { ArtistDetail(teknotes: teknotes, id: artist.id) }
                        }
                    } header: {
                        FlyerLabel(text: "Membres")
                    }
                }
            }
            .flyerBackground()
        } else {
            Text("Cette fiche n'est plus dans l'annuaire.").foregroundStyle(Theme.mute)
        }
    }
}

struct ArtistDetail: View {
    let teknotes: Teknotes
    let id: String

    var body: some View {
        if let artist = teknotes.directory.artist(id: id) {
            List {
                Section {
                    ScreenHeader(label: "Artiste", title: artist.name)
                    Fact(label: "Styles", value: artist.styles.map(\.label).joined(separator: " · "))
                    Fact(label: "Aussi appelé", value: artist.aliases.joined(separator: ", "))
                    Fact(label: "Liens", value: artist.links.joined(separator: "\n"))
                }
                let soundsystems = teknotes.directory.soundsystemsOf(artistId: id)
                if !soundsystems.isEmpty {
                    Section {
                        ForEach(soundsystems, id: \.id) { soundsystem in
                            NavigationLink(soundsystem.name) { SoundsystemDetail(teknotes: teknotes, id: soundsystem.id) }
                        }
                    } header: {
                        FlyerLabel(text: "Soundsystems")
                    }
                }
            }
            .flyerBackground()
        } else {
            Text("Cette fiche n'est plus dans l'annuaire.").foregroundStyle(Theme.mute)
        }
    }
}
