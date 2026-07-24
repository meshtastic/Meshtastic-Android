# Recent

```

# Recent

## 2026-07-23
Exonerated PR #6391; root cause in fw develop (8abae90..d9150e8): Router::sendLocal loopback rewrite breaks BLE admin response. Bisected fw commit history via A/B testing across devices (Pixel 6a, L1 Pro); culprit isolated to d6b12ea3f. Patch reverted and validated; reproduced via admin_repro_trace.py & TCP scripts. Upstream issue filed with fix PR.