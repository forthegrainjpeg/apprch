import Combine
import Foundation
@preconcurrency import CoreNFC

@MainActor
final class NFCTagController: NSObject, ObservableObject {
    enum Status: Equatable {
        case idle
        case success(String)
        case failed(String)
    }

    @Published var status: Status = .idle

    static var unavailableMessage: String {
        "NFC needs a physical iPhone, not the Simulator."
    }

    static var isAvailable: Bool {
        NFCNDEFReaderSession.readingAvailable
    }

    nonisolated private let pending = Pending()
    private var session: NFCNDEFReaderSession?

    func write(url: URL) {
        guard Self.isAvailable else {
            status = .failed(Self.unavailableMessage)
            return
        }
        pending.urlToWrite = url
        pending.onRead = nil
        status = .idle
        beginSession(alert: "Hold the top of your iPhone on the tag to write this Task.")
    }

    func read(onURL: @escaping (URL) -> Void) {
        guard Self.isAvailable else {
            status = .failed(Self.unavailableMessage)
            return
        }
        pending.urlToWrite = nil
        pending.onRead = onURL
        status = .idle
        beginSession(alert: "Hold the top of your iPhone on the tag to read it.")
    }

    private func beginSession(alert: String) {
        session?.invalidate()
        let next = NFCNDEFReaderSession(delegate: self, queue: nil, invalidateAfterFirstRead: false)
        next.alertMessage = alert
        session = next
        next.begin()
    }

    fileprivate func publish(success: Bool, message: String) {
        status = success ? .success(message) : .failed(message)
    }
}

extension NFCTagController: NFCNDEFReaderSessionDelegate {
    nonisolated func readerSessionDidBecomeActive(_ session: NFCNDEFReaderSession) {}

    nonisolated func readerSession(_ session: NFCNDEFReaderSession, didInvalidateWithError error: Error) {
        let nfcError = error as NSError
        if nfcError.domain == NFCReaderError.errorDomain,
           nfcError.code == NFCReaderError.readerSessionInvalidationErrorUserCanceled.rawValue {
            return
        }
        let message = error.localizedDescription
        Task { @MainActor in
            self.publish(success: false, message: message)
        }
    }

    nonisolated func readerSession(_ session: NFCNDEFReaderSession, didDetectNDEFs messages: [NFCNDEFMessage]) {}

    nonisolated func readerSession(_ session: NFCNDEFReaderSession, didDetect tags: [any NFCNDEFTag]) {
        guard let first = tags.first else { return }
        if tags.count > 1 {
            session.alertMessage = "More than one tag found. Try again with a single tag."
            session.restartPolling()
            return
        }

        let pending = self.pending
        nonisolated(unsafe) let connectedSession = session
        nonisolated(unsafe) let connectedTag = first
        connectedSession.connect(to: connectedTag) { [weak self] error in
            if let error {
                connectedSession.invalidate(errorMessage: error.localizedDescription)
                return
            }
            if let url = pending.urlToWrite {
                self?.write(url, to: connectedTag, session: connectedSession)
            } else {
                self?.read(from: connectedTag, session: connectedSession)
            }
        }
    }

    nonisolated fileprivate func write(_ url: URL, to tag: any NFCNDEFTag, session: NFCNDEFReaderSession) {
        nonisolated(unsafe) let connectedSession = session
        nonisolated(unsafe) let connectedTag = tag
        connectedTag.queryNDEFStatus { [weak self] status, capacity, error in
            if let error {
                connectedSession.invalidate(errorMessage: error.localizedDescription)
                return
            }
            guard status != .notSupported else {
                connectedSession.invalidate(errorMessage: "This tag isn’t writable.")
                return
            }
            guard status != .readOnly else {
                connectedSession.invalidate(errorMessage: "This tag is locked and can’t be written.")
                return
            }
            guard let payload = NFCNDEFPayload.wellKnownTypeURIPayload(url: url) else {
                connectedSession.invalidate(errorMessage: "Couldn’t build the tag link.")
                return
            }
            let message = NFCNDEFMessage(records: [payload])
            guard message.length <= capacity else {
                connectedSession.invalidate(errorMessage: "This tag doesn’t have enough space.")
                return
            }
            connectedTag.writeNDEF(message) { error in
                if let error {
                    connectedSession.invalidate(errorMessage: error.localizedDescription)
                    return
                }
                connectedSession.alertMessage = "Tag written."
                connectedSession.invalidate()
                Task { @MainActor in
                    self?.publish(success: true, message: "Tag written.")
                }
            }
        }
    }

    nonisolated fileprivate func read(from tag: any NFCNDEFTag, session: NFCNDEFReaderSession) {
        nonisolated(unsafe) let connectedSession = session
        nonisolated(unsafe) let connectedTag = tag
        let onRead = pending.onRead
        connectedTag.readNDEF { [weak self] message, error in
            if let error {
                connectedSession.invalidate(errorMessage: error.localizedDescription)
                return
            }
            let url = message?.records.compactMap { $0.wellKnownTypeURIPayload() }.first
            guard let url else {
                connectedSession.invalidate(errorMessage: "No link found on this tag.")
                return
            }
            connectedSession.alertMessage = "Tag read."
            connectedSession.invalidate()
            Task { @MainActor in
                onRead?(url)
                self?.publish(success: true, message: "Tag read.")
            }
        }
    }
}

/// CoreNFC callbacks are not Sendable; this box lets the session read the pending URL off the main actor.
nonisolated private final class Pending: @unchecked Sendable {
    var urlToWrite: URL?
    var onRead: ((URL) -> Void)?
}
