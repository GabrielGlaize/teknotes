import Observation
import Shared
import SwiftUI

@Observable
final class NotebookModel {
    let teknotes: Teknotes
    private(set) var nights: [Night] = []
    @ObservationIgnored private var watcher: Watcher?

    init(teknotes: Teknotes) {
        self.teknotes = teknotes
        watcher = teknotes.notebook.watchNights { [weak self] nights in self?.nights = nights }
    }

    deinit { watcher?.cancel() }

    /// "I'm here": returns the night to open.
    func checkIn() -> String { teknotes.notebook.checkIn(department: nil).id }
}

struct NotebookView: View {
    let model: NotebookModel
    @State private var path: [String] = []

    var body: some View {
        NavigationStack(path: $path) {
            List {
                Section {
                    ScreenHeader(label: "Carnet", title: "Mes nuits")
                    Button {
                        path.append(model.checkIn())
                    } label: {
                        Text("J'y suis")
                            .font(Theme.body(20, weight: 700))
                            .frame(maxWidth: .infinity, minHeight: 56)
                    }
                    .buttonStyle(.borderedProminent)
                    .listRowSeparator(.hidden)
                }
                .listRowBackground(Color.clear)

                if model.nights.isEmpty {
                    Text("Aucune nuit notée pour l'instant. Le soir venu, appuie sur « J'y suis ».")
                        .font(Theme.body(15))
                        .foregroundStyle(Theme.mute)
                        .listRowBackground(Color.clear)
                }

                ForEach(model.nights, id: \.id) { night in
                    NavigationLink(value: night.id) { NightRow(night: night) }
                        .listRowBackground(Color.clear)
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationDestination(for: String.self) { nightId in
                NightView(model: NightModel(teknotes: model.teknotes, nightId: nightId))
            }
        }
    }
}

private struct NightRow: View {
    let night: Night

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            FlyerLabel(text: NightDate.format(night.date))
            Text(night.title.isEmpty ? (Departments.shared.byCode(code: night.department)?.label ?? "Département à préciser") : night.title)
                .font(Theme.body(17, weight: 700))
            let count = night.entryCount == 1 ? "1 son noté" : "\(night.entryCount) sons notés"
            Text(night.shared ? "\(count) · partagée" : count)
                .font(Theme.body(14))
                .foregroundStyle(Theme.mute)
        }
        .padding(.vertical, 6)
    }
}
