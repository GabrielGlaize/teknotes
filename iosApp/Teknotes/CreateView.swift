import Shared
import SwiftUI

/// Adds a soundsystem or an artist to the directory. Entries that look alike are shown
/// first: picking one avoids a duplicate. Opened from a night, the result is added to it.
struct CreateView: View {
    let teknotes: Teknotes
    let forNight: Bool
    let onDone: (DirectoryHit) -> Void

    @State private var kind: DirectoryHit.Kind
    @State private var name: String
    @State private var aliases = ""
    @State private var department: String?
    @State private var styles: Set<Style> = []
    @State private var links = ""
    @State private var memberOf: Set<String> = []
    @Environment(\.dismiss) private var dismiss

    init(teknotes: Teknotes, kind: DirectoryHit.Kind, name: String, forNight: Bool, onDone: @escaping (DirectoryHit) -> Void) {
        self.teknotes = teknotes
        self.forNight = forNight
        self.onDone = onDone
        _kind = State(initialValue: kind)
        _name = State(initialValue: name)
    }

    private var isSoundsystem: Bool { kind == .soundsystem }
    private var trimmedName: String { name.trimmingCharacters(in: .whitespaces) }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Type", selection: Binding(get: { isSoundsystem }, set: { kind = $0 ? .soundsystem : .artist })) {
                        Text("Soundsystem").tag(true)
                        Text("Artiste").tag(false)
                    }
                    .pickerStyle(.segmented)
                    TextField("Nom", text: $name).autocorrectionDisabled()
                }

                let lookalikes = teknotes.directory.lookalikes(name: name)
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
                    Button(forNight ? "Créer et ajouter à la nuit" : "Créer la fiche", action: save)
                        .disabled(trimmedName.isEmpty)
                } footer: {
                    Text("Ta fiche rejoint l'annuaire partagé une fois connecté à ton compte.")
                }
            }
            .flyerBackground()
            .navigationTitle(isSoundsystem ? "Nouveau soundsystem" : "Nouvel artiste")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
            }
        }
    }

    private func save() {
        let aliasList = aliases.components(separatedBy: ",")
        let linkList = links.components(separatedBy: "\n")
        let chosenStyles = Style.entries.filter { styles.contains($0) }
        let hit: DirectoryHit
        if isSoundsystem {
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
