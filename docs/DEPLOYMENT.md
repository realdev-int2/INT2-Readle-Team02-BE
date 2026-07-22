# Backend Image Deployment Contract

## Scope

This repository builds and publishes the backend image and owns the shipped backend deploy helper. It does not own the EC2 Compose stack, Nginx upstream, or MySQL container.

On a successful `main` push, GitHub Actions publishes an image and invokes the installed deploy helper at `/usr/local/libexec/readle-backend/deploy-backend`. The backend deploy helper input must be:

```text
ghcr.io/<owner>/int2-readle-team02-be@sha256:<digest>
<40-character-git-sha>
```

Convenience tags such as `:main` or commit-SHA tags may exist for discovery only. They must not be deployment inputs. The deployment layer must use the immutable `@sha256` digest ref and pass the matching expected 40-character Git SHA as the revision.

The image namespace follows `github.repository_owner`. Before the repository transfer it is `realdev-int2`; after the transfer it is `programmers-intern-program`. Update the EC2 image repository prefix and digest ref after the first successful image publish in the new namespace.

## EC2 runtime contract

- EC2 pulls a prebuilt image; it must not run Gradle, `docker build`, or `git pull` for backend deployment.
- The backend runs with `SPRING_PROFILES_ACTIVE=prod` and the ignored production datasource/S3 environment values.
- Backend slots join both `readle-public` (for the Nginx upstream) and `readle-private` (for MySQL); neither backend nor MySQL publishes a host port. Nginx joins `readle-public` only, avoiding multiple default routes in its network namespace.
- The deployment layer starts a temporary candidate, waits for `GET /api/actuator/health/readiness`, then performs the ADR-008 Nginx switch.
- The active backend is a state-driven slot and may be either `readle-backend-blue` or `readle-backend-green`.
- MySQL is a singleton and is not recreated during application deployment.

## Required first-deploy checks

The GHCR package remains **private**. EC2 authenticates with a dedicated GitHub account credential that has only `read:packages` and package read access. Store `GHCR_USERNAME` and `GHCR_PULL_TOKEN` only in the EC2 deployment environment; never commit them to this repository.

Run these on EC2 before adopting GHCR delivery:

```bash
uname -m # x86_64 expected
sudo podman version
printf '%s' "$GHCR_PULL_TOKEN" | sudo podman login ghcr.io -u "$GHCR_USERNAME" --password-stdin
sudo podman pull ghcr.io/<owner>/int2-readle-team02-be@sha256:<digest>
```

The workflow publishes `linux/amd64`. If EC2 is not `x86_64`, stop and add the required platform deliberately. If the pull cannot work because of EC2 outbound access or GitHub package policy, stop and choose a separate artifact-transfer design.

## First-time host bootstrap

Install the shipped helper once on the EC2 host before enabling CI deployment:

```bash
sudo install -d -o root -g root -m 0755 /usr/local/libexec/readle-backend
sudo install -o root -g root -m 0755 backend/ops/backend/deploy-backend.sh /usr/local/libexec/readle-backend/deploy-backend
```

After bootstrap, normal deployment is only the GitHub Actions image publish and CI invocation of `/usr/local/libexec/readle-backend/deploy-backend` with the immutable image digest and matching Git revision. Normal deployment must not run `git pull`, Gradle, or a host-side image build.

## Rollback

Authenticate with the same EC2-only read credential, deploy a prior successful immutable digest ref with its matching 40-character Git revision, then use the same candidate/Nginx validation path.
