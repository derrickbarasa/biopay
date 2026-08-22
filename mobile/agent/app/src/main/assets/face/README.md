# Prototype face-embedding model (not committed to git)

`virtuoturing.onnx` + `best_embedder.onnx.data` are fetched automatically by the
`fetchFaceEmbedderModel` Gradle task (see `mobile/agent/app/build.gradle.kts`) — you don't need to
do anything manually; a normal `./gradlew assemble...` downloads and sha256-verifies them if
they're missing.

**Source:** https://huggingface.co/VirtuoTuring/virtuoturing-face-embedder (MIT license, stated to
cover the weights themselves, not just wrapper code).

**Status: unvalidated prototype, not a production model.** Zero downloads/likes on Hugging Face as
of 2026-08, published by a low-profile org, trained on only ~23,660 images, no published accuracy
benchmark. It exists to prove out the embedding/matching pipeline end-to-end while BioPay pursues
IDEMIA MorphoKit licensing (the intended production path — bundles fingerprint/face/iris under the
vendor BioPay already licenses fingerprint hardware from). If MorphoKit licensing comes through,
this model and `OnnxFaceEmbedder` are expected to be replaced, not extended.

**Confirmed graph spec** (inspected directly, not assumed): input tensor `"input"`, shape
`[1,3,112,112]` NCHW float32; output tensor `"embedding"`, shape `[1,512]` float32, already
L2-normalized internally by the graph's own final ops. Input preprocessing per the model card
(unconfirmed by any published usage example): pixels scaled to `[-1,1]`.
