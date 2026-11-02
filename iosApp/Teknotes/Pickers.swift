import Shared
import SwiftUI

/// Picks one thing in a long list: type to narrow it down, touch a line to choose.
struct SearchPicker<Item>: View {
    let title: String
    let prompt: String
    let items: (String) -> [Item]
    let id: (Item) -> String
    let label: (Item) -> String
    let onPick: (Item) -> Void
    var clearLabel: String?
    var onClear: (() -> Void)?
    var createLabel: ((String) -> String)?
    var onCreate: ((String) -> Void)?
    @State private var query = ""
    @Environment(\.dismiss) private var dismiss

    private struct Line {
        let id: String
        let item: Item
    }

    var body: some View {
        NavigationStack {
            Page {
                SearchLine(prompt: prompt, text: $query).padding(.top, 8).padding(.bottom, 8)
                if let clearLabel, let onClear {
                    RowButton(title: clearLabel, color: Theme.mute) {
                        onClear()
                        dismiss()
                    }
                }
                let trimmed = query.trimmingCharacters(in: .whitespaces)
                ForEach(items(query).map { Line(id: id($0), item: $0) }, id: \.id) { line in
                    RowButton(title: label(line.item)) {
                        onPick(line.item)
                        dismiss()
                    }
                }
                if !trimmed.isEmpty, let createLabel, let onCreate {
                    RowButton(title: createLabel(trimmed), color: Theme.accent) {
                        onCreate(trimmed)
                        dismiss()
                    }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
            }
        }
    }
}

struct DepartmentPicker: View {
    let title: String
    let onPick: (String?) -> Void

    var body: some View {
        SearchPicker<Department>(
            title: title,
            prompt: "Nom ou numéro",
            items: { Departments.shared.search(query: $0) },
            id: { $0.code },
            label: { $0.label },
            onPick: { onPick($0.code) },
            clearLabel: "Non précisé",
            onClear: { onPick(nil) }
        )
    }
}

/// Styles to choose from: the most used first, any new one in a word.
struct StylePicker: View {
    let directory: DirectoryRepository
    /// Filter: only what the directory has, one at a time. Otherwise: several, and new ones.
    var filtering = false
    @Binding var selected: Set<Style>
    @State private var query = ""
    @Environment(\.dismiss) private var dismiss

    private var shown: [Style] {
        let choices = directory.styleChoices(query: query, usedOnly: filtering)
        // A style just created isn't in the directory yet.
        let extra = query.isEmpty ? selected.filter { !choices.contains($0) }.sorted { $0.label < $1.label } : []
        return extra + choices
    }

    private var creatable: Style? {
        guard !filtering, let style = directory.styleNamed(text: query), !shown.contains(style) else { return nil }
        return style
    }

    var body: some View {
        NavigationStack {
            Page {
                SearchLine(prompt: filtering ? "Chercher un style" : "Chercher ou ajouter un style", text: $query, capitalization: .sentences)
                    .padding(.top, 8)
                    .padding(.bottom, 8)
                ForEach(shown, id: \.id) { style in
                    Button {
                        toggle(style)
                    } label: {
                        VStack(spacing: 0) {
                            HStack {
                                Text(style.label).font(Theme.text(16, weight: selected.contains(style) ? 700 : 400))
                                Spacer()
                                if selected.contains(style) {
                                    Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(Theme.accent)
                                }
                            }
                            .frame(minHeight: 48)
                            .contentShape(Rectangle())
                            Hairline()
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(selected.contains(style) ? .isSelected : [])
                }
                if let creatable {
                    RowButton(title: "Ajouter « \(creatable.label) »", color: Theme.accent) {
                        selected.insert(creatable)
                        query = ""
                    }
                } else if shown.isEmpty {
                    Note(text: "Aucun style ne correspond.").padding(.top, 12)
                }
            }
            .navigationTitle("Styles")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("OK") { dismiss() } }
            }
        }
    }

    private func toggle(_ style: Style) {
        if filtering {
            selected = selected.contains(style) ? [] : [style]
            dismiss()
        } else if selected.contains(style) {
            selected.remove(style)
        } else {
            selected.insert(style)
        }
    }
}
