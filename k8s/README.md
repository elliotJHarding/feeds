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
