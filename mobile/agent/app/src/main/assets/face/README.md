# Face-embedding model (not committed to git)

`sface.onnx` is fetched automatically by the `fetchFaceEmbedderModel` Gradle task (see
`mobile/agent/app/build.gradle.kts`) — you don't need to do anything manually; a normal
`./gradlew assemble...` downloads and sha256-verifies it if missing.

**Source:** [`opencv/face_recognition_sface_2021dec.onnx`](https://github.com/opencv/opencv_zoo/blob/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx)
from OpenCV Zoo (Apache 2.0 license, explicitly commercial-use-friendly — no research-only
restriction).

**Status: an interim, benchmarked free model — still not IDEMIA MorphoKit.** This replaces the
earlier `VirtuoTuring/virtuoturing-face-embedder` prototype, which had zero published accuracy
benchmark and near-zero community adoption. SFace instead has a real published benchmark
(99.60% accuracy on LFW at a cosine threshold of 0.363) and is maintained by OpenCV.org, a
reputable first-party source. The stronger InsightFace models (buffalo_l/antelopev2) were
considered and rejected: they score higher but their model zoo license is explicitly
**non-commercial research only**, which disqualifies them for a payments product without a paid
InsightFace commercial license. SFace's Apache 2.0 license has no such restriction.

This is still not a claim of KYC-grade production validation for BioPay's actual beneficiary
population, devices, and field lighting conditions — that requires real acceptance testing, which
has not been done. IDEMIA MorphoKit remains the intended production path (bundles
fingerprint/face/iris under the vendor BioPay already licenses fingerprint hardware from); if that
licensing comes through, this model and `OnnxFaceEmbedder` are expected to be replaced, not
extended.

**Confirmed graph spec** (inspected directly via `onnx.load()`, not assumed from docs — OpenCV's
own doc pages for this model returned HTTP 403 and its own Python demo only exposes the model
through the opaque `cv.FaceRecognizerSF` C++ wrapper): input tensor `"data"`, shape `[1,3,112,112]`
NCHW float32; output tensor `"fc1"`, shape `[1,128]` float32, **not** L2-normalized in-graph (the
last op is `BatchNormalization`, not a norm layer) — fine for `FaceMatcher.cosineSimilarity`, which
normalizes internally regardless.

**Confirmed preprocessing** (from OpenCV's `face_recognize.cpp`, which calls
`blobFromImage(scalefactor=1, size=(112,112), mean=(0,0,0), swapRB=true, crop=false)` before
inference): RGB channel order, **raw pixel values 0–255, no scaling** (this differs from the old
VirtuoTuring model, which scaled to `[-1,1]`). Alignment target: the standard 5-point ArcFace
112×112 template — `FaceAligner`'s existing canonical eye coordinates (`38.2946,51.6963` /
`73.5318,51.5014`) already match this template's eye points exactly, so no alignment change was
needed.
