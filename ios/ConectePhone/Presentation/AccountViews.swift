import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var container: AppContainer
    @State private var configuration = SIPConfiguration()
    @State private var showConfiguration = false
    @State private var showScanner = false
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        ZStack {
            BrandColor.navy.ignoresSafeArea()
            VStack {
                BrandMark(light: true).frame(maxWidth: .infinity, alignment: .leading)
                Spacer()
                VStack(alignment: .leading, spacing: 16) {
                    Text("Bem-vindo").font(.largeTitle.bold()).foregroundStyle(BrandColor.navy)
                    Text("Acesse seu ramal Conecte").foregroundStyle(BrandColor.muted)
                    Button { showScanner = true } label: { Label("Configurar com QR Code", systemImage: "qrcode.viewfinder").frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent).tint(BrandColor.lime).foregroundStyle(BrandColor.navy)
                    Button("Configurar manualmente") { configuration = container.configuration; showConfiguration = true }
                        .frame(maxWidth: .infinity)
                    if loading { ProgressView("Registrando…") }
                    if let error { Text(error).font(.caption).foregroundStyle(BrandColor.danger) }
                }
                .padding(24).background(.white, in: RoundedRectangle(cornerRadius: 28))
                Spacer()
                Text("Telefonia IPBX Conecte").font(.caption).foregroundStyle(.white.opacity(0.55))
            }.padding(24)
        }
        .sheet(isPresented: $showConfiguration) {
            SIPConfigurationView(initial: configuration) { register($0); showConfiguration = false }
        }
        .fullScreenCover(isPresented: $showScanner) {
            ZStack(alignment: .topTrailing) {
                QRScannerView { result in
                    showScanner = false
                    switch result {
                    case let .success(raw):
                        do { register(try SIPQRProvisioning.parse(raw)) }
                        catch { self.error = error.localizedDescription }
                    case .failure: error = "Não foi possível abrir a câmera."
                    }
                }.ignoresSafeArea()
                Button("Cancelar") { showScanner = false }.buttonStyle(.borderedProminent).padding()
            }
        }
    }

    private func register(_ value: SIPConfiguration) {
        loading = true; error = nil
        Task {
            do { try await container.register(value) }
            catch { self.error = "Não foi possível registrar os dados SIP." }
            loading = false
        }
    }
}

struct SIPConfigurationView: View {
    @Environment(\.dismiss) private var dismiss
    @State var configuration: SIPConfiguration
    @State private var showScanner = false
    let onSave: (SIPConfiguration) -> Void

    init(initial: SIPConfiguration, onSave: @escaping (SIPConfiguration) -> Void) {
        _configuration = State(initialValue: initial); self.onSave = onSave
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Button { showScanner = true } label: { Label("Ler QR Code do portal", systemImage: "qrcode.viewfinder") }
                }
                Section("Servidor") {
                    TextField("Servidor ou domínio", text: $configuration.server).textInputAutocapitalization(.never).autocorrectionDisabled()
                    TextField("Porta", value: $configuration.port, format: .number).keyboardType(.numberPad)
                    Picker("Transporte", selection: $configuration.transport) { ForEach(SIPTransport.allCases, id: \.self) { Text($0.rawValue) } }
                }
                Section("Credenciais") {
                    TextField("Usuário SIP", text: $configuration.username).textInputAutocapitalization(.never).autocorrectionDisabled()
                    TextField("Ramal", text: $configuration.extensionNumber).keyboardType(.phonePad)
                    TextField("Nome de exibição", text: $configuration.displayName)
                    SecureField("Senha SIP", text: $configuration.password)
                }
            }
            .navigationTitle("Conta SIP")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Salvar") { onSave(configuration) }.disabled(!configuration.isConfigured)
                }
            }
        }
        .fullScreenCover(isPresented: $showScanner) {
            ZStack(alignment: .topTrailing) {
                QRScannerView { result in
                    showScanner = false
                    if case let .success(raw) = result, let parsed = try? SIPQRProvisioning.parse(raw) { configuration = parsed }
                }.ignoresSafeArea()
                Button("Cancelar") { showScanner = false }.buttonStyle(.borderedProminent).padding()
            }
        }
    }
}
