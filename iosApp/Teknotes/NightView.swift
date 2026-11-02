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
        didSet { choices = query.trimmingCharacters(in: .whitespaces).isEmpty ? [] : Array(teknotes.directory.setChoices(query: query).prefix(10)) }
    }
    private(set) var choices: [SetChoice] = []
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
    func add(_ choice: SetChoice) {
        _ = teknotes.notebook.addSet(nightId: nightId, choice: choice)
        query = ""
    }

    func addCreated(_ hit: DirectoryHit) { add(teknotes.directory.defaultChoice(hit: hit)) }

    func rate(_ entry: NightEntry, _ rating: Int32) {
        let same = entry.rating?.int32Value == rating
        teknotes.notebook.updateEntry(entryId: entry.id, rating: same ? nil : KotlinInt(int: rating), comment: entry.comment)
    }

    func comment(_ entry: NightEntry, _ text: String) {
        teknotes.notebook.updateEntry(entryId: entry.id, rating: entry.rating, comment: text)
    }

    func change(_ entry: NightEntry, to hit: DirectoryHit) { teknotes.notebook.changeSet(entryId: entry.id, hit: hit) }

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

/// Something to create in the directory while noting a night.
private struct Creation: Identifiable {
    let name: String
    let kind: DirectoryHit.Kind
    var id: String { name + kind.name }
}

/// One part of a set to choose or change.
private struct PartChange: Identifiable {
    let entry: NightEntry
    let kind: DirectoryHit.Kind
    var id: String { entry.id + kind.name }
}

struct NightView: View {
    @State var model: NightModel
    @State private var title = ""
    @State private var memories = ""
    @State private var creating: Creation?
    @State private var changing: PartChange?
    @State private var choosingDepartment = false
    @State private var authenticating = false
    @State private var confirmDelete = false
    @Environment(AccountModel.self) private var account
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Page {
            if let night = model.night {
                Text(NightDate.title(night.date))
                    .font(Theme.display(30))
                    .tracking(-0.5)
                    .padding(.top, 4)
                    .accessibilityAddTraits(.isHeader)
                TextField("Donner un titre", text: $title)
                    .font(Theme.text(17))
                    .frame(minHeight: 40)
                    .onChange(of: title) { _, value in model.edit(title: value) }
            }

            SectionHead(text: "Sets") {
                if !model.entries.isEmpty {
                    Text("\(model.entries.count)").font(Theme.text(15)).foregroundStyle(Theme.mute)
                }
            }
            adder
            ForEach(model.entries, id: \.id) { entry in
                EntryRow(entry: entry, model: model) { changing = PartChange(entry: entry, kind: $0) }
            }
            if model.entries.isEmpty && model.query.isEmpty {
                Note(text: "Tape un artiste ou un soundsystem, puis touche la bonne ligne.").padding(.top, 12)
            }

            SectionHead(text: "Souvenirs")
            HStack(alignment: .top) {
                TextField("Ce qu'il faut retenir de cette nuit", text: $memories, axis: .vertical)
                    .font(Theme.text(16))
                    .lineLimit(3...10)
                    .padding(.top, 10)
                    .onChange(of: memories) { _, value in model.edit(comment: value) }
                DictationButton { memories = [memories, $0].filter { !$0.isEmpty }.joined(separator: " ") }
            }

            SectionHead(text: "Détails")
            RowButton(
                title: "Département",
                detail: Departments.shared.byCode(code: model.night?.department)?.name ?? "Non précisé",
                chevron: true
            ) { choosingDepartment = true }
            shareRow
        }
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            title = model.night?.title ?? ""
            memories = model.night?.comment ?? ""
        }
        .toolbar {
            Menu {
                Button("Supprimer cette nuit", role: .destructive) { confirmDelete = true }
            } label: {
                Image(systemName: "ellipsis")
            }
            .accessibilityLabel("Plus d'actions")
        }
        .confirmationDialog("Supprimer cette nuit ?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Supprimer", role: .destructive) {
                model.delete()
                dismiss()
            }
        } message: {
            Text("Elle disparaît de ton carnet, avec tout ce que tu y as noté.")
        }
        .sheet(item: $creating) { creation in
            CreateView(teknotes: model.teknotes, kind: creation.kind, name: creation.name, mode: .createForNight) { hit in
                model.addCreated(hit)
            }
        }
        .sheet(item: $changing) { part in partPicker(part) }
        .sheet(isPresented: $choosingDepartment) {
            DepartmentPicker(title: "Département") { model.edit(department: .some($0)) }
        }
        .sheet(isPresented: $authenticating) { AuthSheet(mode: .signUp) }
    }

    @ViewBuilder
    private var adder: some View {
        HStack(spacing: 4) {
            SearchLine(prompt: "Artiste ou soundsystem", text: $model.query)
            DictationButton { model.query = $0 }
        }
        .padding(.top, 6)
        let query = model.query.trimmingCharacters(in: .whitespaces)
        if !query.isEmpty {
            VStack(spacing: 0) {
                ForEach(model.choices, id: \.self) { choice in
                    Button { model.add(choice) } label: { ChoiceRow(choice: choice) }
                        .buttonStyle(.plain)
                }
                RowButton(title: "Créer l'artiste « \(query) »", color: Theme.accent) {
                    creating = Creation(name: query, kind: .artist)
                    model.query = ""
                }
                RowButton(title: "Créer le soundsystem « \(query) »", color: Theme.accent) {
                    creating = Creation(name: query, kind: .soundsystem)
                    model.query = ""
                }
            }
            .padding(.bottom, 12)
        }
    }

    @ViewBuilder
    private var shareRow: some View {
        if account.isSignedIn {
            Toggle(isOn: Binding(get: { model.night?.shared ?? false }, set: { model.edit(shared: $0) })) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Partager cette nuit").font(Theme.text(16, weight: 500))
                    Note(text: "Visible à partir du lendemain, avec ton pseudo et le département seulement.")
                }
            }
            .tint(Theme.accent)
            .padding(.vertical, 12)
        } else {
            VStack(alignment: .leading, spacing: 2) {
                Text("Partager cette nuit").font(Theme.text(16, weight: 500))
                if account.isUnavailable {
                    Note(text: "Le partage arrivera avec le serveur de l'annuaire.")
                } else {
                    Note(text: "Il faut un compte pour partager une nuit.")
                    TextAction(title: "Créer un compte ou se connecter") {
                        account.clearMessage()
                        authenticating = true
                    }
                }
            }
            .padding(.vertical, 12)
        }
    }

    private func partPicker(_ part: PartChange) -> some View {
        let isSoundsystem = part.kind == .soundsystem
        return SearchPicker<DirectoryHit>(
            title: isSoundsystem ? "Sur quel soundsystem ?" : "Quel artiste ?",
            prompt: isSoundsystem ? "Chercher un soundsystem" : "Chercher un artiste",
            items: { model.teknotes.directory.browse(kind: part.kind, query: $0) },
            id: { $0.id },
            label: { $0.name },
            onPick: { model.change(part.entry, to: $0) }
        )
    }
}

private struct ChoiceRow: View {
    let choice: SetChoice

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                SetName(artist: choice.artistName, soundsystem: choice.soundsystemName, size: 16)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 8)
                if choice.artistName == nil || choice.soundsystemName == nil {
                    Text(choice.artistName == nil ? "soundsystem seul" : "artiste seul")
                        .font(Theme.text(13))
                        .foregroundStyle(Theme.mute)
                }
            }
            .frame(minHeight: 48)
            .contentShape(Rectangle())
            Hairline()
        }
    }
}

private struct EntryRow: View {
    let entry: NightEntry
    let model: NightModel
    let onChange: (DirectoryHit.Kind) -> Void
    @State private var comment = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top) {
                SetName(artist: entry.artistName, soundsystem: entry.soundsystemName, fallback: entry.label)
                    .padding(.top, 12)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 8)
                Menu {
                    Button(entry.artistId == nil ? "Préciser l'artiste" : "Changer l'artiste") { onChange(.artist) }
                    Button(entry.soundsystemId == nil ? "Préciser le soundsystem" : "Changer le soundsystem") { onChange(.soundsystem) }
                    Button("Retirer", role: .destructive) { model.remove(entry) }
                } label: {
                    Image(systemName: "ellipsis")
                        .foregroundStyle(Theme.mute)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel("Actions sur ce set")
            }
            if entry.soundsystemId == nil || entry.artistId == nil {
                TextAction(title: entry.soundsystemId == nil ? "Sur quel soundsystem ?" : "Quel artiste ?") {
                    onChange(entry.soundsystemId == nil ? .soundsystem : .artist)
                }
            }
            NumberRate(level: Int(entry.rating?.int32Value ?? 0)) { model.rate(entry, Int32($0)) }
            HStack(alignment: .top) {
                TextField("Un mot sur ce set", text: $comment, axis: .vertical)
                    .font(Theme.text(15))
                    .padding(.top, 12)
                    .onChange(of: comment) { _, value in model.comment(entry, value) }
                DictationButton { comment = [comment, $0].filter { !$0.isEmpty }.joined(separator: " ") }
            }
            Hairline().padding(.top, 4)
        }
        .onAppear { comment = entry.comment }
    }
}

struct HitRow: View {
    let hit: DirectoryHit

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(hit.name).font(Theme.text(16, weight: 600))
                    if let alias = hit.matchedAlias {
                        Text("aussi appelé \(alias)").font(Theme.text(13)).foregroundStyle(Theme.mute)
                    }
                }
                Spacer(minLength: 8)
                Text(hit.kind == .soundsystem ? "soundsystem" : "artiste").font(Theme.text(13)).foregroundStyle(Theme.mute)
            }
            .frame(minHeight: 48)
            .contentShape(Rectangle())
            Hairline()
        }
    }
}
