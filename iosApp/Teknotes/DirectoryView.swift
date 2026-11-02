import Observation
import Shared
import SwiftUI

@Observable
final class DirectoryModel {
    let teknotes: Teknotes
    private(set) var soundsystems: [Soundsystem] = []
    var query = "" {
        didSet { results = teknotes.directory.search(query: query) }
    }
    private(set) var results: [DirectoryHit] = []
    @ObservationIgnored private var watcher: Watcher?

    init(teknotes: Teknotes) {
        self.teknotes = teknotes
        watcher = teknotes.directory.watchSoundsystems { [weak self] items in self?.soundsystems = items }
    }

    deinit { watcher?.cancel() }
}

struct DirectoryView: View {
    @State var model: DirectoryModel

    var body: some View {
        NavigationStack {
            List {
                ScreenHeader(label: "Annuaire", title: "Soundsystems")
                    .listRowBackground(Color.clear)
                if model.query.isEmpty {
                    ForEach(model.soundsystems, id: \.id) { soundsystem in
                        VStack(alignment: .leading, spacing: 4) {
                            FlyerLabel(text: Departments.shared.byCode(code: soundsystem.department)?.label ?? "Origine inconnue")
                            Text(soundsystem.name).font(Theme.body(17, weight: 700))
                            Text(soundsystem.styles.map(\.label).joined(separator: " · "))
                                .font(Theme.body(14))
                                .foregroundStyle(Theme.mute)
                        }
                        .listRowBackground(Color.clear)
                    }
                } else {
                    ForEach(model.results, id: \.id) { hit in
                        HitRow(hit: hit).listRowBackground(Color.clear)
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .searchable(text: $model.query, prompt: "Chercher, même avec une faute")
        }
    }
}

struct ProfileView: View {
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                ScreenHeader(label: "Profil", title: "Sans compte")
                Text("Ton carnet marche sans compte et reste sur ce téléphone. Un compte servira à contribuer à l'annuaire partagé : il arrive avec la synchronisation.")
                    .font(Theme.body(16))
                    .foregroundStyle(Theme.mute)
                Spacer()
            }
            .padding(20)
            .background(Theme.background)
        }
    }
}
