import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var container: AppContainer
    @ObservedObject var model: PhoneViewModel
    let onOpenDialer: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 0) {
                    header
                    HStack(spacing: 12) {
                        quickAction("Nova ligação", icon: "circle.grid.3x3.fill", color: BrandColor.lime, action: onOpenDialer)
                        quickAction("Suporte", icon: "headphones", color: .white) { model.call("4000") }
                    }
                    .padding(20)
                    HStack {
                        Text("Recentes").font(.title3.bold())
                        Spacer()
                        Button("Ver todos") {}
                    }
                    .padding(.horizontal, 22)
                    if container.callStore.recent.isEmpty {
                        ContentUnavailableView("Nenhuma ligação recente", systemImage: "clock.arrow.circlepath", description: Text("Suas chamadas aparecerão aqui."))
                            .padding(.top, 24)
                    } else {
                        ForEach(container.callStore.recent.prefix(5)) { call in recentRow(call) }
                    }
                }
            }
            .background(BrandColor.ice)
            .toolbar(.hidden, for: .navigationBar)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            BrandMark(light: true)
            Spacer().frame(height: 28)
            Text(greeting).font(.system(size: 28, weight: .bold)).foregroundStyle(.white)
            Text("Seu ramal está pronto para chamar.").font(.subheadline).foregroundStyle(.white.opacity(0.68))
            HStack(spacing: 10) {
                Circle().fill(BrandColor.lime).frame(width: 10, height: 10)
                VStack(alignment: .leading, spacing: 2) {
                    Text(registrationTitle).font(.subheadline.weight(.semibold)).foregroundStyle(.white)
                    Text("Ramal \(container.configuration.effectiveExtension) • IPBX Conecte").font(.caption).foregroundStyle(.white.opacity(0.58))
                }
                Spacer()
                Image(systemName: "lock.shield.fill").foregroundStyle(BrandColor.lime)
            }
            .padding(17)
            .background(BrandColor.navySoft, in: RoundedRectangle(cornerRadius: 18))
            .padding(.top, 20)
        }
        .padding(.horizontal, 22).padding(.top, 12).padding(.bottom, 24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(BrandColor.navy)
    }

    private var greeting: String {
        let name = container.configuration.displayName.trimmingCharacters(in: .whitespaces)
        return name.isEmpty ? "Olá, ramal \(container.configuration.effectiveExtension)" : "Olá, \(name)"
    }

    private var registrationTitle: String {
        switch model.registration { case .online: "Disponível"; case .connecting: "Registrando…"; case .offline: "Desconectado"; case .failed: "Falha no registro SIP" }
    }

    private func quickAction(_ title: String, icon: String, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading) {
                Image(systemName: icon).font(.title3)
                Spacer()
                Text(title).font(.subheadline.bold())
            }
            .foregroundStyle(BrandColor.navy).padding(16).frame(maxWidth: .infinity, minHeight: 98, alignment: .leading)
            .background(color, in: RoundedRectangle(cornerRadius: 20))
        }
        .buttonStyle(.plain)
    }

    private func recentRow(_ call: CallRecord) -> some View {
        Button { model.call(call.handle) } label: {
            HStack(spacing: 13) {
                Text(String(call.displayName.prefix(1))).font(.headline).foregroundStyle(BrandColor.navy)
                    .frame(width: 46, height: 46).background(BrandColor.navy.opacity(0.07), in: Circle())
                VStack(alignment: .leading, spacing: 3) {
                    Text(call.displayName).font(.subheadline.weight(.semibold)).foregroundStyle(BrandColor.navy)
                    Label(call.handle, systemImage: call.direction == .missed ? "phone.down.fill" : "phone.arrow.up.right.fill")
                        .font(.caption).foregroundStyle(call.direction == .missed ? BrandColor.danger : BrandColor.muted)
                }
                Spacer()
                Text(call.occurredAt, style: .time).font(.caption).foregroundStyle(BrandColor.muted)
            }
            .padding(.horizontal, 22).padding(.vertical, 10)
        }
        .buttonStyle(.plain)
    }
}
