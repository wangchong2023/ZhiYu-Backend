#!/usr/bin/env bash
# ============================================================
# JWT RSA Key Rotation Script
#
# Usage:
#   ./scripts/rotate-jwt-keys.sh [--env <env>] [--restart] [--dry-run]
#
# Options:
#   --env <env>    Target environment (default: kubeadm)
#   --restart      Rolling restart backend pod after rotation
#   --dry-run      Generate keys without replacing live files
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

ENV="kubeadm"
RESTART=false
DRY_RUN=false
KEY_SIZE=2048
BACKUP_DIR=""

usage() {
    sed -n '4,11p' "$0"
    exit 0
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --env)    ENV="$2";    shift 2 ;;
        --restart) RESTART=true; shift ;;
        --dry-run) DRY_RUN=true; shift ;;
        --help)   usage ;;
        *) echo "Unknown option: $1"; usage ;;
    esac
done

KEY_DIR="$PROJECT_ROOT/deploy/envs/$ENV"
PRIVATE_KEY="$KEY_DIR/jwt-private.pem"
PUBLIC_KEY="$KEY_DIR/jwt-public.pem"

if [[ ! -d "$KEY_DIR" ]]; then
    echo "Error: key directory not found: $KEY_DIR"
    exit 1
fi

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_DIR="$KEY_DIR/key-backups/$TIMESTAMP"

echo "=== JWT Key Rotation ==="
echo "Environment: $ENV"
echo "Key directory: $KEY_DIR"
echo "Key size: ${KEY_SIZE}-bit RSA"
echo "Dry run: $DRY_RUN"
echo ""

# ── Step 1: Generate new key pair ──
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

echo "Generating new RSA-${KEY_SIZE} key pair..."
openssl genrsa -out "$TEMP_DIR/jwt-private.pem" "$KEY_SIZE" 2>/dev/null
openssl rsa -in "$TEMP_DIR/jwt-private.pem" -pubout -out "$TEMP_DIR/jwt-public.pem" 2>/dev/null

# Convert private key to PKCS8 format (matching JwtKeyLoader expectation)
openssl pkcs8 -topk8 -inform PEM -outform PEM \
    -in "$TEMP_DIR/jwt-private.pem" -out "$TEMP_DIR/jwt-private-pkcs8.pem" \
    -nocrypt 2>/dev/null
mv "$TEMP_DIR/jwt-private-pkcs8.pem" "$TEMP_DIR/jwt-private.pem"

echo "New keys generated successfully."
echo ""

if "$DRY_RUN"; then
    echo "=== DRY RUN: Keys would be placed at ==="
    echo "  Private: $PRIVATE_KEY"
    echo "  Public:  $PUBLIC_KEY"
    echo ""
    echo "Generated keys (temp dir, will be cleaned up):"
    ls -la "$TEMP_DIR/"
    exit 0
fi

# ── Step 2: Backup existing keys ──
echo "Backing up existing keys..."
mkdir -p "$BACKUP_DIR"

if [[ -f "$PRIVATE_KEY" ]]; then
    cp -p "$PRIVATE_KEY" "$BACKUP_DIR/jwt-private.pem"
    echo "  Backed up: $PRIVATE_KEY → $BACKUP_DIR/jwt-private.pem"
fi
if [[ -f "$PUBLIC_KEY" ]]; then
    cp -p "$PUBLIC_KEY" "$BACKUP_DIR/jwt-public.pem"
    echo "  Backed up: $PUBLIC_KEY → $BACKUP_DIR/jwt-public.pem"
fi

# ── Step 3: Deploy new keys ──
echo ""
echo "Deploying new keys..."
cp "$TEMP_DIR/jwt-private.pem" "$PRIVATE_KEY"
cp "$TEMP_DIR/jwt-public.pem" "$PUBLIC_KEY"
chmod 600 "$PRIVATE_KEY"
chmod 644 "$PUBLIC_KEY"

echo "  Private key deployed: $PRIVATE_KEY (mode 600)"
echo "  Public key deployed:  $PUBLIC_KEY  (mode 644)"
echo ""

# ── Step 4: Cleanup old backups (> 90 days) ──
BACKUP_BASE="$KEY_DIR/key-backups"
if [[ -d "$BACKUP_BASE" ]]; then
    DELETED=$(find "$BACKUP_BASE" -maxdepth 1 -type d -mtime +90 \
        -not -path "$BACKUP_BASE" -exec rm -rf {} \; -print | wc -l)
    if [[ "$DELETED" -gt 0 ]]; then
        echo "Cleaned up $DELETED old backup(s) (> 90 days)."
    fi
fi

# ── Step 5: Optional restart ──
if "$RESTART"; then
    echo ""
    echo "Restarting backend deployment (rolling restart)..."
    NAMESPACE="${NAMESPACE:-default}"
    kubectl rollout restart deployment/zhiyu-backend -n "$NAMESPACE" 2>/dev/null || {
        echo "Warning: kubectl not available or deployment not found."
        echo "Please restart the backend service manually to pick up new keys."
    }
    echo "Rolling restart initiated."
fi

echo ""
echo "=== Key rotation complete ==="
echo "New keys:  $KEY_DIR/jwt-{private,public}.pem"
echo "Backup:    $BACKUP_DIR"
