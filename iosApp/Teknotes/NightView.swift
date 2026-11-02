import Observation
import Shared
import SwiftUI

@Observable
final class NightModel {
    let teknotes: Teknotes
    let nightId: String
    private(set) var night: Night?
    private(set) var entries: [NightEntry] = []
    var query = "" {
        didSet { results = query.trimmingCharacters(in: .whitespaces).isEmpty ? [] : Array(teknotes.directory.search(query: query).prefix(8)) }
    }
    private(set) var results: [DirectoryHit] = []
    @ObservationIgnored private var watchers: [Watcher] = []

    init(teknotes: Teknotes, nightId: String) {
        self.teknotes = teknotes
        self.nightId = nightId
        watchers.append(teknotes.notebook.watchNights { [weak self] nights in
            self?.night = nights.first { $0.id == nightId }
        })
        watchers.append(teknotes.notebook.watchEntries(nightId: nightId) { [weak self] entries in self?.entries = entries })
    }

    deinit { watchers.forEach { $0.cancel() } }

    /// Second tap of "add in 2 taps".
    func add(_ hit: DirectoryHit) {
        _ = teknotes.notebook.addEntry(nightId: nightId, hit: hit)
        query = ""
    }

    /// Nothing matched: creates the soundsystem in the directory, then adds it.
    func createAndAdd() {
        let soundsystem = teknotes.directory.createSoundsystem(name: query, aliases: [], department: nil, styles: [], links: [])
        add(DirectoryHit(id: soundsystem.id, kind: .soundsystem, name: soundsystem.name, matchedAlias: nil, score: 100))
    }

    func rate(_ entry: NightEntry, _ rating: Int32) {
        let same = entry.rating?.int32Value == rating
        teknotes.notebook.rateEntry(entryId: entry.id, rating: same ? nil : KotlinInt(int: rating), comment: entry.comment)
    }

    func remove(_ entry: NightEntry) { teknotes.notebook.removeEntry(entryId: entry.id) }

    func setDepartment(_ code: String?) {
        guard let night else { return }
        teknotes.notebook.updateNight(id: night.id, department: code, title: night.title, comment: night.comment, shared: night.shared)
    }

    func delete() { teknotes.notebook.deleteNight(id: nightId) }
}

struct NightView: View {
    @State var model: NightModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            Section {
                Menu {
                    ForEach(Departments.shared.all, id: \.code) { department in
                        Button(department.label) { model.setDepartment(department.code) }
                    }
                } label: {
                    Label(
                        Departments.shared.byCode(code: model.night?.department)?.label ?? "Choisir le département",
                        systemImage: "mappin.and.ellipse"
                    )
                }
                TextField("Soundsystem ou artiste entendu", text: $model.query)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
            }

            if !model.query.trimmingCharacters(in: .whitespaces).isEmpty {
                Section {
                    ForEach(model.results, id: \.id) { hit in
                        Button { model.add(hit) } label: { HitRow(hit: hit) }
                    }
                    Button {
                        model.createAndAdd()
                    } label: {
                        Label("Créer « \(model.query.trimmingCharacters(in: .whitespaces)) »", systemImage: "plus")
                    }
                }
            }

            Section {
                if model.entries.isEmpty {
                    Text("Cherche un nom au-dessus, puis touche-le pour l'ajouter.")
                        .font(Theme.body(15))
                        .foregroundStyle(Theme.mute)
                }
                ForEach(model.entries, id: \.id) { entry in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(entry.label).font(Theme.body(17, weight: 700))
                        // Big touch targets: rated at night, often with one hand.
                        HStack(spacing: 4) {
                            ForEach(1...5, id: \.self) { star in
                                Image(systemName: (entry.rating?.int32Value ?? 0) >= star ? "circle.fill" : "circle")
                                    .font(.system(size: 22))
                                    .foregroundStyle(Theme.pink)
                                    .frame(width: 40, height: 40)
                                    .contentShape(Rectangle())
                                    .onTapGesture { model.rate(entry, Int32(star)) }
                            }
                        }
                    }
                    .swipeActions { Button("Retirer", role: .destructive) { model.remove(entry) } }
                }
            } header: {
                FlyerLabel(text: "Entendu cette nuit")
            }
        }
        .navigationTitle(model.night.map { NightDate.format($0.date) } ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Button("Supprimer", role: .destructive) {
                model.delete()
                dismiss()
            }
        }
    }
}

struct HitRow: View {
    let hit: DirectoryHit

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            FlyerLabel(text: hit.kind == .soundsystem ? "Soundsystem" : "Artiste")
            Text(hit.name).font(Theme.body(17, weight: 600)).foregroundStyle(.primary)
            if let alias = hit.matchedAlias {
                Text("aussi appelé \(alias)").font(Theme.body(13)).foregroundStyle(Theme.mute)
            }
        }
    }
}
