# Feeds — Kubernetes deployment

Manifests for the feeds server on the existing microk8s cluster, mirroring the
meals cluster setup (see the meals DEPLOY.md / meals_cluster repo for the
reference patterns). ArgoCD syncs this directory; secrets are applied
out-of-band, exactly as meals does it.

## Layout

- `deployment.yaml` — feeds server, image `grubplanner.co.uk:32000/feeds:latest`
- `service.yaml` — ClusterIP, 80 -> 8080
- `ingress.yaml` — nginx + cert-manager, host is a `feeds.REPLACE-ME` placeholder
- `argocd-application.yaml` — ArgoCD Application pointing at this repo's `k8s/` path
- `secrets/feeds-secrets.template.yaml` — secret template; the filled-in copy is
  gitignored and applied by hand, never synced by ArgoCD

## Placeholders to fill before first deploy

- `deployment.yaml`: `DB_URL` (Supabase pooler host of the new feeds project),
  `GOOGLE_CLIENT_ID` (OAuth client id used as ID-token audience)
- `ingress.yaml`: `feeds.REPLACE-ME` host (two occurrences: tls.hosts and rules.host)
- `argocd-application.yaml`: `repoURL` (this repo's real remote — the meals
  reference only ever shows the `your-username` placeholder)
- `secrets/feeds-secrets.yaml`: real base64 values (see below)

## First-deploy prerequisites

Create the Supabase project: new project (separate from meals, free tier),
note the connection pooler host, database name, and the pooler username
(`postgres.<project-ref>`) and password. Put host:port/db into `DB_URL` in
`deployment.yaml` — the `jdbc:postgresql://` prefix lives in the server's
`application.properties`, as in meals.

Pick the host: decide the real hostname (e.g. `feeds.grubplanner.co.uk`),
replace `feeds.REPLACE-ME` in `ingress.yaml`, and add a DNS A/CNAME record
pointing at the cluster ingress. cert-manager (issuer `letsencrypt-prod`)
then issues `feeds-tls-cert`. Without the DNS record the host 404s and the
cert fails.

Set the secrets (do this before the first sync — the pod crash-loops on
missing `secretKeyRef` otherwise):

```bash
cd k8s/secrets
cp feeds-secrets.template.yaml feeds-secrets.yaml
# fill in values, encoding each with:  echo -n "value" | base64
kubectl apply -f feeds-secrets.yaml
```

`feeds-secrets.yaml` is gitignored; never commit it. Base64 is encoding, not
encryption — anyone with cluster read access to Secrets can read the values.

## Build, push, deploy

Same flow as meals DEPLOY.md.

Build and push the server image (from `server/`; `bootBuildImage` is expected
to be configured like the meals server — `publish = true`, image name
`grubplanner.co.uk:32000/feeds:latest`, registry credentials from env):

```bash
export REGISTRY_USER=... REGISTRY_PASSWORD=...
./gradlew bootBuildImage   # builds AND publishes to grubplanner.co.uk:32000 — not a local-only build
```

Register the app with ArgoCD (first time only):

```bash
kubectl apply -f k8s/argocd-application.yaml
```

Alternatively, if the meals app-of-apps should own it, commit a copy of the
Application manifest into the meals_cluster `argocd-applications/` directory
and let the root app pick it up.

ArgoCD then syncs `k8s/` automatically (prune + selfHeal are on). Manifest
changes deploy by commit + push to this repo.

Subsequent image deploys: the tag is `:latest` with `imagePullPolicy: Always`
(the meals convention), so a deploy is re-push + pod restart:

```bash
./gradlew bootBuildImage
kubectl rollout restart deployment/feeds
```

In practice the server image is built by GitHub Actions (`.github/workflows/build-image.yml`)
on push to `main`, not locally — a local Apple-Silicon build produces an arm64 image the amd64
cluster can't run (`exec format error`). The commands above still work on an amd64 host.

## Registry TLS cert — recurring gotcha (re-expires ~every 90 days)

The cluster registry `grubplanner.co.uk:32000` serves TLS from a **static** Let's Encrypt cert in
secret `registry-certs` (namespace `container-registry`). Nothing renews it, so it lapses roughly
every 90 days and **all pushes fail** (feeds *and* meals) with:

```
curl: (60) SSL certificate problem: certificate has expired
```

Quick fix — copy the cluster's still-valid ingress cert (`default/tls-cert`, auto-renewed by
cert-manager, and it covers `grubplanner.co.uk`) into `registry-certs`, then restart the registry:

```bash
kubectl get secret tls-cert -n default -o jsonpath='{.data.tls\.crt}' | base64 -d > /tmp/tls.crt
kubectl get secret tls-cert -n default -o jsonpath='{.data.tls\.key}' | base64 -d > /tmp/tls.key
kubectl create secret tls registry-certs --cert=/tmp/tls.crt --key=/tmp/tls.key \
  -n container-registry --dry-run=client -o yaml | kubectl apply -f -
kubectl rollout restart deployment/registry -n container-registry
rm -f /tmp/tls.crt /tmp/tls.key
# verify (no -k): curl -sS https://grubplanner.co.uk:32000/v2/ -> HTTP 200
```

Permanent fix (not yet done): have cert-manager manage `registry-certs` (an `Issuer` in the
`container-registry` namespace + a `Certificate`, or a cert reflector mirroring `default/tls-cert`)
so it auto-renews and this stops recurring.

## Deviations from the meals manifests

- No Redis env block (feeds has no Redis) and no `GOOGLE_CLIENT_SECRET`
  (feeds only verifies Google ID tokens; the client id is the audience and is
  not sensitive, so it is a plain env value).
- `resources` requests/limits added, sized small for a 2-user service (the
  meals deployment defines none).
- Container runs unprivileged; the meals deployment sets `privileged: true` and
  runs as root with no documented reason, and a buildpack Spring Boot image
  does not need it. If image pulls or startup fail only on this difference,
  revert to the meals securityContext and investigate.
- Ingress routes `/api/` only — feeds has no web client.
