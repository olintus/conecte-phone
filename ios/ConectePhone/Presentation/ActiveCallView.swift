import SwiftUI
import UIKit

struct ActiveCallView: View {
    let call: ActiveCall
    @ObservedObject var model: PhoneViewModel
    @State private var now = Date()
    @State private var showingKeypad = false

    var body: some View {
        VStack {
            HStack(spacing: 8) {
                Circle().fill(BrandColor.lime).frame(width: 9, height: 9)
                Text("CONECTE PHONE").font(.caption2.bold()).tracking(1.5).foregroundStyle(.white.opacity(0.7))
                Spacer()
            }
            Spacer()
            Text(String(call.displayName.prefix(1)).uppercased())
                .font(.system(size: 43, weight: .bold)).foregroundStyle(.white)
                .frame(width: 112, height: 112).background(.white.opacity(0.1), in: Circle())
            Text(call.displayName).font(.system(size: 29, weight: .bold)).foregroundStyle(.white).padding(.top, 22)
            Text(call.handle).font(.body).foregroundStyle(.white.opacity(0.58))
            Text(status).font(.subheadline.weight(.medium)).foregroundStyle(BrandColor.lime).padding(.top, 6)
            Spacer()
            HStack {
                control("mic.slash.fill", "Mudo", selected: call.isMuted) { model.mute(!call.isMuted) }
                Spacer()
                control("circle.grid.3x3.fill", "Teclado") { showingKeypad = true }
                Spacer()
                control("speaker.wave.2.fill", "Viva-voz", selected: call.isSpeakerEnabled) { model.speaker(!call.isSpeakerEnabled) }
            }
            .padding(.horizontal, 24)
            Button { model.end() } label: {
                Image(systemName: "phone.down.fill").font(.system(size: 29, weight: .semibold)).foregroundStyle(.white)
                    .frame(width: 72, height: 72).background(BrandColor.danger, in: Circle())
            }
            .padding(.top, 40).padding(.bottom, 24)
        }
        .padding(26).background(BrandColor.navy.ignoresSafeArea())
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1))
                now = Date()
            }
        }
        .sheet(isPresented: $showingKeypad) {
            CallKeypadView { digit in
                model.dtmf(digit)
            }
            .presentationDetents([.medium])
        }
    }

    private var status: String {
        switch call.phase {
        case .dialing: "Chamando…"
        case .ringing: "Ligação recebida"
        case .connecting: "Conectando…"
        case .active:
            if let start = call.startedAt {
                Duration.seconds(now.timeIntervalSince(start)).formatted(.time(pattern: .minuteSecond(padMinuteToLength: 2)))
            } else { "Em ligação" }
        default: "Finalizando…"
        }
    }

    private func control(_ icon: String, _ label: String, selected: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: icon).font(.title3).foregroundStyle(selected ? BrandColor.navy : .white)
                    .frame(width: 62, height: 62).background(selected ? .white : .white.opacity(0.1), in: Circle())
                Text(label).font(.caption).foregroundStyle(.white.opacity(0.78))
            }
        }
    }
}

private struct CallKeypadView: View {
    @Environment(\.dismiss) private var dismiss
    let onDigit: (Character) -> Void

    private let keys: [(digit: String, letters: String)] = [
        ("1", ""), ("2", "ABC"), ("3", "DEF"),
        ("4", "GHI"), ("5", "JKL"), ("6", "MNO"),
        ("7", "PQRS"), ("8", "TUV"), ("9", "WXYZ"),
        ("*", ""), ("0", "+"), ("#", "")
    ]

    var body: some View {
        NavigationStack {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 12) {
                ForEach(keys, id: \.digit) { key in
                    Button {
                        guard let digit = key.digit.first else { return }
                        onDigit(digit)
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    } label: {
                        VStack(spacing: 0) {
                            Text(key.digit).font(.system(size: 26, weight: .medium))
                            Text(key.letters).font(.system(size: 9, weight: .medium)).tracking(1.5).frame(height: 11)
                        }
                        .foregroundStyle(BrandColor.navy)
                        .frame(width: 68, height: 58)
                        .background(BrandColor.ice, in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(key.letters.isEmpty ? key.digit : "\(key.digit), \(key.letters)")
                }
            }
            .padding(.horizontal, 42)
            .navigationTitle("Teclado")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Concluído") { dismiss() }
                }
            }
        }
    }
}
