import Shared
import SwiftUI

/// Adds or edits a soundsystem or an artist. When adding, entries that look alike are shown
/// first: picking one avoids a duplicate. Opened from a night, the result is added to it.
struct CreateView: View {
    enum Mode: Equatable {
        case create
        case createForNight
        case edit(id: String)
    }

    let teknotes: Teknotes
    let mode: Mode
    let onDone: (DirectoryHit) -> Void

    @State private var kind: DirectoryHit.Kind
    @State private var name: String
    @State private var aliases = ""
    @State private var department: String?
    @State private var styles: Set<Style> = []
    @State private var links = ""
    @State private var memberOf: Set<String> = []
    @State private var choosingStyles = false
    @State private var choosingSoundsystem = false
    @State private var choosingDepartment = false
    @Environment(\.dismiss) private var dismiss

    init(teknotes: Teknotes, kind: DirectoryHit.Kind, name: String, mode: Mode, onDone: @escaping (DirectoryHit) -> Void) {
        self.teknotes = teknotes
        self.mode = mode
        self.onDone = onDone
        _kind = State(initialValue: kind)
        _name = State(initialValue: name)
        guard case .edit(let id) = mode else { return }
        if kind == .soundsystem, let entry = teknotes.directory.soundsystem(id: id) {
            _name = State(initialValue: entry.name)
            _aliases = State(initialValue: entry.aliases.joined(separator: ", "))
            _department = State(initialValue: entry.department)
            _styles = State(initialValue: Set(entry.styles))
            _links = State(initialValue: entry.links.joined(separator: "\n"))
        } else if let entry = teknotes.directory.artist(id: id) {
            _name = State(initialValue: entry.name)
            _aliases = State(initialValue: entry.aliases.joined(separator: ", "))
            _styles = State(initialValue: Set(entry.styles))
            _links = State(initialValue: entry.links.joined(separator: "\n"))
            _memberOf = State(initialValue: Set(teknotes.directory.soundsystemsOf(artistId: id).map(\.id)))
        }
    }

    private var editedId: String? {
        if case .edit(let id) = mode { return id }
        return nil
    }

    private var isSoundsystem: Bool { kind == .soundsystem }
    private var trimmedName: String { name.trimmingCharacters(in: .whitespaces) }

    private var soundsystemNames: [(id: String, name: String)] {
        memberOf.compactMap { id in teknotes.directory.soundsystem(id: id).map { (id, $0.name) } }.sorted { $0.name < $1.name }
    }

    var body: some View {
        NavigationStack {
            Page {
                if editedId == nil {
                    WordTabs(items: [DirectoryHit.Kind.artist, DirectoryHit.Kind.soundsystem], selection: $kind, label: { $0 == .soundsystem ? "Soundsystem" : "Artiste" }, size: 16)
                        .padding(.top, 4)
                }
                LineField(label: "Nom", text: $name, capitalization: .words)

                // While editing, the entry itself isn't a duplicate to warn about.
                let lookalikes = editedId == nil ? teknotes.directory.lookalikes(name: name) : []
                if !lookalikes.isEmpty {
                    Text("Ça existe peut-être déjà").font(Theme.text(13, weight: 600)).foregroundStyle(Theme.accent).padding(.top, 8)
                    ForEach(lookalikes, id: \.id) { hit in
                        Button { finish(hit) } label: { HitRow(hit: hit) }.buttonStyle(.plain)
                    }
                }

                if !isSoundsystem {
                    picked(label: "Joue sur") {
                        ForEach(soundsystemNames, id: \.id) { soundsystem in
                            Button { memberOf.remove(soundsystem.id) } label: { Tag(text: soundsystem.name, systemImage: "xmark") }
                                .buttonStyle(.plain)
                                .accessibilityHint("Retire ce soundsystem")
                        }
                        Button { choosingSoundsystem = true } label: { Tag(text: "Ajouter", systemImage: "plus") }
                            .buttonStyle(.plain)
                    }
                }

                picked(label: "Styles") {
                    ForEach(styles.sorted { $0.label < $1.label }, id: \.id) { style in
                        Button { styles.remove(style) } label: { Tag(text: style.label, systemImage: "xmark") }
                            .buttonStyle(.plain)
                            .accessibilityHint("Retire ce style")
                    }
                    Button { choosingStyles = true } label: { Tag(text: "Ajouter", systemImage: "plus") }
                        .buttonStyle(.plain)
                }

                LineField(label: "Aussi appelé (séparés par des virgules)", text: $aliases, capitalization: .words)
                LineField(label: "Liens (SoundCloud, Instagram… un par ligne)", text: $links, multiline: true)

                if isSoundsystem {
                    RowButton(
                        title: "Origine",
                        detail: Departments.shared.byCode(code: department)?.name ?? "Non précisée",
                        chevron: true
                    ) { choosingDepartment = true }
                }

                ActionButton(title: submitLabel, enabled: !trimmedName.isEmpty, action: save).padding(.top, 24)
                if editedId == nil {
                    Note(text: "Ta fiche rejoint l'annuaire public une fois connecté à ton compte.").padding(.top, 10)
                }
            }
            .navigationTitle(editedId != nil ? "Modifier la fiche" : "Nouvelle fiche")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
            }
            .sheet(isPresented: $choosingStyles) { StylePicker(directory: teknotes.directory, selected: $styles) }
            .sheet(isPresented: $choosingDepartment) { DepartmentPicker(title: "Origine") { department = $0 } }
            .sheet(isPresented: $choosingSoundsystem) {
                SearchPicker<DirectoryHit>(
                    title: "Joue sur",
                    prompt: "Chercher un soundsystem",
                    items: { teknotes.directory.browse(kind: .soundsystem, query: $0) },
                    id: { $0.id },
                    label: { $0.name },
                    onPick: { memberOf.insert($0.id) }
                )
            }
        }
    }

    private func picked<Content: View>(label: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label).font(Theme.text(13, weight: 600)).foregroundStyle(Theme.mute)
            TagFlow { content() }
        }
        .padding(.vertical, 12)
    }

    private var submitLabel: String {
        switch mode {
        case .create: return "Créer la fiche"
        case .createForNight: return "Créer et ajouter à la nuit"
        case .edit: return "Enregistrer"
        }
    }

    private func save() {
        let aliasList = aliases.components(separatedBy: ",")
        let linkList = links.components(separatedBy: "\n")
        let chosenStyles = styles.sorted { $0.label < $1.label }
        let hit: DirectoryHit
        if let id = editedId {
            if isSoundsystem {
                teknotes.directory.updateSoundsystem(id: id, name: trimmedName, aliases: aliasList, department: department, styles: chosenStyles, links: linkList)
            } else {
                teknotes.directory.updateArtist(id: id, name: trimmedName, aliases: aliasList, styles: chosenStyles, links: linkList, soundsystemIds: Array(memberOf))
            }
            hit = DirectoryHit(id: id, kind: kind, name: trimmedName, matchedAlias: nil, score: 100)
        } else if isSoundsystem {
            let created = teknotes.directory.createSoundsystem(name: trimmedName, aliases: aliasList, department: department, styles: chosenStyles, links: linkList)
            hit = DirectoryHit(id: created.id, kind: .soundsystem, name: created.name, matchedAlias: nil, score: 100)
        } else {
            let created = teknotes.directory.createArtist(name: trimmedName, aliases: aliasList, styles: chosenStyles, links: linkList, soundsystemIds: Array(memberOf))
            hit = DirectoryHit(id: created.id, kind: .artist, name: created.name, matchedAlias: nil, score: 100)
        }
        finish(hit)
    }

    private func finish(_ hit: DirectoryHit) {
        onDone(hit)
        dismiss()
    }
}
