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
        night = teknotes.notebook.night(id: nightId)
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

    func rate(_ entry: NightEntry, _ rating: Int32) {
        let same = entry.rating?.int32Value == rating
        teknotes.notebook.updateEntry(entryId: entry.id, rating: same ? nil : KotlinInt(int: rating), comment: entry.comment)
    }

    func comment(_ entry: NightEntry, _ text: String) {
        teknotes.notebook.updateEntry(entryId: entry.id, rating: entry.rating, comment: text)
    }

    func remove(_ entry: NightEntry) { teknotes.notebook.removeEntry(entryId: entry.id) }

    func edit(department: String?? = nil, title: String? = nil, comment: String? = nil, shared: Bool? = nil) {
        guard let night else { return }
        teknotes.notebook.updateNight(
            id: night.id,
            department: department ?? night.department,
            title: title ?? night.title,
            comment: comment ?? night.comment,
            shared: shared ?? night.shared
        )
    }

    func delete() { teknotes.notebook.deleteNight(id: nightId) }
}

struct NightView: View {
    @State var model: NightModel
    @State private var title = ""
    @State private var memories = ""
    @State private var creating: String?
    @State private var confirmDelete = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            Section {
                Menu {
                    ForEach(Departments.shared.all, id: \.code) { department in
                        Button(department.label) { model.edit(department: .some(department.code)) }
                    }
                } label: {
                    Label(
                        Departments.shared.byCode(code: model.night?.department)?.label ?? "Choisir le département",
                        systemImage: "mappin.and.ellipse"
                    )
                }
                HStack {
                    TextField("Soundsystem ou artiste entendu", text: $model.query)
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled()
                    DictationButton { model.query = $0 }
                }
            }

            let query = model.query.trimmingCharacters(in: .whitespaces)
            if !query.isEmpty {
                Section {
                    ForEach(model.results, id: \.id) { hit in
                        Button { model.add(hit) } label: { HitRow(hit: hit) }
                    }
                    Button {
                        creating = query
                        model.query = ""
                    } label: {
                        Label("Créer « \(query) »", systemImage: "plus")
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
                    EntryRow(entry: entry, model: model)
                        .swipeActions { Button("Retirer", role: .destructive) { model.remove(entry) } }
                }
            } header: {
                FlyerLabel(text: "Entendu cette nuit")
            }

            Section {
                TextField("Titre (facultatif)", text: $title)
                    .onChange(of: title) { _, value in model.edit(title: value) }
                HStack(alignment: .top) {
                    TextField("Souvenirs de la nuit", text: $memories, axis: .vertical)
                        .lineLimit(3...8)
                        .onChange(of: memories) { _, value in model.edit(comment: value) }
                    DictationButton { memories = [memories, $0].filter { !$0.isEmpty }.joined(separator: " ") }
                }
                Toggle(isOn: Binding(get: { model.night?.shared ?? false }, set: { model.edit(shared: $0) })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Partager cette nuit").font(Theme.body(16, weight: 600))
                        Text("Visible des autres à partir du lendemain, avec le département seulement. Il faut un compte.")
                            .font(Theme.body(13))
                            .foregroundStyle(Theme.mute)
                    }
                }
            } header: {
                FlyerLabel(text: "La nuit")
            }
        }
        .flyerBackground()
        .navigationTitle(model.night.map { NightDate.format($0.date) } ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            title = model.night?.title ?? ""
            memories = model.night?.comment ?? ""
        }
        .toolbar {
            Button("Supprimer", role: .destructive) { confirmDelete = true }
        }
        .confirmationDialog("Supprimer cette nuit ?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Supprimer", role: .destructive) {
                model.delete()
                dismiss()
            }
        } message: {
            Text("Elle disparaît de ton carnet, avec tout ce que tu y as noté.")
        }
        .sheet(item: Binding(get: { creating.map(CreationName.init) }, set: { creating = $0?.name })) { item in
            CreateView(teknotes: model.teknotes, kind: .soundsystem, name: item.name, mode: .createForNight) { hit in
                model.add(hit)
            }
        }
    }
}

private struct CreationName: Identifiable {
    let name: String
    var id: String { name }
}

private struct EntryRow: View {
    let entry: NightEntry
    let model: NightModel
    @State private var comment = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(entry.label).font(Theme.body(17, weight: 700))
            // Big touch targets: rated at night, often with one hand.
            HStack(spacing: 4) {
                ForEach(1...5, id: \.self) { star in
                    Image(systemName: (entry.rating?.int32Value ?? 0) >= star ? "circle.fill" : "circle")
                        .font(.system(size: 22))
                        .foregroundStyle(Theme.pink)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                        .onTapGesture { model.rate(entry, Int32(star)) }
                        .accessibilityLabel("Note \(star) sur 5")
                        .accessibilityAddTraits(.isButton)
                }
            }
            HStack(alignment: .top) {
                TextField("Un mot sur ce set", text: $comment, axis: .vertical)
                    .font(Theme.body(15))
                    .onChange(of: comment) { _, value in model.comment(entry, value) }
                DictationButton { comment = [comment, $0].filter { !$0.isEmpty }.joined(separator: " ") }
            }
        }
        .onAppear { comment = entry.comment }
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
