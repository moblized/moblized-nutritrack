import AVFoundation
import UIKit
import Shared

/// Scans food barcodes (EAN-13, EAN-8, UPC-E; UPC-A arrives as EAN-13) with the camera.
final class CameraBarcodeScanner: NSObject, BarcodeScannerBridge {
    func scan(onResult: @escaping (String?) -> Void, onError: @escaping (String) -> Void) {
        DispatchQueue.main.async {
            guard AVCaptureDevice.default(for: .video) != nil else {
                onError("this device has no camera")
                return
            }
            guard let root = Self.topViewController() else {
                onError("couldn't open the camera")
                return
            }
            let present = {
                let vc = ScannerViewController(onResult: onResult)
                vc.modalPresentationStyle = .fullScreen
                root.present(vc, animated: true)
            }
            switch AVCaptureDevice.authorizationStatus(for: .video) {
            case .authorized:
                present()
            case .notDetermined:
                AVCaptureDevice.requestAccess(for: .video) { granted in
                    DispatchQueue.main.async {
                        if granted { present() } else { onError("camera access was declined") }
                    }
                }
            default:
                onError("camera access is off. Turn it on in Settings > NutriTrack")
            }
        }
    }

    static func topViewController() -> UIViewController? {
        let windows = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
        var top = (windows.first { $0.isKeyWindow } ?? windows.first)?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

private final class ScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    private let session = AVCaptureSession()
    private let preview = AVCaptureVideoPreviewLayer()
    private let onResult: (String?) -> Void
    private var finished = false

    init(onResult: @escaping (String?) -> Void) {
        self.onResult = onResult
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("not used") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black

        if let device = AVCaptureDevice.default(for: .video),
           let input = try? AVCaptureDeviceInput(device: device),
           session.canAddInput(input) {
            session.addInput(input)
            let output = AVCaptureMetadataOutput()
            if session.canAddOutput(output) {
                session.addOutput(output)
                output.setMetadataObjectsDelegate(self, queue: .main)
                let wanted: [AVMetadataObject.ObjectType] = [.ean13, .ean8, .upce]
                output.metadataObjectTypes = wanted.filter { output.availableMetadataObjectTypes.contains($0) }
            }
        }
        preview.session = session
        preview.videoGravity = .resizeAspectFill
        view.layer.addSublayer(preview)

        // A frame to aim with, a hint, and a way out.
        let frame = UIView()
        frame.layer.borderColor = UIColor.white.cgColor
        frame.layer.borderWidth = 3
        frame.layer.cornerRadius = 18
        frame.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(frame)

        let hint = UILabel()
        hint.text = "Point at the barcode"
        hint.textColor = .white
        hint.font = .preferredFont(forTextStyle: .headline)
        hint.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(hint)

        var config = UIButton.Configuration.filled()
        config.title = "Cancel"
        config.cornerStyle = .capsule
        config.baseBackgroundColor = UIColor(white: 0.15, alpha: 0.85)
        let cancel = UIButton(configuration: config)
        cancel.addTarget(self, action: #selector(cancelTapped), for: .touchUpInside)
        cancel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(cancel)

        NSLayoutConstraint.activate([
            frame.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            frame.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            frame.widthAnchor.constraint(equalTo: view.widthAnchor, multiplier: 0.8),
            frame.heightAnchor.constraint(equalTo: frame.widthAnchor, multiplier: 0.5),
            hint.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            hint.bottomAnchor.constraint(equalTo: frame.topAnchor, constant: -20),
            cancel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            cancel.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -24),
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview.frame = view.layer.bounds
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        DispatchQueue.global(qos: .userInitiated).async { [session] in session.startRunning() }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        DispatchQueue.global(qos: .userInitiated).async { [session] in session.stopRunning() }
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard let code = (metadataObjects.first as? AVMetadataMachineReadableCodeObject)?.stringValue else { return }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        finish(code)
    }

    @objc private func cancelTapped() { finish(nil) }

    private func finish(_ code: String?) {
        guard !finished else { return }
        finished = true
        dismiss(animated: true) { self.onResult(code) }
    }
}
