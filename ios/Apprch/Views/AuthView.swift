import SwiftUI

struct AuthView: View {
    @EnvironmentObject var authVM: AuthViewModel

    @State private var isSignUp = false
    @State private var email = ""
    @State private var password = ""
    @State private var displayName = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var showReset = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer()

                VStack(spacing: 8) {
                    Text("Apprch")
                        .font(.largeTitle.bold())
                    Text("Tap once. Everyone knows.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                VStack(spacing: 16) {
                    if isSignUp {
                        TextField("Your name", text: $displayName)
                            .textContentType(.name)
                            .textFieldStyle(.roundedBorder)
                    }

                    TextField("Email", text: $email)
                        .textContentType(isSignUp ? .emailAddress : .username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .textFieldStyle(.roundedBorder)

                    SecureField("Password", text: $password)
                        .textContentType(isSignUp ? .newPassword : .password)
                        .textFieldStyle(.roundedBorder)

                    if let msg = errorMessage {
                        Text(msg)
                            .foregroundStyle(.red)
                            .font(.caption)
                    }

                    Button {
                        submit()
                    } label: {
                        if isLoading {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                        } else {
                            Text(isSignUp ? "Create account" : "Sign in")
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isLoading || !formValid)

                    if !isSignUp {
                        Button("Forgot Password") {
                            showReset = true
                        }
                        .font(.footnote)
                        .disabled(!AuthEmail.isComplete(email))
                    }
                }
                .padding(.horizontal)

                Button {
                    withAnimation { isSignUp.toggle() }
                    errorMessage = nil
                } label: {
                    Text(isSignUp ? "Already have an account? Sign in" : "New here? Create account")
                        .font(.footnote)
                }

                Spacer()
            }
            .navigationDestination(isPresented: $showReset) {
                ResetPasswordView(initialEmail: email)
            }
        }
    }

    private var formValid: Bool {
        !email.isEmpty && password.count >= 6 && (!isSignUp || !displayName.isEmpty)
    }

    private func submit() {
        isLoading = true
        errorMessage = nil
        Task {
            do {
                if isSignUp {
                    try await authVM.signUp(email: email, password: password, displayName: displayName)
                } else {
                    try await authVM.signIn(email: email, password: password)
                }
            } catch {
                errorMessage = AuthUserFacing.message(for: error)
            }
            isLoading = false
        }
    }
}

struct ResetPasswordView: View {
    @EnvironmentObject var authVM: AuthViewModel

    @State private var email: String
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var resetSent = false

    init(initialEmail: String) {
        _email = State(initialValue: initialEmail)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Provide the email on your account. We’ll send a reset link there.")
                .foregroundStyle(.secondary)

            TextField("Email", text: $email)
                .textContentType(.emailAddress)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .textFieldStyle(.roundedBorder)
                .onChange(of: email) { _, _ in
                    resetSent = false
                    errorMessage = nil
                }

            if let errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
                    .font(.caption)
            }

            Button {
                sendReset()
            } label: {
                if isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                } else {
                    Text("Reset password")
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(isLoading || !AuthEmail.isComplete(email))

            if resetSent {
                Text("Check your email for a reset link.")
                    .font(.subheadline)
                    .foregroundStyle(.green)
            }

            Spacer()
        }
        .padding()
        .navigationTitle("Reset password")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func sendReset() {
        isLoading = true
        errorMessage = nil
        resetSent = false
        Task {
            do {
                try await authVM.sendPasswordReset(email: email)
                resetSent = true
            } catch {
                errorMessage = AuthUserFacing.message(for: error)
            }
            isLoading = false
        }
    }
}

enum AuthEmail {
    static func isComplete(_ value: String) -> Bool {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        let parts = trimmed.split(separator: "@", omittingEmptySubsequences: false)
        guard parts.count == 2, !parts[0].isEmpty else { return false }
        let domain = parts[1]
        return domain.contains(".")
            && !domain.hasPrefix(".")
            && !domain.hasSuffix(".")
            && !domain.contains(" ")
    }
}
