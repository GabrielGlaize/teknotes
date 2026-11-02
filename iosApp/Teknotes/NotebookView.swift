import Observation
import Shared
import SwiftUI

@Observable
final class NotebookModel {
    let teknotes: Teknotes
    private(set) var nights: [Night] = []
    var path: [String] = []
    @ObservationIgnored private var watcher: Watcher?

    init(teknotes: Teknotes) {
        self.teknotes = teknotes
        watcher = teknotes.notebook.watchNights { [weak self] nights in self?.nights = nights }
    }

    deinit { watcher?.cancel() }

    /// "I'm here": opens tonight's night.
    func openTonight() { path = [teknotes.notebook.checkIn(department: nil).id] }

    /// Nights by month, newest first (they already come in that order).
    var months: [(title: String, nights: [Night])] {
        var months: [(title: String, nights: [Night])] = []
        for night in nights {
            let title = NightDate.monthTitle(night.date)
            if months.last?.title == title {
                months[months.count - 1].nights.append(night)
            } else {
                months.append((title, [night]))
            }
        }
        return months
    }
}

struct NotebookView: View {
    @Bindable var model: NotebookModel
    let onFirstNote: () -> Void

    var body: some View {
        NavigationStack(path: $model.path) {
            Page {
                HStack(alignment: .center) {
                    BigTitle(text: "Tek'Notes !")
                    Spacer(minLength: 8)
                    Chip(text: "J'y suis", action: model.openTonight)
                        .accessibilityHint("Ouvre la nuit de ce soir")
                }
                .padding(.top, 8)
                .padding(.bottom, 8)

                if model.nights.isEmpty {
                    TipCard(text: "Tu viens d'entendre un set ? Note ton premier soundsystem ou artiste : tape son nom, touche la bonne ligne.", action: onFirstNote)
                }

                ForEach(model.months, id: \.title) { month in
                    Text(month.title)
                        .font(Theme.text(13, weight: 600))
                        .foregroundStyle(Theme.mute)
                        .padding(.top, 20)
                        .padding(.bottom, 6)
                    ForEach(month.nights, id: \.id) { night in
                        NavigationLink(value: night.id) { NightRow(night: night) }
                            .buttonStyle(.plain)
                    }
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: String.self) { nightId in
                NightView(model: NightModel(teknotes: model.teknotes, nightId: nightId))
            }
        }
    }
}

private struct NightRow: View {
    let night: Night

    private var heard: String {
        let shown = night.sets.prefix(3).joined(separator: ", ")
        return night.sets.count > 3 ? "\(shown)…" : shown
    }

    private var details: String {
        var parts = [NightDate.weekday(night.date), night.entryCount == 1 ? "1 set" : "\(night.entryCount) sets"]
        if night.shared { parts.append("partagée") }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Theme.ink).frame(height: 2)
            HStack(alignment: .top, spacing: 12) {
                Text(NightDate.dayNumber(night.date))
                    .font(Theme.number(46))
                    .tracking(-2)
                    .frame(width: 62, alignment: .leading)
                VStack(alignment: .leading, spacing: 3) {
                    if !night.title.isEmpty {
                        Text(night.title).font(Theme.text(16, weight: 700))
                    }
                    if night.sets.isEmpty {
                        Text("Rien de noté").font(Theme.text(14)).foregroundStyle(Theme.mute)
                    } else {
                        Text(heard)
                            .font(Theme.text(14, weight: night.title.isEmpty ? 600 : 400))
                            .foregroundStyle(night.title.isEmpty ? Theme.ink : Theme.mute)
                            .lineLimit(3)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Text(details).font(Theme.text(12)).foregroundStyle(Theme.mute)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                ScoreText(value: night.average?.doubleValue)
            }
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
    }
}
