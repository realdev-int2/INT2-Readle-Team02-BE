# Backend deploy helper

`deploy-backend.sh` runs the backend-only temporary Blue-Green deployment for the
single EC2 rootful Podman stack.

## Prerequisites

- Run on the EC2 host that owns `readle-nginx` and the backend containers.
- Keep `readle-nginx`, `readle-frontend`, and both backend slots on
  `readle-public`. Backend slots must not publish host ports.
- Keep both backend slots and MySQL on `readle-private`; `readle-nginx` must
  not join this network, so it has only the public default route.
- Keep the production backend env file on the host at `/etc/readle/backend.env`
  unless `READLE_BACKEND_ENV_FILE` overrides it.
- Keep the GHCR image repository prefix in
  `/etc/readle/backend-image-repository`, one line only, for example:

  ```text
  ghcr.io/<owner>/int2-readle-team02-be
  ```

## First-time host install

Install the shipped helper once at the path CI invokes:

```sh
sudo install -d -o root -g root -m 0755 /usr/local/libexec/readle-backend
sudo install -o root -g root -m 0755 ./deploy-backend.sh /usr/local/libexec/readle-backend/deploy-backend
```

This bootstrap copies the reviewed helper to the EC2 host with root ownership
and executable mode. Normal deployment does not `git pull`, build with Gradle,
or build an image on the host; CI passes the published immutable image digest
and matching Git revision to the installed helper.

## Nginx upstream include bootstrap

The helper updates one host-side include file and expects the Nginx container to
see the same file through a bind mount.

| Path | Default |
| --- | --- |
| Host include | `/opt/readle/nginx/readle-backend-upstream.conf` |
| Container include | `/etc/nginx/readle-backend-upstream.conf` |

Bootstrap the host file from
`backend/ops/backend/nginx/backend-upstream.conf.template`, bind-mount it into
`readle-nginx`, and include this exact container path from the Nginx config:

```nginx
include /etc/nginx/readle-backend-upstream.conf;
```

Because this is a file bind mount, update its contents in place; do not replace
the host file with `mv`, which leaves the container mount on the previous inode.

The active slot in `/var/lib/readle/backend-deploy.env` must match this include.

## Slots and limits

The helper uses two fixed backend slots:

- `readle-backend-blue`
- `readle-backend-green`

Only one slot serves traffic at a time. During deployment the inactive slot runs
as the candidate, and both backend slots run with `--memory=640m`.

## Deploy

From this directory, pass an immutable image digest and the expected
40-character Git revision:

```sh
sudo ./deploy-backend.sh \
  ghcr.io/<owner>/int2-readle-team02-be@sha256:<64-lowercase-hex> \
  <40-lowercase-git-sha>
```

The script pulls the digest, checks the image label
`org.opencontainers.image.revision`, starts the candidate on `readle-public`
then attaches `readle-private`, waits for `/api/actuator/health/readiness`,
updates the Nginx upstream include, runs `nginx -t`, reloads Nginx, and smoke checks
`/api/actuator/health/readiness` through the edge.

Deployments use two locks:

- `/run/lock/readle-candidate-deploy.lock`: shared candidate lock that prevents
  another candidate-style app deployment from running at the same time.
- `/run/lock/readle-backend-deploy.lock`: backend deployment lock.

## Rollback behavior

If candidate readiness or `nginx -t` fails, the helper removes the candidate and
keeps traffic on the current active slot.

After Nginx reload, the helper writes pending rollback state before smoke
checking the edge. If the smoke check fails, it restores the previous upstream
include, reloads Nginx, clears pending rollback state, and removes the failed
candidate.

After a successful cutover, the helper records the new `last_good_*` state,
keeps the old image tuple in `previous_*`, clears pending rollback state, and
stops the old slot.
