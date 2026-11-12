# Build definition for the four backend images plus the deploy kit (see Dockerfile and
# deploy/Dockerfile.kit for the targets).
#
# Why a bake file instead of a build matrix: all four runtime targets share the
# heavy `build` stage (full Gradle build). `docker buildx bake` resolves them as one
# build graph, so that stage runs once; four separate `docker build --target` calls
# would run it four times (and download the Gradle dependencies four times).
# The kit target is a `FROM scratch` file carrier and shares nothing with them.
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
  targets = ["account", "resource", "server", "bff", "kit"]
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

# Deploy kit: compose files + ops scripts, shipped as an image so the host needs no git.
# Own context: the root .dockerignore excludes deploy/, and deploy/.dockerignore keeps
# secrets (and the ~1.8 MB plugin docs) out of the image.
target "kit" {
  inherits   = ["_common"]
  context    = "./deploy"
  dockerfile = "Dockerfile.kit"
  tags       = ["${REGISTRY}/${REPO}/kit:${TAG}", "${REGISTRY}/${REPO}/kit:${SHA}"]
}
