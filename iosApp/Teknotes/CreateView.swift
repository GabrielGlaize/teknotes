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

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if editedId == nil {
                        Picker("Type", selection: Binding(get: { isSoundsystem }, set: { kind = $0 ? .soundsystem : .artist })) {
                            Text("Soundsystem").tag(true)
                            Text("Artiste").tag(false)
                        }
                        .pickerStyle(.segmented)
                    }
                    TextField("Nom", text: $name).autocorrectionDisabled()
                }

                // While editing, the entry itself isn't a duplicate to warn about.
                let lookalikes = editedId == nil ? teknotes.directory.lookalikes(name: name) : []
                if !lookalikes.isEmpty {
                    Section {
                        ForEach(lookalikes, id: \.id) { hit in
                            Button { finish(hit) } label: { HitRow(hit: hit) }
                        }
                    } header: {
                        FlyerLabel(text: "Ça existe peut-être déjà")
                    }
                }

                Section {
                    TextField("Aussi appelé (séparés par des virgules)", text: $aliases).autocorrectionDisabled()
                    if isSoundsystem {
                        Picker("Origine", selection: $department) {
                            Text("Inconnue").tag(String?.none)
                            ForEach(Departments.shared.all, id: \.code) { Text($0.label).tag(String?.some($0.code)) }
                        }
                    }
                    TextField("Liens (SoundCloud, Instagram… un par ligne)", text: $links, axis: .vertical)
                        .lineLimit(2...5)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }

                Section {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Style.entries, id: \.id) { style in
                                StyleChip(style: style, selected: styles.contains(style)) {
                                    if styles.contains(style) { styles.remove(style) } else { styles.insert(style) }
                                }
                            }
                        }
                    }
                } header: {
                    FlyerLabel(text: "Styles")
                }

                if !isSoundsystem {
                    Section {
                        ForEach(SoundsystemsList(teknotes: teknotes).items, id: \.id) { soundsystem in
                            Toggle(soundsystem.name, isOn: Binding(
                                get: { memberOf.contains(soundsystem.id) },
                                set: { if $0 { memberOf.insert(soundsystem.id) } else { memberOf.remove(soundsystem.id) } }
                            ))
                        }
                    } header: {
                        FlyerLabel(text: "Joue avec")
                    }
                }

                Section {
                    Button(submitLabel, action: save)
                        .disabled(trimmedName.isEmpty)
                } footer: {
                    if editedId == nil {
                        Text("Ta fiche rejoint l'annuaire partagé une fois connecté à ton compte.")
                    }
                }
            }
            .flyerBackground()
            .navigationTitle(editedId != nil ? "Modifier la fiche" : (isSoundsystem ? "Nouveau soundsystem" : "Nouvel artiste"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
            }
        }
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
        let chosenStyles = Style.entries.filter { styles.contains($0) }
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

/// Soundsystems to pick from, read once when the section shows.
private struct SoundsystemsList {
    let items: [Soundsystem]

    init(teknotes: Teknotes) {
        items = teknotes.directory.allSoundsystemsNow()
    }
}
