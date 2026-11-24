# Build definition for the four backend images plus the standalone tools site
# (see Dockerfile and nitrowater-tools/Dockerfile for the targets).
#
# Why a bake file instead of a build matrix: all four backend runtime targets share the
# heavy `build` stage (full Gradle build). `docker buildx bake` resolves them as one
# build graph, so that stage runs once; four separate `docker build --target` calls
# would run it four times (and download the Gradle dependencies four times). The tools
# target uses its own context (nitrowater-tools/) and never touches the Gradle stage.
#
# Variables are read from the environment, e.g. TAG=v1.2.3 SHA=abc123 REPO=org/repo.
# VITE_OFFLINE_TOOLS_URL is the portal's outbound link, a build-time placeholder;
# CI overrides it with a repository variable so a domain change needs no source edit.
# The tools image takes no build args at all — it never links back to the portal.
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

variable "VITE_OFFLINE_TOOLS_URL" {
  description = "Origin of the standalone tools site, baked into the portal SPA"
  default     = "https://tools.example.com"
}

group "default" {
  targets = ["account", "resource", "server", "bff", "tools"]
}

target "_common" {
  context    = "."
  dockerfile = "Dockerfile"
  # The portal's cross-site link is a build-time Vite variable, so it has to be an
  # explicit build arg (ENV in the Dockerfile alone is not enough for `import.meta.env`).
  args = {
    VITE_OFFLINE_TOOLS_URL = VITE_OFFLINE_TOOLS_URL
  }
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

# Standalone browser-local tools: its own Vite build + nginx image. Not part of the app
# compose unit; external services pull this image and deploy it on their own origin.
# Deliberately does NOT inherit `_common` — that target carries the portal's VITE arg,
# and bake merges (never clears) inherited args. Own context so the Dockerfile's COPY
# paths stay relative to the tools package; no args, no portal origin of any kind.
target "tools" {
  context    = "nitrowater-tools"
  dockerfile = "Dockerfile"
  tags       = ["${REGISTRY}/${REPO}/tools:${TAG}", "${REGISTRY}/${REPO}/tools:${SHA}"]
  # SBOM + provenance attestations; drop this line if the registry rejects indices.
  attest = ["type=provenance,mode=max", "type=sbom"]
}
