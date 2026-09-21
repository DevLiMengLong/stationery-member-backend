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
