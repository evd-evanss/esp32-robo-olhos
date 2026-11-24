import SwiftUI
import UIKit
import ComposeApp
#if canImport(FoundationModels)
import FoundationModels
#endif

@main
struct RoboOlhosApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeRootView()
                .ignoresSafeArea()
                .preferredColorScheme(.dark)
        }
    }
}

private struct ComposeRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let intelligence = AppleIntelligenceBridge()
        return MainViewControllerKt.MainViewController(
            aiAvailable: intelligence.isAvailable,
            aiResponder: { prompt, completion in
                intelligence.respond(to: prompt) { reply, emotion in
                    completion(reply, emotion)
                }
                return KotlinUnit()
            }
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private final class AppleIntelligenceBridge {
    var isAvailable: Bool {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            return SystemLanguageModel.default.isAvailable
        }
        #endif
        return false
    }

    func respond(to prompt: String, completion: @escaping (String?, String?) -> Void) {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *), SystemLanguageModel.default.isAvailable {
            Task {
                let result = await OnDeviceRobotModel.shared.respond(to: prompt)
                await MainActor.run { completion(result?.reply, result?.emotion) }
            }
            return
        }
        #endif
        completion(nil, nil)
    }
}

#if canImport(FoundationModels)
@available(iOS 26.0, *)
private final class OnDeviceRobotModel {
    static let shared = OnDeviceRobotModel()

    private let session = LanguageModelSession(instructions: """
        Você é a personalidade de um pequeno robô de mesa chamado Robô Olhos.
        Converse em português brasileiro de forma afetuosa, curiosa e natural.
        Lembre do contexto desta conversa. Faça perguntas curtas quando isso ajudar.
        Nunca alegue ter câmera, braços, pernas ou acesso à internet.
        Responda em no máximo duas frases curtas, adequadas para serem faladas em voz alta.
        Termine sempre com uma nova linha no formato exato:
        EMOCAO: feliz
        Troque feliz por somente uma destas opções: feliz, triste, raiva, neutro, surpresa, curioso, sono.
        """)

    func respond(to prompt: String) async -> (reply: String, emotion: String)? {
        do {
            let options = GenerationOptions(temperature: 0.7, maximumResponseTokens: 100)
            let response = try await session.respond(to: prompt, options: options)
            return parse(response.content)
        } catch {
            return nil
        }
    }

    private func parse(_ content: String) -> (reply: String, emotion: String)? {
        let marker = "EMOCAO:"
        guard let range = content.range(of: marker, options: .backwards) else {
            let reply = content.trimmingCharacters(in: .whitespacesAndNewlines)
            return reply.isEmpty ? nil : (reply, "neutro")
        }
        let reply = String(content[..<range.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
        let emotion = String(content[range.upperBound...])
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        let allowed = ["feliz", "triste", "raiva", "neutro", "surpresa", "curioso", "sono"]
        return reply.isEmpty ? nil : (reply, allowed.contains(emotion) ? emotion : "neutro")
    }
}
#endif
