import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var container: AppContainer
    @ObservedObject var model: PhoneViewModel
    @State private var wifiOnly = false
    @State private var editingAccount = false
    @State private var showLogout = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Label {
                        VStack(alignment: .leading) {
                            Text(accountTitle).fontWeight(.semibold)
                            Text(registrationTitle).font(.caption).foregroundStyle(.secondary)
                        }
                    } icon: { Image(systemName: "person.crop.circle.fill").font(.title2) }
                    Button { editingAccount = true } label: {
                        Label {
                            VStack(alignment: .leading) {
                                Text("Conta SIP")
                                Text("\(container.configuration.username)@\(container.configuration.server):\(container.configuration.port)")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        } icon: { Image(systemName: "server.rack") }
                    }
                    Label {
                        VStack(alignment: .leading) {
                            Text("Chamadas em segundo plano")
                            Text(VoIPPushTokenStore.current == nil ? "Ativação pendente" : "Ativadas para este ramal")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    } icon: { Image(systemName: "bell.badge.fill") }
                }
                Section {
                    Toggle(isOn: $wifiOnly) {
                        Label {
                            VStack(alignment: .leading) {
                                Text("Chamadas apenas no Wi-Fi")
                                Text("Desative para receber também na rede móvel").font(.caption).foregroundStyle(.secondary)
                            }
                        } icon: { Image(systemName: "wifi") }
                    }
                    Label("Qualidade e diagnóstico", systemImage: "waveform.path.ecg")
                    Label("Privacidade e termos", systemImage: "hand.raised.fill")
                    Label("Código-fonte e licenças", systemImage: "chevron.left.forwardslash.chevron.right")
                }
                Section { Button("Sair da conta", role: .destructive) { showLogout = true } }
                if let error { Section { Text(error).foregroundStyle(BrandColor.danger) } }
                Section { Text("Conecte Phone 0.6.4").font(.caption).foregroundStyle(.secondary).frame(maxWidth: .infinity) }
            }
            .navigationTitle("Ajustes")
            .safeAreaInset(edge: .top) { BrandMark().frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 22).padding(.vertical, 8).background(.white) }
        }
        .sheet(isPresented: $editingAccount) {
            SIPConfigurationView(initial: container.configuration) { value in
                Task {
                    do { try await container.register(value); editingAccount = false }
                    catch { self.error = "Não foi possível registrar nesse servidor SIP." }
                }
            }
        }
        .confirmationDialog("Sair da conta?", isPresented: $showLogout, titleVisibility: .visible) {
            Button("Sair", role: .destructive) { Task { await container.logout() } }
            Button("Cancelar", role: .cancel) {}
        } message: { Text("Este iPhone deixará de receber chamadas do ramal.") }
    }

    private var accountTitle: String {
        let name = container.configuration.displayName.trimmingCharacters(in: .whitespaces)
        return name.isEmpty ? "Ramal \(container.configuration.effectiveExtension)" : "\(name) • Ramal \(container.configuration.effectiveExtension)"
    }
    private var registrationTitle: String {
        switch model.registration { case .online: "Registrado com segurança"; case .connecting: "Registrando no servidor SIP…"; case .offline: "Desconectado"; case .failed: "Falha no registro SIP" }
    }
}
