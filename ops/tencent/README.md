# Tencent production operations

These assets deploy the stationery member system to Tencent Cloud while keeping Ubuntu-managed packages and service definitions separate from data-disk application state.

## Fixed paths

- Application root: `/mnt/datadisk0/stationery-member`
- MySQL persistence: `/mnt/datadisk0/mysql` bind-mounted at `/var/lib/mysql`
- Redis persistence: `/mnt/datadisk0/redis` bind-mounted at `/var/lib/redis`
- Runtime environment: `/mnt/datadisk0/stationery-member/config/backend.env`
- Releases: `/mnt/datadisk0/stationery-member/releases`
- Backups: `/mnt/datadisk0/stationery-member/backups`

Run scripts only from the repository version that produced the release. Review each script before using `sudo`.

## Secret policy

`backend.env.example` contains names and safe defaults only. Generate production database credentials on Tencent and keep them in the data-disk config directory. Do not put WeChat, cloud-provider, database, upload, or notification secret values in this directory or Git history.

Production starts with `STATIONERY_PAYMENT_MODE=BOOKKEEPING`. Enabling `WECHAT_SERVICE_PROVIDER` requires service-provider credentials, a per-store special merchant number, HTTPS callback reachability, and a separate real-funds acceptance run.

## Installation order

```bash
sudo bash bootstrap-host.sh
sudo bash install-runtime-config.sh
sudo bash install-backup.sh
```

`bootstrap-host.sh` must pass the Ubuntu, mount, filesystem, and free-space guards before it installs packages or relocates state. Reboot once after bootstrap and verify both bind mounts before staging an application release.

The runtime installer creates the database user and password locally on Tencent. It intentionally does not print the password and does not start Java before a release exists. The backup installer intentionally leaves its timer disabled until one backup has been restored into a temporary database and compared.

## Release and health

Stage each release under `releases/<release-id>`, copy its SHA-256 manifest, and run `sha256sum -c SHA256SUMS` before changing either current symlink. Then:

```bash
sudo systemctl restart stationery-member-backend
sudo systemctl reload nginx
curl --fail http://127.0.0.1:8090/api/health
```

Java, MySQL, and Redis must remain loopback-only. Nginx is the only public application listener.

## Backup operations

```bash
sudo systemctl start stationery-member-backup.service
systemctl status stationery-member-backup.timer --no-pager
```

Backups are written atomically to `backups/mysql`, include a sibling SHA-256 file, and are retained for 14 days only when both members of an old backup pair exist. Validate restore into an explicitly named temporary database before enabling the timer or replacing production data.

## Rollback boundary

Before the first Tencent write, rollback is stopping Tencent and restarting Alibaba. After the first Tencent write, freeze Tencent and reconcile its delta before restarting Alibaba. Never run both writers concurrently. For the 2026-09-22 migration, the first-write boundary is `2026-09-22T10:30:52+08:00`.

## Current external blockers

- `usepenciles.com` is not ICP-approved, so DNS/HTTPS and WeChat legal-domain configuration are pending.
- No service-provider or per-store special-merchant credentials are installed; production remains `BOOKKEEPING`.
- The native miniprogram has not been uploaded or released.
- Local data-disk backups need an off-host/COS copy.
