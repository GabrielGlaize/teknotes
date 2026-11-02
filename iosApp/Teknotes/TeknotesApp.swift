import Observation
import Shared
import SwiftUI

@main
struct TeknotesApp: App {
    private let teknotes: Teknotes

    init() {
        Theme.registerFonts()
        #if DEBUG
        // Local Supabase (supabase start) as seen from the simulator. The key is the public
        // demo key every local Supabase uses.
        let config: SupabaseConfig? = SupabaseConfig(
            url: "http://127.0.0.1:55321",
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
        }
    }
}

/// Appearance and field mode, applied to the whole app.
@Observable
final class PreferencesModel {
    private(set) var appearance: Appearance
    private(set) var fieldMode: Bool
    @ObservationIgnored private var watcher: Watcher?

    init(teknotes: Teknotes) {
        let current = teknotes.settings.current()
        appearance = current.appearance
        fieldMode = current.fieldMode
        Theme.scale = current.fieldMode ? 1.18 : 1
        watcher = teknotes.settings.watchPreferences { [weak self] preferences in
            Theme.scale = preferences.fieldMode ? 1.18 : 1
            self?.appearance = preferences.appearance
            self?.fieldMode = preferences.fieldMode
        }
    }

    deinit { watcher?.cancel() }

    var colorScheme: ColorScheme? {
        if fieldMode { return .dark }
        switch appearance {
        case .dark: return .dark
        case .light: return .light
        default: return nil
        }
    }
}

struct RootView: View {
    let teknotes: Teknotes
    @State private var preferences: PreferencesModel
    @State private var notebook: NotebookModel
    @State private var directory: DirectoryModel
    @State private var account: AccountModel
    @State private var tab = 0

    init(teknotes: Teknotes) {
        self.teknotes = teknotes
        _preferences = State(initialValue: PreferencesModel(teknotes: teknotes))
        _notebook = State(initialValue: NotebookModel(teknotes: teknotes))
        _directory = State(initialValue: DirectoryModel(teknotes: teknotes))
        _account = State(initialValue: AccountModel(teknotes: teknotes))
    }

    var body: some View {
        TabView(selection: $tab) {
            NotebookView(model: notebook)
                .tabItem { Label("Carnet", systemImage: "list.bullet") }
                .tag(0)
            DirectoryView(model: directory)
                .tabItem { Label("Annuaire", systemImage: "magnifyingglass") }
                .tag(1)
            ProfileView(model: account)
                .tabItem { Label("Profil", systemImage: "person") }
                .tag(2)
        }
        // Fonts are sized by Theme.scale: rebuilding the tabs applies a field mode change.
        .id(preferences.fieldMode)
        .tint(preferences.fieldMode ? Theme.pinkDimmed : Theme.pink)
        .preferredColorScheme(preferences.colorScheme)
    }
}
