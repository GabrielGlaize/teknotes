import Observation
import Shared
import SwiftUI

@main
struct TeknotesApp: App {
    private let teknotes: Teknotes
    @Environment(\.scenePhase) private var scenePhase

    init() {
        #if DEBUG
        // Local Supabase (supabase start). The simulator reaches it on 127.0.0.1; a real iPhone
        // needs the Mac's address (scripts/iphone.sh passes it to the build). The key is the
        // public demo key every local Supabase uses.
        let url = Bundle.main.object(forInfoDictionaryKey: "TeknotesSupabaseURL") as? String ?? "http://127.0.0.1:55321"
        let config: SupabaseConfig? = SupabaseConfig(
            url: url,
            key: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"
        )
        #else
        // No production project yet: the release build works offline until one exists.
        let config: SupabaseConfig? = nil
        #endif
        teknotes = TeknotesIosKt.createTeknotes(config: config)
        #if DEBUG
        // Before the first sync (or with no server), the directory starts with the demo data.
        teknotes.directory.seedDemoIfEmpty()
        #endif
    }

    var body: some Scene {
        WindowGroup {
            RootView(teknotes: teknotes)
                .onChange(of: scenePhase) { _, phase in
                    // Back in the app: whatever was waiting for the network gets another chance.
                    if phase == .active { teknotes.sync.schedule() }
                }
        }
    }
}

/// Appearance, applied to the whole app.
@Observable
final class PreferencesModel {
    private(set) var appearance: Appearance
    @ObservationIgnored private var watcher: Watcher?

    init(teknotes: Teknotes) {
        appearance = teknotes.settings.current().appearance
        watcher = teknotes.settings.watchPreferences { [weak self] preferences in
            self?.appearance = preferences.appearance
        }
    }

    deinit { watcher?.cancel() }

    var colorScheme: ColorScheme? {
        switch appearance {
        case .dark: return .dark
        case .light: return .light
        default: return nil
        }
    }
}

/// What stands in front of the app: the first-launch tutorial, then the code.
@Observable
final class GateModel {
    let teknotes: Teknotes
    private(set) var tutorialDone: Bool
    private(set) var locked: Bool

    init(teknotes: Teknotes) {
        self.teknotes = teknotes
        #if DEBUG
        // Screen tests start in the app itself.
        if ProcessInfo.processInfo.arguments.contains("-skipIntro") { teknotes.settings.setTutorialDone() }
        #endif
        tutorialDone = teknotes.settings.tutorialDone
        locked = teknotes.settings.hasPasscode
    }

    func finishTutorial() {
        teknotes.settings.setTutorialDone()
        tutorialDone = true
    }

    func unlock() { locked = false }

    /// Leaving the app locks it again.
    func wentToBackground() {
        if teknotes.settings.hasPasscode { locked = true }
    }
}

struct RootView: View {
    let teknotes: Teknotes
    @State private var gate: GateModel
    @State private var preferences: PreferencesModel
    @State private var notebook: NotebookModel
    @State private var directory: DirectoryModel
    @State private var account: AccountModel
    @State private var tab = 0
    @Environment(\.scenePhase) private var scenePhase

    init(teknotes: Teknotes) {
        self.teknotes = teknotes
        _gate = State(initialValue: GateModel(teknotes: teknotes))
        _preferences = State(initialValue: PreferencesModel(teknotes: teknotes))
        _notebook = State(initialValue: NotebookModel(teknotes: teknotes))
        _directory = State(initialValue: DirectoryModel(teknotes: teknotes))
        _account = State(initialValue: AccountModel(teknotes: teknotes))
    }

    /// From a tip anywhere: opens tonight's night to write the first note.
    private func firstNote() {
        notebook.openTonight()
        withAnimation(.snappy) { tab = 0 }
    }

    var body: some View {
        ZStack {
            if gate.tutorialDone {
                ZStack(alignment: .bottom) {
                    // Paged: the screen follows the finger from one tab to the next.
                    TabView(selection: $tab) {
                        NotebookView(model: notebook, onFirstNote: firstNote).tag(0)
                        DirectoryView(model: directory, onFirstNote: firstNote).tag(1)
                        ProfileView(model: account).tag(2)
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))
                    .ignoresSafeArea(.container, edges: .bottom)
                    FloatingBar(tab: $tab)
                }
                .background(Theme.paper.ignoresSafeArea())
                if gate.locked {
                    LockView(teknotes: teknotes) { gate.unlock() }
                        .transition(.opacity)
                }
            } else {
                IntroView(teknotes: teknotes) { gate.finishTutorial() }
            }
        }
        .environment(account)
        .environment(gate)
        .tint(Theme.accent)
        .preferredColorScheme(preferences.colorScheme)
        .background(KeyboardDismisser())
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { gate.wentToBackground() }
        }
    }
}

/// The tabs, floating above the screen: a framed bar with barely rounded corners.
private struct FloatingBar: View {
    @Binding var tab: Int
    private let items = ["Carnet", "Annuaire", "Profil"]

    var body: some View {
        HStack(spacing: 4) {
            ForEach(items.indices, id: \.self) { index in
                Button {
                    withAnimation(.snappy) { tab = index }
                } label: {
                    Text(items[index])
                        .font(Theme.text(14, weight: 700))
                        .foregroundStyle(tab == index ? Theme.paper : Theme.ink)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(tab == index ? Theme.ink : Color.clear, in: RoundedRectangle(cornerRadius: 9))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(tab == index ? .isSelected : [])
            }
        }
        .padding(5)
        .background(Theme.paper, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.ink, lineWidth: 1.5))
        .shadow(color: .black.opacity(0.12), radius: 10, y: 4)
        .padding(.horizontal, 20)
        .padding(.bottom, 6)
        .ignoresSafeArea(.keyboard)
    }
}
