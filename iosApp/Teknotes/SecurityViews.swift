import LocalAuthentication
import Shared
import SwiftUI

/// The first launch: what the app is for, then the code.
struct IntroView: View {
    let teknotes: Teknotes
    /// Seen again from the profile: the code isn't asked.
    var replay = false
    let onDone: () -> Void
    @State private var page = 0
    @State private var choosingCode = false

    private let pages = [
        ("1", "Note ce que tu entends", "Un set, c'est un artiste sur un soundsystem : Pyl (Caisson Noir). Une note de 1 à 5, en deux touches, même dans le noir."),
        ("2", "Ton classement se fait tout seul", "Tes notes rangent les artistes et les soundsystems du meilleur au moins bon, sans compte ni réseau."),
        ("3", "Reste discret", "Jamais de lieu précis ni de photo. Ton carnet reste privé tant que tu ne partages pas une nuit."),
    ]

    private func next() {
        if page < pages.count - 1 {
            withAnimation(.snappy) { page += 1 }
        } else if replay || teknotes.settings.hasPasscode {
            onDone()
        } else {
            withAnimation(.snappy) { choosingCode = true }
        }
    }

    var body: some View {
        ZStack {
            Theme.paper.ignoresSafeArea()
            if choosingCode {
                PasscodeSetup(teknotes: teknotes, allowsSkipping: true, onDone: onDone)
                    .transition(.move(edge: .trailing))
            } else {
                VStack(spacing: 0) {
                    HStack {
                        Spacer()
                        TextAction(title: replay ? "Fermer" : "Passer", color: Theme.mute) {
                            page = pages.count - 1
                            next()
                        }
                    }
                    .padding(.horizontal, 24)
                    TabView(selection: $page) {
                        ForEach(pages.indices, id: \.self) { index in
                            VStack(alignment: .leading, spacing: 14) {
                                Text(pages[index].0).font(Theme.number(150)).tracking(-6)
                                Rectangle().fill(Theme.ink).frame(height: 2)
                                Text(pages[index].1).font(Theme.display(32)).tracking(-1)
                                Text(pages[index].2).font(Theme.text(16)).foregroundStyle(Theme.mute)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 24)
                            .tag(index)
                        }
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))
                    ActionButton(title: page == pages.count - 1 && !replay ? "Choisir mon code" : (page == pages.count - 1 ? "Terminer" : "Continuer"), action: next)
                        .padding(.horizontal, 24)
                        .padding(.bottom, 12)
                }
                .transition(.move(edge: .leading))
            }
        }
        .foregroundStyle(Theme.ink)
    }
}

/// Four dots and a keypad, drawn like the rest of the app: big digits, no circles.
struct Keypad: View {
    let title: String
    let note: String
    /// Called with the four digits.
    let onCode: (String) -> Void
    @State private var code = ""

    var body: some View {
        VStack(spacing: 0) {
            Text(title).font(Theme.display(30)).tracking(-1).multilineTextAlignment(.center).padding(.top, 48)
            Text(note).font(Theme.text(14)).foregroundStyle(Theme.mute).multilineTextAlignment(.center).padding(.top, 6).padding(.horizontal, 24)
            HStack(spacing: 16) {
                ForEach(0..<4, id: \.self) { index in
                    Rectangle()
                        .fill(index < code.count ? Theme.ink : Theme.line)
                        .frame(width: 36, height: 4)
                }
            }
            .padding(.top, 28)
            .accessibilityElement()
            .accessibilityLabel("\(code.count) chiffres sur 4")
            Spacer()
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 4) {
                ForEach(["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"], id: \.self) { key in
                    if key.isEmpty {
                        Color.clear.frame(height: 72)
                    } else {
                        Button {
                            press(key)
                        } label: {
                            Text(key)
                                .font(Theme.number(key == "⌫" ? 26 : 38))
                                .frame(maxWidth: .infinity, minHeight: 72)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(key == "⌫" ? "Effacer" : key)
                    }
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 16)
        }
    }

    private func press(_ key: String) {
        if key == "⌫" {
            if !code.isEmpty { code.removeLast() }
            return
        }
        guard code.count < 4 else { return }
        code += key
        if code.count == 4 {
            let entered = code
            // The last dot shows before anything changes.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                code = ""
                onCode(entered)
            }
        }
    }
}

/// Chooses a code, typed twice.
struct PasscodeSetup: View {
    let teknotes: Teknotes
    var allowsSkipping = false
    let onDone: () -> Void
    @State private var first: String?
    @State private var mismatch = false

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Theme.paper.ignoresSafeArea()
            Keypad(
                title: first == nil ? "Choisis ton code" : "Encore une fois",
                note: mismatch ? "Les deux codes sont différents. Recommence." : "Il protège ton carnet sur ce téléphone. S'il est oublié, il faudra réinstaller l'app : garde un compte pour ne rien perdre."
            ) { code in
                if let first {
                    if first == code {
                        teknotes.settings.setPasscode(code: code)
                        onDone()
                    } else {
                        self.first = nil
                        mismatch = true
                    }
                } else {
                    first = code
                    mismatch = false
                }
            }
            if allowsSkipping {
                TextAction(title: "Plus tard", color: Theme.mute, action: onDone).padding(.horizontal, 24)
            }
        }
        .foregroundStyle(Theme.ink)
    }
}

/// In front of the app, until the right code or Face ID.
struct LockView: View {
    let teknotes: Teknotes
    let onUnlock: () -> Void
    @State private var message = "Entre ton code"

    var body: some View {
        ZStack {
            Theme.paper.ignoresSafeArea()
            Keypad(title: "Tek'Notes !", note: message) { code in
                if teknotes.settings.checkPasscode(code: code) {
                    onUnlock()
                } else {
                    let wait = teknotes.settings.passcodeWaitSeconds()
                    message = wait > 0 ? "Trop d'essais. Réessaie dans \(wait) secondes." : "Ce n'est pas le bon code."
                }
            }
        }
        .foregroundStyle(Theme.ink)
        .task { tryBiometrics() }
    }

    private func tryBiometrics() {
        guard teknotes.settings.biometricsEnabled else { return }
        let context = LAContext()
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else { return }
        context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: "Ouvrir ton carnet") { success, _ in
            if success { DispatchQueue.main.async { onUnlock() } }
        }
    }
}

/// The code, from the profile.
struct SecuritySection: View {
    let teknotes: Teknotes
    @State private var hasCode = false
    @State private var biometrics = false
    @State private var flow: Flow?

    private enum Flow: Identifiable {
        case choose, changeCheck, removeCheck
        var id: Self { self }
    }

    private var biometricName: String? {
        let context = LAContext()
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else { return nil }
        return context.biometryType == .faceID ? "Face ID" : "Touch ID"
    }

    private func refresh() {
        hasCode = teknotes.settings.hasPasscode
        biometrics = teknotes.settings.biometricsEnabled
    }

    var body: some View {
        Group {
            if hasCode {
                RowButton(title: "Changer mon code") { flow = .changeCheck }
                RowButton(title: "Retirer le code") { flow = .removeCheck }
                if let biometricName {
                    Toggle(isOn: Binding(get: { biometrics }, set: {
                        teknotes.settings.setBiometricsEnabled(enabled: $0)
                        biometrics = $0
                    })) {
                        Text("Ouvrir avec \(biometricName)").font(Theme.text(16, weight: 500))
                    }
                    .tint(Theme.ink)
                    .frame(minHeight: 52)
                }
            } else {
                RowButton(title: "Protéger l'app par un code") { flow = .choose }
            }
        }
        .onAppear(perform: refresh)
        .fullScreenCover(item: $flow, onDismiss: refresh) { current in
            switch current {
            case .choose:
                PasscodeSetup(teknotes: teknotes) { flow = nil }
            case .changeCheck:
                LockView(teknotes: teknotes) { flow = .choose }
            case .removeCheck:
                LockView(teknotes: teknotes) {
                    teknotes.settings.removePasscode()
                    flow = nil
                }
            }
        }
    }
}
