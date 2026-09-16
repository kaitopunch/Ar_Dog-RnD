#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 INPUT.glb OUTPUT_DIR [required,clip,names]" >&2
  exit 64
fi

INPUT="$1"
OUTPUT_DIR="$2"
REQUIRED_CLIPS="${3:-}"
CLI=(npx --yes @gltf-transform/cli@4.2.1)
NAME="$(basename "$INPUT" .glb)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
mkdir -p "$OUTPUT_DIR"

run_base_pipeline() {
  local source="$1"
  local target="$2"
  "${CLI[@]}" dedup "$source" "$TMP_DIR/01-dedup.glb"
  "${CLI[@]}" prune "$TMP_DIR/01-dedup.glb" "$TMP_DIR/02-prune.glb"
  "${CLI[@]}" weld "$TMP_DIR/02-prune.glb" "$TMP_DIR/03-weld.glb"
  "${CLI[@]}" resample "$TMP_DIR/03-weld.glb" "$TMP_DIR/04-resample.glb"
  "${CLI[@]}" resize "$TMP_DIR/04-resample.glb" "$TMP_DIR/05-texture.glb" --width 1024 --height 1024
  "${CLI[@]}" quantize "$TMP_DIR/05-texture.glb" "$target" --pattern "POSITION,NORMAL,TANGENT,TEXCOORD_*"
}

run_base_pipeline "$INPUT" "$OUTPUT_DIR/$NAME.glb"
"${CLI[@]}" simplify "$TMP_DIR/03-weld.glb" "$TMP_DIR/lod-geometry.glb" --ratio 0.55 --error 0.002 --lock-border true
"${CLI[@]}" resample "$TMP_DIR/lod-geometry.glb" "$TMP_DIR/lod-animation.glb"
"${CLI[@]}" resize "$TMP_DIR/lod-animation.glb" "$TMP_DIR/lod-texture.glb" --width 512 --height 512
"${CLI[@]}" quantize "$TMP_DIR/lod-texture.glb" "$OUTPUT_DIR/${NAME}_lod1.glb" --pattern "POSITION,NORMAL,TANGENT,TEXCOORD_*"

python3 "$(dirname "$0")/audit-glb.py" "$INPUT" --require-clips "$REQUIRED_CLIPS"
python3 "$(dirname "$0")/audit-glb.py" "$OUTPUT_DIR/$NAME.glb" --require-clips "$REQUIRED_CLIPS"
python3 "$(dirname "$0")/audit-glb.py" "$OUTPUT_DIR/${NAME}_lod1.glb" --require-clips "$REQUIRED_CLIPS"
