import AVFoundation
import Observation
import Speech
import SwiftUI

/// Dictation for noisy, one-handed moments. Tap to talk, tap again to stop.
@Observable
final class Dictation {
    private(set) var listening = false
    private(set) var denied = false
    @ObservationIgnored private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "fr-FR"))
    @ObservationIgnored private let engine = AVAudioEngine()
    @ObservationIgnored private var request: SFSpeechAudioBufferRecognitionRequest?
    @ObservationIgnored private var task: SFSpeechRecognitionTask?

    func toggle(onText: @escaping (String) -> Void) {
        if listening {
            stop()
            return
        }
        SFSpeechRecognizer.requestAuthorization { status in
            AVAudioApplication.requestRecordPermission { granted in
                DispatchQueue.main.async {
                    guard status == .authorized, granted else {
                        self.denied = true
                        return
                    }
                    self.start(onText: onText)
                }
            }
        }
    }

    private func start(onText: @escaping (String) -> Void) {
        guard let recognizer, recognizer.isAvailable else {
            denied = true
            return
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)
            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = false
            self.request = request
            let input = engine.inputNode
            input.installTap(onBus: 0, bufferSize: 1024, format: input.outputFormat(forBus: 0)) { buffer, _ in
                request.append(buffer)
            }
            engine.prepare()
            try engine.start()
            listening = true
            task = recognizer.recognitionTask(with: request) { [weak self] result, error in
                DispatchQueue.main.async {
                    if let result, result.isFinal {
                        onText(result.bestTranscription.formattedString)
                    }
                    if error != nil || result?.isFinal == true {
                        self?.stop()
                    }
                }
            }
        } catch {
            stop()
        }
    }

    private func stop() {
        if engine.isRunning {
            engine.stop()
            engine.inputNode.removeTap(onBus: 0)
        }
        request?.endAudio()
        request = nil
        task = nil
        listening = false
    }
}

struct DictationButton: View {
    let onText: (String) -> Void
    @State private var dictation = Dictation()

    var body: some View {
        Button {
            dictation.toggle(onText: onText)
        } label: {
            Image(systemName: dictation.listening ? "mic.fill" : "mic")
                .font(.system(size: 20))
                .foregroundStyle(dictation.listening ? Theme.pink : Theme.mute)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.borderless)
        .accessibilityLabel(dictation.listening ? "Arrêter la dictée" : "Dicter")
    }
}
