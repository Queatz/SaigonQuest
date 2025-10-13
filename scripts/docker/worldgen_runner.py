import argparse
import sys
import os
from pathlib import Path
import traceback

# Ensure our local shim for pytorch3d and WorldGen source are importable
# This allows "from pytorch3d.transforms import matrix_to_quaternion" via the shim
# even if the real pytorch3d isn't installed or importable in the container.
SHIM_DIR = "/opt/pytorch3d_shim"
WG_SRC_DIR = "/opt/WorldGen/src"
WG_PKG_DIR = "/opt/WorldGen"
for p in (SHIM_DIR, WG_SRC_DIR, WG_PKG_DIR):
    if p and p not in sys.path:
        sys.path.insert(0, p)


def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)


def decide_device() -> str:
    force_cpu = os.environ.get('WORLDGEN_FORCE_CPU', '') == '1'
    try:
        import torch
        if not force_cpu and torch.cuda.is_available():
            return 'cuda'
    except Exception:
        pass
    return 'cpu'


def decide_low_vram(device: str) -> bool:
    if device != 'cuda':
        return True
    try:
        import torch
        props = torch.cuda.get_device_properties(0)
        return getattr(props, 'total_memory', 0) < (24 * 1024 ** 3)
    except Exception:
        return True


def save_mesh(output: Path, mesh) -> bool:
    """Try to save the mesh to the desired output path.
    Prefer writing GLB/GLTF/PLY via Open3D if available.
    Returns True on success.
    """
    try:
        import open3d as o3d  # type: ignore
        # If mesh is not an Open3D TriangleMesh but has a convert method, try it
        if not isinstance(mesh, getattr(o3d.geometry, 'TriangleMesh')) and hasattr(mesh, 'to_legacy'):
            try:
                mesh = mesh.to_legacy()
            except Exception:
                pass
        ok = o3d.io.write_triangle_mesh(str(output), mesh)
        if not ok:
            raise RuntimeError('open3d write_triangle_mesh returned False')
        return True
    except Exception as ex:
        eprint(f"[worldgen] Open3D save failed: {ex}")
        # As a fallback, try to export PLY next to target
        try:
            import open3d as o3d  # ensure available for conversion
            ply_path = output.with_suffix('.ply')
            ok = o3d.io.write_triangle_mesh(str(ply_path), mesh)
            if ok:
                eprint(f"[worldgen] Saved fallback mesh as {ply_path}")
                return True
        except Exception as ex2:
            eprint(f"[worldgen] Fallback PLY save failed: {ex2}")
    return False


def main():
    parser = argparse.ArgumentParser(description='WorldGen API runner (no demo.py)')
    g = parser.add_mutually_exclusive_group(required=True)
    g.add_argument('-p', '--prompt', type=str, help='Text prompt')
    g.add_argument('-i', '--image', type=str, help='Path to a 360 image')
    parser.add_argument('-o', '--output', type=str, required=True, help='Output file path (expects .glb or .ply)')

    args = parser.parse_args()

    output_path = Path(args.output)
    if not output_path.is_absolute():
        output_path = Path('/workspace') / output_path
    output_path.parent.mkdir(parents=True, exist_ok=True)

    try:
        import torch  # noqa: F401
        from worldgen import WorldGen  # type: ignore
        from PIL import Image  # type: ignore
    except Exception as ex:
        eprint('[worldgen] ERROR: Missing required Python deps in container:', ex)
        traceback.print_exc()
        return 2

    device_str = decide_device()
    low_vram = decide_low_vram(device_str)
    eprint(f"[worldgen] Using device={device_str}, low_vram={low_vram}")

    mode = 't2s' if args.prompt else 'i2s'
    try:
        worldgen = WorldGen(
            mode=mode,
            device=torch.device(device_str),
            low_vram=low_vram,
        )
    except Exception as ex:
        eprint('[worldgen] ERROR: Failed to initialize WorldGen:', ex)
        traceback.print_exc()
        return 3

    try:
        if args.prompt:
            mesh = worldgen.generate_world(args.prompt, return_mesh=True)
        else:
            img_path = Path(args.image)
            if not img_path.exists():
                eprint(f"[worldgen] ERROR: Image not found: {img_path}")
                return 2
            image = Image.open(str(img_path))
            # Treat -i input as an equirectangular pano and avoid generate_pano to save VRAM
            try:
                eprint("[worldgen] Using generate_world(pano_image=...) for panoramic input")
                mesh = worldgen._generate_world(pano_image=image, return_mesh=True)
            except TypeError as te:
                # Older API might not accept pano_image; fall back (may be heavier)
                eprint(f"[worldgen] WARN: pano bypass API not supported ({te}); falling back to image path")
                mesh = worldgen.generate_world(image=image, return_mesh=True)
    except Exception as ex:
        eprint('[worldgen] ERROR: World generation failed:', ex)
        traceback.print_exc()
        return 4

    # If not a mesh but has save(), save as PLY splat and return
    if hasattr(mesh, 'save') and not hasattr(mesh, 'triangles'):
        try:
            ply_out = output_path.with_suffix('.ply') if output_path.suffix.lower() != '.ply' else output_path
            mesh.save(str(ply_out))
            eprint(f"[worldgen] Saved splat as {ply_out}")
            return 0
        except Exception as ex:
            eprint('[worldgen] ERROR: Failed to save splat output:', ex)
            traceback.print_exc()
            return 5

    # Save mesh (prefer GLB/GLTF using Open3D)
    if save_mesh(output_path, mesh):
        eprint(f"[worldgen] Output at {output_path}")
        return 0

    eprint('[worldgen] ERROR: Failed to save mesh to the requested path.')
    return 6


if __name__ == '__main__':
    sys.exit(main())
