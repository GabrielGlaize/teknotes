import Shared
import SwiftUI

/// Creating an account and signing in are two separate paths; choosing a pseudo ends the first
/// sign-in, whichever way it came.
struct AuthSheet: View {
    enum Mode {
        case signUp, signIn
    }

    @State var mode: Mode
    @State private var usingCode = false
    @Environment(AccountModel.self) private var account
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Page {
                if account.needsPseudo {
                    PseudoStep()
                } else if usingCode {
                    CodeForm { usingCode = false }
                } else if mode == .signUp {
                    SignUpForm { mode = .signIn }
                } else {
                    SignInForm(onSignUp: { mode = .signUp }, onCode: { usingCode = true })
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() } }
            }
            .onChange(of: account.isSignedIn) { _, signedIn in
                if signedIn { dismiss() }
            }
            .onChange(of: mode) { _, _ in account.clearMessage() }
        }
    }
}

private struct ErrorLine: View {
    @Environment(AccountModel.self) private var account

    var body: some View {
        if let message = account.message {
            Text(message).font(Theme.text(14, weight: 600)).foregroundStyle(Theme.accent).padding(.top, 8)
        }
    }
}

private struct SignUpForm: View {
    let onSignIn: () -> Void
    @Environment(AccountModel.self) private var account

    var body: some View {
        @Bindable var account = account
        VStack(alignment: .leading, spacing: 0) {
            BigTitle(text: "Créer un compte").padding(.bottom, 8)
            Note(text: "Pour contribuer à l'annuaire, partager tes nuits et retrouver ton carnet sur un autre téléphone.")
                .padding(.bottom, 16)
            LineField(label: "Email", text: $account.email, keyboard: .emailAddress, content: .emailAddress)
            LineField(label: "Mot de passe (6 caractères minimum)", text: $account.password, content: .newPassword, secure: true)
            ErrorLine()
            ActionButton(title: "Créer mon compte", enabled: !account.busy && !account.email.isEmpty && !account.password.isEmpty) {
                account.signUp()
            }
            .padding(.top, 20)
            TextAction(title: "J'ai déjà un compte", color: Theme.ink, action: onSignIn).padding(.top, 8)
        }
    }
}

private struct SignInForm: View {
    let onSignUp: () -> Void
    let onCode: () -> Void
    @Environment(AccountModel.self) private var account

    var body: some View {
        @Bindable var account = account
        VStack(alignment: .leading, spacing: 0) {
            BigTitle(text: "Se connecter").padding(.bottom, 16)
            LineField(label: "Email", text: $account.email, keyboard: .emailAddress, content: .emailAddress)
            LineField(label: "Mot de passe", text: $account.password, content: .password, secure: true)
            ErrorLine()
            ActionButton(title: "Me connecter", enabled: !account.busy && !account.email.isEmpty && !account.password.isEmpty) {
                account.signIn()
            }
            .padding(.top, 20)
            TextAction(title: "Recevoir plutôt un code par mail", color: Theme.ink, action: onCode).padding(.top, 8)
            TextAction(title: "Je n'ai pas encore de compte", color: Theme.ink, action: onSignUp)
        }
    }
}

private struct CodeForm: View {
    let onBack: () -> Void
    @Environment(AccountModel.self) private var account

    var body: some View {
        @Bindable var account = account
        VStack(alignment: .leading, spacing: 0) {
            BigTitle(text: "Recevoir un code").padding(.bottom, 16)
            LineField(label: "Email", text: $account.email, keyboard: .emailAddress, content: .emailAddress)
            if account.codeSent {
                Note(text: "Un code à 6 chiffres vient de partir par mail.").padding(.top, 4)
                LineField(label: "Code reçu", text: $account.code, keyboard: .numberPad, content: .oneTimeCode)
            }
            ErrorLine()
            if account.codeSent {
                ActionButton(title: "Valider le code", enabled: !account.busy && !account.code.isEmpty) { account.verifyCode() }
                    .padding(.top, 20)
            } else {
                ActionButton(title: "Envoyer le code", enabled: !account.busy && !account.email.isEmpty) { account.sendCode() }
                    .padding(.top, 20)
            }
            TextAction(title: "Revenir", color: Theme.ink) {
                account.codeSent = false
                onBack()
            }
            .padding(.top, 8)
        }
    }
}

private struct PseudoStep: View {
    @Environment(AccountModel.self) private var account

    var body: some View {
        @Bindable var account = account
        VStack(alignment: .leading, spacing: 0) {
            BigTitle(text: "Ton pseudo").padding(.bottom, 8)
            Note(text: "C'est la seule chose que les autres verront de toi.").padding(.bottom, 16)
            LineField(label: "Pseudo", text: $account.pseudo)
            Toggle(isOn: $account.termsAccepted) {
                Text("J'accepte les conditions d'utilisation").font(Theme.text(15))
            }
            .tint(Theme.accent)
            .padding(.vertical, 8)
            NavigationLink {
                LegalView(privacy: false)
            } label: {
                Text("Lire les conditions").font(Theme.text(15, weight: 600)).foregroundStyle(Theme.accent).frame(minHeight: 44)
            }
            .buttonStyle(.plain)
            ErrorLine()
            ActionButton(title: "Valider", enabled: !account.busy && !account.pseudo.isEmpty && account.termsAccepted) {
                account.choosePseudo()
            }
            .padding(.top, 20)
        }
    }
}
