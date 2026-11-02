import CoreText
import Shared
import SwiftUI

@main
struct TeknotesApp: App {
    private let teknotes: Teknotes

    init() {
        Theme.registerFonts()
        teknotes = TeknotesIosKt.createTeknotes()
        #if DEBUG
        // Until the sync with Supabase exists, the directory starts with the demo data.
        teknotes.directory.seedDemoIfEmpty()
        #endif
    }

    var body: some Scene {
        WindowGroup {
            RootView(teknotes: teknotes)
                .tint(Theme.pink)
        }
    }
}

struct RootView: View {
    let teknotes: Teknotes

    var body: some View {
        TabView {
            NotebookView(model: NotebookModel(teknotes: teknotes))
                .tabItem { Label("Carnet", systemImage: "list.bullet") }
            DirectoryView(model: DirectoryModel(teknotes: teknotes))
                .tabItem { Label("Annuaire", systemImage: "magnifyingglass") }
            ProfileView()
                .tabItem { Label("Profil", systemImage: "person") }
        }
    }
}
