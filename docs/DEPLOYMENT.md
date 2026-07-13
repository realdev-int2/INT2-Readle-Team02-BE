# Backend Image Deployment Contract

## Scope

This repository builds and publishes the backend image. It does not own the EC2 Compose stack, Nginx upstream, MySQL container, or deployment script.

On a successful `main` push, GitHub Actions publishes:

```text
ghcr.io/<repository-owner>/int2-readle-team02-be:<commit-sha>
```

`:main` is a convenience tag only. The deployment layer must set `IMAGE_REF` to the full commit-SHA tag.

The image namespace follows `github.repository_owner`. Before the repository transfer it is `realdev-int2`; after the transfer it is `programmers-intern-program`. Update the EC2 `IMAGE_REF` after the first successful image publish in the new namespace.

## EC2 runtime contract

- EC2 pulls a prebuilt image; it must not run Gradle, `docker build`, or `git pull` for backend deployment.
- The backend runs with `SPRING_PROFILES_ACTIVE=prod` and the ignored production datasource/S3 environment values.
- Backend and MySQL stay on internal Docker networks. Neither service publishes a host port.
- The deployment layer starts a temporary `backend-candidate`, waits for `GET /api/actuator/health/readiness`, then performs the ADR-008 Nginx switch.
- MySQL is a singleton and is not recreated during application deployment.

## Required first-deploy checks

Run these on EC2 before adopting GHCR delivery:

```bash
uname -m # x86_64 expected
docker version
docker compose version
docker pull ghcr.io/<repository-owner>/int2-readle-team02-be:<commit-sha>
```

The workflow publishes `linux/amd64`. If EC2 is not `x86_64`, stop and add the required platform deliberately. If the pull cannot work because of EC2 outbound access or GitHub package policy, stop and choose a separate artifact-transfer design. Do not add a GitHub token to this repository.

## Rollback

Set the deployment layer's `IMAGE_REF` to the previous successful commit-SHA image and use the same candidate/Nginx validation path.
