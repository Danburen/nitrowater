# Build definition for the four backend images (see Dockerfile for the targets).
#
# Why a bake file instead of a build matrix: all four runtime targets share the
# heavy `build` stage (full Gradle build). `docker buildx bake` resolves them as one
# build graph, so that stage runs once; four separate `docker build --target` calls
# would run it four times (and download the Gradle dependencies four times).
#
# Variables are read from the environment, e.g. TAG=v1.2.3 SHA=abc123 REPO=org/repo.
# Usage: docker buildx bake --push   (workflow: .github/workflows/build.yml)

variable "REGISTRY" {
  default = "ghcr.io"
}

variable "REPO" {
  description = "lowercase owner/repo, e.g. danburen/nitrowater"
}

variable "TAG" {
  default = "latest"
}

variable "SHA" {
  description = "commit sha, pushed as a second tag (rollback target)"
}

group "default" {
  targets = ["account", "resource", "server", "bff"]
}

target "_common" {
  context    = "."
  dockerfile = "Dockerfile"
  # SBOM + provenance attestations; drop this line if the registry rejects indices.
  attest = ["type=provenance,mode=max", "type=sbom"]
}

target "account" {
  inherits = ["_common"]
  target   = "account"
  tags     = ["${REGISTRY}/${REPO}/account:${TAG}", "${REGISTRY}/${REPO}/account:${SHA}"]
}

target "resource" {
  inherits = ["_common"]
  target   = "resource"
  tags     = ["${REGISTRY}/${REPO}/resource:${TAG}", "${REGISTRY}/${REPO}/resource:${SHA}"]
}

target "server" {
  inherits = ["_common"]
  target   = "server"
  tags     = ["${REGISTRY}/${REPO}/server:${TAG}", "${REGISTRY}/${REPO}/server:${SHA}"]
}

target "bff" {
  inherits = ["_common"]
  target   = "bff"
  tags     = ["${REGISTRY}/${REPO}/bff:${TAG}", "${REGISTRY}/${REPO}/bff:${SHA}"]
}
