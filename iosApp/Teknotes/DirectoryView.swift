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
                        NavigationLink { FicheView(teknotes: model.teknotes, kind: hit.kind, id: hit.id) } label: { HitRow(hit: hit) }
                            .listRowBackground(Color.clear)
                    }
                } else if model.tab == .soundsystems {
                    ForEach(model.soundsystems, id: \.id) { soundsystem in
                        NavigationLink {
                            FicheView(teknotes: model.teknotes, kind: .soundsystem, id: soundsystem.id)
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
                            FicheView(teknotes: model.teknotes, kind: .artist, id: artist.id)
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
                CreateView(teknotes: model.teknotes, kind: model.kind, name: model.query, mode: .create) { _ in }
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
